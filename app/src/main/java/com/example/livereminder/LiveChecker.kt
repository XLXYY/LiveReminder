package com.example.livereminder

import com.example.livereminder.db.FollowedRoomEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object LiveChecker {

    data class RoomInfo(val roomId: Long, val uname: String, val liveStatus: Int)

    /**
     * 根据直播间号获取信息，会自动纠正短 ID
     */
    suspend fun getRoomInfo(inputId: Long): RoomInfo? = withContext(Dispatchers.IO) {
        try {
            // 先用 get_info 请求，B 站 API 可处理短 ID 返回真实 room_id
            val url = URL("https://api.live.bilibili.com/room/v1/Room/get_info?room_id=$inputId")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (conn.responseCode == 200) {
                val json = conn.inputStream.bufferedReader().readText()
                val obj = JSONObject(json)
                val code = obj.getInt("code")
                if (code == 0) {
                    val data = obj.getJSONObject("data")
                    val roomInfo = data.getJSONObject("room_info")
                    val realRoomId = roomInfo.getLong("room_id")
                    val uname = roomInfo.getString("uname")
                    val liveStatus = roomInfo.getInt("live_status")
                    RoomInfo(realRoomId, uname, liveStatus)
                } else null
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 解析用户输入的直播间号或链接
     */
    fun parseRoomId(input: String): Long? {
        val trimmed = input.trim()
        // 直接数字
        if (trimmed.matches(Regex("\\d+"))) return trimmed.toLongOrNull()
        // 链接形式 https://live.bilibili.com/12345 或带参数
        val regex = Regex("bilibili\\.com/(\\d+)")
        val match = regex.find(trimmed)
        if (match != null) {
            return match.groupValues[1].toLongOrNull()
        }
        return null
    }
}
