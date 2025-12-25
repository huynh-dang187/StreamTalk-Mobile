package com.example.androidclient

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

// 1. ENTITY: Định nghĩa bảng dữ liệu (Giống data class ChatMessage nhưng dành cho DB)
@Entity(tableName = "chat_history")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0, // Tự động tăng ID (1, 2, 3...)
    val user: String,
    val content: String,
    val image: String?,
    val fileData: String?,
    val fileName: String?,
    val isMine: Boolean,
    val timestamp: Long = System.currentTimeMillis() // Lưu thời gian để sau này sắp xếp
)

// 2. DAO: Các lệnh SQL (Thêm, Sửa, Xóa, Lấy)
@Dao
interface ChatDao {
    // Lấy tất cả tin nhắn
    @Query("SELECT * FROM chat_history ORDER BY timestamp ASC")
    suspend fun getAllMessages(): List<MessageEntity>

    // Thêm tin nhắn mới
    @Insert
    suspend fun insertMessage(message: MessageEntity)

    // Xóa hết (Dùng khi muốn reset)
    @Query("DELETE FROM chat_history")
    suspend fun clearAll()
}

// 3. DATABASE: Cục tổng quản lý kết nối
@Database(entities = [MessageEntity::class], version = 1, exportSchema = false)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null

        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_database" // Tên file database trong máy
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}