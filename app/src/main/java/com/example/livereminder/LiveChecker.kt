package com.example.livereminder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object LiveChecker {

    data class RoomInfo(val roomId: Long, val uname: String, val liveStatus: Int)
    data class CheckResult(
        val roomInfo: RoomInfo?,
        val errorMsg: String? = null  // 为 null 表示成功
    )

    /**
     * 检测直播间信息，返回更详细的结果
     */
    suspend fun getRoomInfoWithResult(inputId: Long): CheckResult = withContext(Dispatchers.IO) {
        try {
            // 优先尝试主 API
            var result = requestApi("https://api.live.bilibili.com/room/v1/Room/get_info?room_id=$inputId")
            if (result != null) return@withContext result

            // 主 API 失败，尝试备用 API
            result = requestApi("https://api.live.bilibili.com/xlive/web-room/v1/index/getInfoByRoom?room_id=$inputId")
            if (result != null) return@withContext result

            CheckResult(null, "房间不存在或网络错误（请检查电视联网）")
        } catch (e: Exception) {
            e.printStackTrace()
            CheckResult(null, "网络异常: ${e.localizedMessage ?: "未知错误"}")
        }
    }

    /**
     * 兼容旧版调用（返回 RoomInfo?）
     */
    suspend fun getRoomInfo(inputId: Long): RoomInfo? {
        val result = getRoomInfoWithResult(inputId)
        return result.roomInfo
    }

    private fun requestApi(urlStr: String): CheckResult? {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0") // 模拟浏览器

            if (conn.responseCode == 200) {
                val json = conn.inputStream.bufferedReader().readText()
                val obj = JSONObject(json)
                val code = obj.getInt("code")
                if (code != 0) {
                    // B 站返回的错误信息
                    val msg = obj.optString("message", "未知错误")
                    return CheckResult(null, "B站接口返回: $msg")
                }

                // 尝试从主 API 格式解析
                var data = obj.optJSONObject("data")
                if (data != null) {
                    val roomInfo = data.optJSONObject("room_info")
                    if (roomInfo != null) {
                        val realRoomId = roomInfo.getLong("room_id")
                        val uname = roomInfo.getString("uname")
                        val liveStatus = roomInfo.getInt("live_status")
                        return CheckResult(RoomInfo(realRoomId, uname, liveStatus))
                    }

                    // 备用 API 格式：可能字段不同
                    val realRoomId = data.optLong("room_id", 0)
                    val uname = data.optString("uname", "")
                    val liveStatus = data.optInt("live_status", 0)
                    if (realRoomId != 0L && uname.isNotEmpty()) {
                        return CheckResult(RoomInfo(realRoomId, uname, liveStatus))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            conn?.disconnect()
        }
        return null
    }

    /**
     * 解析用户输入
     */
    fun parseRoomId(input: String): Long? {
        val trimmed = input.trim()
        if (trimmed.matches(Regex("\\d+"))) return trimmed.toLongOrNull()
        val regex = Regex("bilibili\\.com/(\\d+)")
        val match = regex.find(trimmed)
        return match?.groupValues?.get(1)?.toLongOrNull()
    }
}
