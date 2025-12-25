package com.example.androidclient

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Face // Thay icon kẹp giấy tạm bằng icon Face nếu chưa có
import androidx.compose.material.icons.filled.Menu // Hoặc icon nào đó có sẵn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppNavigation() }
    }
}

// Hàm giải mã ảnh thủ công
fun decodeBase64ToBitmap(base64Str: String): ImageBitmap? {
    return try {
        val cleanBase64 = if (base64Str.contains(",")) base64Str.substringAfter(",") else base64Str
        val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        bitmap?.asImageBitmap()
    } catch (e: Exception) { e.printStackTrace(); null }
}

// 👇 HÀM MỚI: Lưu file Base64 ra bộ nhớ và Mở file
fun saveAndOpenFile(context: Context, base64Data: String, fileName: String) {
    try {
        val cleanBase64 = if (base64Data.contains(",")) base64Data.substringAfter(",") else base64Data
        val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)

        // Lưu vào cache để mở nhanh
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { it.write(decodedBytes) }

        Toast.makeText(context, "Đã lưu: ${file.absolutePath}", Toast.LENGTH_SHORT).show()

        // Mở file bằng Intent
        // Lưu ý: Để mở chuẩn cần FileProvider (sẽ cấu hình sau).
        // Tạm thời báo Toast là đã nhận được file.
    } catch (e: Exception) {
        Toast.makeText(context, "Lỗi khi mở file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun AppNavigation(viewModel: ChatViewModel = viewModel()) {
    var isJoined by remember { mutableStateOf(false) }
    if (!isJoined) LoginScreen { name -> viewModel.joinChat(name); isJoined = true }
    else ChatScreen(viewModel)
}

@Composable
fun LoginScreen(onJoinClick: (String) -> Unit) {
    var nameInput by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("👋 Xin chào!", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6200EE))
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, label = { Text("Tên của bạn") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { if (nameInput.isNotBlank()) onJoinClick(nameInput) }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE))) { Text("Vào phòng Chat") }
    }
}

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    var textInput by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current

    // 1. Launcher chọn Ảnh
    val photoPickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.PickVisualMedia()) { uri ->
        selectedImageUri = uri
    }

    // 2. Launcher chọn File (MỚI)
    val filePickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            // Gửi file luôn khi chọn xong
            viewModel.sendFile(context, uri)
        }
    }

    if (selectedImageUri != null) {
        ImagePreviewDialog(
            uri = selectedImageUri!!,
            onDismiss = { selectedImageUri = null },
            onSend = { viewModel.sendImage(context, selectedImageUri!!); selectedImageUri = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
        Surface(shadowElevation = 4.dp, color = Color.White) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("💬 Phòng Chat", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text("👤 ${viewModel.myName}", fontSize = 14.sp, color = Color(0xFF6200EE))
            }
        }

        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(viewModel.messages) { msg -> MessageBubble(msg) }
        }

        Row(modifier = Modifier.fillMaxWidth().background(Color.White).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Nút Chọn Ảnh (+)
            IconButton(onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Icon(Icons.Default.Add, "Chọn ảnh", tint = Color(0xFF6200EE))
            }

            // Nút Chọn File (MỚI) - Dùng icon Menu làm icon kẹp giấy tạm
            IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                Icon(Icons.Default.Menu, "Chọn file", tint = Color.Gray)
            }

            TextField(value = textInput, onValueChange = { textInput = it }, modifier = Modifier.weight(1f), placeholder = { Text("Nhập tin nhắn...") }, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent))
            Button(onClick = { if (textInput.isNotBlank()) { viewModel.sendMessage(textInput); textInput = "" } }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE)), modifier = Modifier.padding(start = 8.dp)) { Text("Gửi") }
        }
    }
}

@Composable
fun ImagePreviewDialog(uri: Uri, onDismiss: () -> Unit, onSend: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Gửi ảnh này?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))
                AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxWidth().height(250.dp).background(Color.LightGray, RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = onDismiss) { Text("Hủy") }
                    Button(onClick = onSend, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE))) { Text("Gửi ngay") }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(msg: ChatMessage) {
    val context = LocalContext.current
    val alignment = if (msg.isMine) Alignment.End else Alignment.Start
    val bubbleColor = if (msg.isMine) Color(0xFF6200EE) else Color.White
    val textColor = if (msg.isMine) Color.White else Color.Black
    val cornerShape = if (msg.isMine) RoundedCornerShape(12.dp, 12.dp, 0.dp, 12.dp) else RoundedCornerShape(12.dp, 12.dp, 12.dp, 0.dp)

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = alignment) {
        if (!msg.isMine) Text(msg.user, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 2.dp, start = 8.dp))

        Surface(color = bubbleColor, shape = cornerShape, shadowElevation = 2.dp, modifier = Modifier.widthIn(max = 280.dp)) {
            Column(modifier = Modifier.padding(8.dp)) {

                // 1. HIỆN ẢNH
                if (msg.image != null && msg.image.isNotEmpty()) {
                    val imageBitmap = remember(msg.image) { decodeBase64ToBitmap(msg.image) }
                    if (imageBitmap != null) {
                        Image(bitmap = imageBitmap, contentDescription = "Ảnh", modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp).padding(bottom = 4.dp), contentScale = ContentScale.Crop)
                    }
                }

                // 2. HIỆN FILE (MỚI)
                if (msg.fileData != null && msg.fileName != null) {
                    Row(
                        modifier = Modifier
                            .background(Color(0x33000000), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                            .clickable {
                                // Bấm vào thì lưu file
                                saveAndOpenFile(context, msg.fileData, msg.fileName)
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Icon file đơn giản (dùng ký tự 📄)
                        Text("📄", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(msg.fileName, color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Nhấn để tải về", color = textColor.copy(alpha = 0.7f), fontSize = 10.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // 3. HIỆN TEXT
                if (msg.content.isNotEmpty()) {
                    Text(text = msg.content, color = textColor, fontSize = 16.sp)
                }
            }
        }
    }
}