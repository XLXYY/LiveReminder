package com.example.livereminder

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.livereminder.db.AppDatabase
import com.example.livereminder.db.FollowedRoomEntity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var etInput: EditText
    private lateinit var btnAdd: Button
    private lateinit var rvRooms: RecyclerView
    private lateinit var adapter: RoomAdapter
    private lateinit var fabSettings: FloatingActionButton
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = AppDatabase.getInstance(this)

        etInput = findViewById(R.id.et_input)
        btnAdd = findViewById(R.id.btn_add)
        rvRooms = findViewById(R.id.rv_rooms)
        fabSettings = findViewById(R.id.fab_settings)

        adapter = RoomAdapter(
            onDelete = { room -> deleteRoom(room) },
            onRefresh = { refreshRoom(it) }
        )
        rvRooms.layoutManager = LinearLayoutManager(this)
        rvRooms.adapter = adapter

        btnAdd.setOnClickListener { addRoom() }
        fabSettings.setOnClickListener { showSettingsDialog() }

        // 首次启动请求悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("需要悬浮窗权限")
                .setMessage("本应用需要悬浮窗权限以在其他应用上显示直播提醒，请授权。")
                .setPositiveButton("去设置") { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName"))
                    startActivity(intent)
                }
                .setNegativeButton("取消", null)
                .show()
        }

        // 监听数据库变化更新列表
        lifecycleScope.launch {
            db.followedRoomDao().getAllRooms().collect { rooms ->
                adapter.submitList(rooms)
            }
        }

        // 启动时检测一次
        refreshAll()
    }

    private fun addRoom() {
        val input = etInput.text.toString().trim()
        if (input.isEmpty()) {
            Toast.makeText(this, "请输入房间号或链接", Toast.LENGTH_SHORT).show()
            return
        }
        val roomId = LiveChecker.parseRoomId(input)
        if (roomId == null) {
            Toast.makeText(this, "格式错误", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val info = LiveChecker.getRoomInfo(roomId)
            if (info != null) {
                withContext(Dispatchers.IO) {
                    db.followedRoomDao().insertRoom(
                        FollowedRoomEntity(
                            roomId = info.roomId,
                            uname = info.uname,
                            liveStatus = info.liveStatus
                        )
                    )
                }
                etInput.text.clear()
                Toast.makeText(this@MainActivity, "已添加 ${info.uname}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "房间不存在或网络错误", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteRoom(room: FollowedRoomEntity) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                db.followedRoomDao().deleteRoom(room.roomId)
            }
        }
    }

    private fun refreshRoom(room: FollowedRoomEntity) {
        lifecycleScope.launch {
            val info = LiveChecker.getRoomInfo(room.roomId)
            if (info != null) {
                withContext(Dispatchers.IO) {
                    db.followedRoomDao().updateLiveStatus(room.roomId, info.liveStatus)
                }
            }
        }
    }

    private fun refreshAll() {
        lifecycleScope.launch {
            val rooms = withContext(Dispatchers.IO) { db.followedRoomDao().getAllRooms() }
            rooms.collect { list ->
                for (room in list) {
                    refreshRoom(room)
                }
            }
        }
    }

    private fun showSettingsDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("定时检测设置")

        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        val switchEnabled = view.findViewById<Switch>(R.id.switch_enabled)
        val btnAddTime = view.findViewById<Button>(R.id.btn_add_time)
        val lvTimes = view.findViewById<ListView>(R.id.lv_times)

        val times = ScheduleManager.getTimes(this).toMutableList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, times)
        lvTimes.adapter = adapter

        switchEnabled.isChecked = ScheduleManager.isEnabled(this)
        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            ScheduleManager.setEnabled(this, isChecked)
        }

        btnAddTime.setOnClickListener {
            val cal = java.util.Calendar.getInstance()
            TimePickerDialog(this, { _, hour, minute ->
                ScheduleManager.addTime(this, hour, minute)
                times.clear()
                times.addAll(ScheduleManager.getTimes(this))
                adapter.notifyDataSetChanged()
            }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), true).show()
        }

        lvTimes.setOnItemLongClickListener { _, _, position, _ ->
            AlertDialog.Builder(this)
                .setMessage("删除此时间点？")
                .setPositiveButton("确定") { _, _ ->
                    val timeStr = times[position]
                    ScheduleManager.removeTime(this, timeStr)
                    times.clear()
                    times.addAll(ScheduleManager.getTimes(this))
                    adapter.notifyDataSetChanged()
                }
                .setNegativeButton("取消", null)
                .show()
            true
        }

        builder.setPositiveButton("完成", null)
        builder.setView(view)
        builder.show()
    }
}

class RoomAdapter(
    private val onDelete: (FollowedRoomEntity) -> Unit,
    private val onRefresh: (FollowedRoomEntity) -> Unit
) : RecyclerView.Adapter<RoomAdapter.ViewHolder>() {

    private var rooms = listOf<FollowedRoomEntity>()

    fun submitList(list: List<FollowedRoomEntity>) {
        rooms = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_room, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val room = rooms[position]
        holder.tvName.text = room.uname
        val status = if (room.liveStatus == 1) "正在直播" else "未直播"
        holder.tvStatus.text = status
        holder.tvStatus.setTextColor(
            if (room.liveStatus == 1) holder.itemView.context.getColor(android.R.color.holo_green_dark)
            else holder.itemView.context.getColor(android.R.color.darker_gray)
        )
        holder.btnDelete.setOnClickListener { onDelete(room) }
        holder.itemView.setOnLongClickListener {
            onDelete(room)
            true
        }
        holder.btnRefresh.setOnClickListener { onRefresh(room) }
    }

    override fun getItemCount() = rooms.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_name)
        val tvStatus: TextView = itemView.findViewById(R.id.tv_status)
        val btnDelete: ImageView = itemView.findViewById(R.id.btn_delete)
        val btnRefresh: ImageView = itemView.findViewById(R.id.btn_refresh)
    }
}
