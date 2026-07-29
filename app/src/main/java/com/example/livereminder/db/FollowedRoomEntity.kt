package com.example.livereminder.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "followed_rooms")
data class FollowedRoomEntity(
    @PrimaryKey
    val roomId: Long,          // 真实房间号
    val uname: String,         // 主播名称
    val liveStatus: Int = 0,   // 0=未直播, 1=直播中
    val addedTime: Long = System.currentTimeMillis()
)
