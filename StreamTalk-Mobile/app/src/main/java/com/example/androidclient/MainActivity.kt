package com.example.androidclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChatScreen()
        }
    }
}

@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    var textInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) { // Nền xám nhạt

        // 1. Tiêu đề
        Surface(shadowElevation = 4.dp, color = Color.White) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "💬 Nexus Chat", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6200EE))
            }
        }

        // 2. Danh sách tin nhắn
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            reverseLayout = false
        ) {
            items(viewModel.messages) { msg ->
                MessageBubble(msg)
            }
        }

        // 3. Ô nhập liệu
        Row(
            modifier = Modifier.fillMaxWidth().background(Color.White).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Nhập tin nhắn...") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            Button(
                onClick = {
                    if (textInput.isNotBlank()) {
                        viewModel.sendMessage(textInput)
                        textInput = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE)),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("Gửi")
            }
        }
    }
}

// Composable riêng để vẽ từng tin nhắn
@Composable
fun MessageBubble(msg: ChatMessage) {
    // 1. SỬA LỖI Ở ĐÂY: Chuyển sang Alignment.End và Alignment.Start
    val alignment = if (msg.isMine) Alignment.End else Alignment.Start

    val bubbleColor = if (msg.isMine) Color(0xFF6200EE) else Color.White
    val textColor = if (msg.isMine) Color.White else Color.Black
    val cornerShape = if (msg.isMine)
        RoundedCornerShape(12.dp, 12.dp, 0.dp, 12.dp)
    else
        RoundedCornerShape(12.dp, 12.dp, 12.dp, 0.dp)

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = alignment // Giờ thì hết lỗi đỏ rồi nhé
    ) {
        // Tên người gửi
        if (!msg.isMine) {
            Text(text = msg.user, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 2.dp, start = 8.dp))
        }

        // Nội dung tin nhắn
        Surface(
            color = bubbleColor,
            shape = cornerShape,
            shadowElevation = 2.dp,
            modifier = Modifier.widthIn(max = 250.dp)
        ) {
            Text(
                text = msg.content,
                color = textColor,
                modifier = Modifier.padding(12.dp),
                fontSize = 16.sp
            )
        }
    }
}