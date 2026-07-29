private fun addRoom() {
    val input = etInput.text.toString().trim()
    if (input.isEmpty()) {
        Toast.makeText(this, "请输入房间号或链接", Toast.LENGTH_SHORT).show()
        return
    }
    val roomId = LiveChecker.parseRoomId(input)
    if (roomId == null) {
        Toast.makeText(this, "格式错误，请输入纯数字或 B 站直播间链接", Toast.LENGTH_SHORT).show()
        return
    }
    lifecycleScope.launch {
        // 使用带详细错误的方法
        val result = LiveChecker.getRoomInfoWithResult(roomId)
        if (result.roomInfo != null) {
            withContext(Dispatchers.IO) {
                db.followedRoomDao().insertRoom(
                    FollowedRoomEntity(
                        roomId = result.roomInfo.roomId,
                        uname = result.roomInfo.uname,
                        liveStatus = result.roomInfo.liveStatus
                    )
                )
            }
            etInput.text.clear()
            Toast.makeText(this@MainActivity, "已添加 ${result.roomInfo.uname}", Toast.LENGTH_SHORT).show()
        } else {
            // 显示具体错误原因
            val error = result.errorMsg ?: "未知错误"
            Toast.makeText(this@MainActivity, "添加失败: $error", Toast.LENGTH_LONG).show()
        }
    }
}
