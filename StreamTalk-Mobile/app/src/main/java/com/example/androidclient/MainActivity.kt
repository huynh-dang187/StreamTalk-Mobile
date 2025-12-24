package com.example.androidclient

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage // 👈 Import thư viện Coil mới thêm

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppNavigation()
        }
    }
}

@Composable
fun AppNavigation(viewModel: ChatViewModel = viewModel()) {
    var isJoined by remember { mutableStateOf(false) }

    if (!isJoined) {
        LoginScreen(onJoinClick = { name ->
            viewModel.joinChat(name)
            isJoined = true
        })
    } else {
        ChatScreen(viewModel)
    }
}

@Composable
fun LoginScreen(onJoinClick: (String) -> Unit) {
    var nameInput by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "👋 Xin chào!", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6200EE))
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = nameInput,
            onValueChange = { nameInput = it },
            label = { Text("Tên của bạn") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { if (nameInput.isNotBlank()) onJoinClick(nameInput) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE))
        ) { Text("Vào phòng Chat", fontSize = 18.sp) }
    }
}

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    var textInput by remember { mutableStateOf("") }

    // 👇 1. Biến lưu ảnh đang được chọn (nếu có)
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    // 👇 2. Bộ khởi chạy thư viện ảnh (Photo Picker)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        // Khi người dùng chọn xong ảnh, uri sẽ được trả về đây
        selectedImageUri = uri
    }

    // 👇 3. Nếu đang có ảnh được chọn -> Hiện Dialog xem trước
    if (selectedImageUri != null) {
        ImagePreviewDialog(
            uri = selectedImageUri!!,
            onDismiss = { selectedImageUri = null }, // Hủy chọn
            onSend = {
                // Tạm thời chỉ đóng dialog, bài sau sẽ xử lý gửi
                selectedImageUri = null
                // viewModel.sendImage(...) -> Sẽ làm ở bài sau
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
        // Header
        Surface(shadowElevation = 4.dp, color = Color.White) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "💬 Phòng Chat", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text(text = "👤 ${viewModel.myName}", fontSize = 14.sp, color = Color(0xFF6200EE))
            }
        }

        // List Chat
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(viewModel.messages) { msg -> MessageBubble(msg) }
        }

        // Input Area
        Row(
            modifier = Modifier.fillMaxWidth().background(Color.White).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 👇 4. Nút bấm dấu cộng (+) để chọn ảnh
            IconButton(onClick = {
                // Mở thư viện ảnh, chỉ lọc lấy ảnh (ImageOnly)
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }) {
                Icon(Icons.Default.Add, contentDescription = "Chọn ảnh", tint = Color(0xFF6200EE))
            }

            TextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Nhập tin nhắn...") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
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
            ) { Text("Gửi") }
        }
    }
}

// 👇 5. Composable hiển thị Dialog xem trước ảnh
@Composable
fun ImagePreviewDialog(uri: Uri, onDismiss: () -> Unit, onSend: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Gửi ảnh này?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))

                // Hiển thị ảnh dùng thư viện Coil
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .background(Color.LightGray, RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = onDismiss) { Text("Hủy") }
                    Button(onClick = onSend, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE))) {
                        Text("Gửi ngay")
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(msg: ChatMessage) {
    val alignment = if (msg.isMine) Alignment.End else Alignment.Start
    val bubbleColor = if (msg.isMine) Color(0xFF6200EE) else Color.White
    val textColor = if (msg.isMine) Color.White else Color.Black
    val cornerShape = if (msg.isMine) RoundedCornerShape(12.dp, 12.dp, 0.dp, 12.dp)
    else RoundedCornerShape(12.dp, 12.dp, 12.dp, 0.dp)

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = alignment) {
        if (!msg.isMine) Text(text = msg.user, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 2.dp, start = 8.dp))
        Surface(color = bubbleColor, shape = cornerShape, shadowElevation = 2.dp, modifier = Modifier.widthIn(max = 250.dp)) {
            Text(text = msg.content, color = textColor, modifier = Modifier.padding(12.dp), fontSize = 16.sp)
        }
    }
}