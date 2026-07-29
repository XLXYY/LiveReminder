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
        val errorMsg: String? = null
    )

    /**
     * 返回详细的结果对象，方便上层展示具体错误
     */
    suspend fun getRoomInfoWithResult(inputId: Long): CheckResult = withContext(Dispatchers.IO) {
        try {
            // 优先用这个新接口，返回稳定、无需 cookie，且字段明确
            var result = requestGetInfoByRoom(inputId)
            if (result != null) return@withContext result

            // 新接口失败，尝试旧接口
            result = requestGetInfo(inputId)
            if (result != null) return@withContext result

            CheckResult(null, "无法连接到 B 站 API，请检查网络或稍后重试")
        } catch (e: Exception) {
            e.printStackTrace()
            CheckResult(null, "网络异常: ${e.localizedMessage ?: "未知错误"}")
        }
    }

    /**
     * 兼容旧版调用，只返回 RoomInfo
     */
    suspend fun getRoomInfo(inputId: Long): RoomInfo? {
        return getRoomInfoWithResult(inputId).roomInfo
    }

    // 新接口：xlive/web-room/v1/index/getInfoByRoom
    private fun requestGetInfoByRoom(roomId: Long): CheckResult? {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("https://api.live.bilibili.com/xlive/web-room/v1/index/getInfoByRoom?room_id=$roomId")
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            // 模拟浏览器请求头，防止被拒
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            conn.setRequestProperty("Referer", "https://live.bilibili.com/")

            if (conn.responseCode == 200) {
                val json = conn.inputStream.bufferedReader().readText()
                val obj = JSONObject(json)
                val code = obj.getInt("code")
                if (code != 0) {
                    val msg = obj.optString("message", "未知错误")
                    return CheckResult(null, "B站接口返回错误: $msg")
                }
                val data = obj.getJSONObject("data")
                val roomInfoObj = data.getJSONObject("room_info")
                val realRoomId = roomInfoObj.getLong("room_id")
                val uname = roomInfoObj.getString("uname")
                val liveStatus = roomInfoObj.getInt("live_status")
                return CheckResult(RoomInfo(realRoomId, uname, liveStatus))
            } else {
                return CheckResult(null, "服务器响应异常: ${conn.responseCode}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            conn?.disconnect()
        }
        return null
    }

    // 旧接口：room/v1/Room/get_info，作为备选
    private fun requestGetInfo(roomId: Long): CheckResult? {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("https://api.live.bilibili.com/room/v1/Room/get_info?room_id=$roomId")
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            conn.setRequestProperty("Referer", "https://live.bilibili.com/")

            if (conn.responseCode == 200) {
                val json = conn.inputStream.bufferedReader().readText()
                val obj = JSONObject(json)
                val code = obj.getInt("code")
                if (code != 0) {
                    val msg = obj.optString("message", "未知错误")
                    return CheckResult(null, "B站接口返回错误: $msg")
                }
                val data = obj.getJSONObject("data")
                val roomInfo = data.getJSONObject("room_info")
                val realRoomId = roomInfo.getLong("room_id")
                val uname = roomInfo.getString("uname")
                val liveStatus = roomInfo.getInt("live_status")
                return CheckResult(RoomInfo(realRoomId, uname, liveStatus))
            } else {
                return CheckResult(null, "服务器响应异常: ${conn.responseCode}")
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
