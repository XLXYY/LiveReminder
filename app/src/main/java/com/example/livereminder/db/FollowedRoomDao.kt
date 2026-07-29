package com.example.livereminder.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FollowedRoomDao {
    @Query("SELECT * FROM followed_rooms ORDER BY addedTime DESC")
    fun getAllRooms(): Flow<List<FollowedRoomEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoom(room: FollowedRoomEntity)

    @Query("DELETE FROM followed_rooms WHERE roomId = :roomId")
    suspend fun deleteRoom(roomId: Long)

    @Query("UPDATE followed_rooms SET liveStatus = :status WHERE roomId = :roomId")
    suspend fun updateLiveStatus(roomId: Long, status: Int)
}
