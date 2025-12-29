package com.example.androidclient

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import org.webrtc.SurfaceViewRenderer
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppNavigation() }
    }
}

// ... (Giữ nguyên các hàm decodeBase64ToBitmap và saveAndOpenFile cũ của bạn)
fun decodeBase64ToBitmap(base64Str: String): ImageBitmap? {
    return try {
        val cleanBase64 = if (base64Str.contains(",")) base64Str.substringAfter(",") else base64Str
        val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        bitmap?.asImageBitmap()
    } catch (e: Exception) { e.printStackTrace(); null }
}

fun saveAndOpenFile(context: Context, base64Data: String, fileName: String) {
    try {
        val cleanBase64 = if (base64Data.contains(",")) base64Data.substringAfter(",") else base64Data
        val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { it.write(decodedBytes) }
        Toast.makeText(context, "Đã lưu: ${file.absolutePath}", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
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
    var showCamera by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val photoPickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.PickVisualMedia()) { uri -> selectedImageUri = uri }
    val filePickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri -> if (uri != null) viewModel.sendFile(context, uri) }

    if (selectedImageUri != null) {
        ImagePreviewDialog(uri = selectedImageUri!!, onDismiss = { selectedImageUri = null }, onSend = { viewModel.sendImage(context, selectedImageUri!!); selectedImageUri = null })
    }

    // UI VIDEO CALL
    if (showCamera) {
        VideoCallDialog(
            context = context,
            viewModel = viewModel,
            onDismiss = { showCamera = false }
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
            IconButton(onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Icon(Icons.Default.Add, "Ảnh", tint = Color(0xFF6200EE))
            }
            IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                Icon(Icons.Default.Menu, "File", tint = Color.Gray)
            }
            IconButton(onClick = {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    showCamera = true
                } else {
                    Toast.makeText(context, "Cần quyền Camera!", Toast.LENGTH_SHORT).show()
                    ActivityCompat.requestPermissions(context as android.app.Activity, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 101)
                }
            }) {
                Text("📹", fontSize = 24.sp)
            }
            TextField(value = textInput, onValueChange = { textInput = it }, modifier = Modifier.weight(1f), placeholder = { Text("Nhập tin nhắn...") }, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent))
            Button(onClick = { if (textInput.isNotBlank()) { viewModel.sendMessage(textInput); textInput = "" } }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE)), modifier = Modifier.padding(start = 8.dp)) { Text("Gửi") }
        }
    }
}

@Composable
fun VideoCallDialog(context: Context, viewModel: ChatViewModel, onDismiss: () -> Unit) {
    // Khởi tạo WebRTC Client khi mở Dialog
    val rtcClient = remember {
        WebRTCClient(context) { type, data ->
            // Callback: Khi WebRTC có tín hiệu (Offer/Answer/Ice) -> Gửi qua Socket
            viewModel.sendSignal(type, data)
        }
    }

    // Lắng nghe tín hiệu từ Server (Socket) để cập nhật WebRTC
    LaunchedEffect(Unit) {
        viewModel.webRTCEvent.collect { signal ->
            rtcClient.onRemoteSessionReceived(signal.data)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

            // 1. VIDEO ĐỐI PHƯƠNG (Remote) - Full màn hình
            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        rtcClient.createPeerConnection(this) // Gắn view này để nhận video đối phương
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // 2. VIDEO CỦA MÌNH (Local) - Góc nhỏ
            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        rtcClient.startLocalVideo(this) // Gắn view này để hiện camera mình
                        setZOrderOnTop(true) // Đè lên trên
                    }
                },
                modifier = Modifier.width(120.dp).height(160.dp).align(Alignment.TopEnd).padding(16.dp).background(Color.DarkGray)
            )

            // 3. CÁC NÚT ĐIỀU KHIỂN
            Row(modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)) {
                Button(onClick = { rtcClient.call() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Green)) {
                    Text("📞 Gọi")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = {
                    rtcClient.close()
                    onDismiss()
                }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                    Text("❌ Tắt")
                }
            }
        }
    }
}

@Composable
fun ImagePreviewDialog(uri: Uri, onDismiss: () -> Unit, onSend: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxWidth().height(250.dp).background(Color.LightGray), contentScale = ContentScale.Fit)
                Spacer(modifier = Modifier.height(16.dp))
                Row {
                    TextButton(onClick = onDismiss) { Text("Hủy") }
                    Button(onClick = onSend) { Text("Gửi") }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(msg: ChatMessage) {
    // (Giữ nguyên code MessageBubble của bạn, không thay đổi)
    val context = LocalContext.current
    val alignment = if (msg.isMine) Alignment.End else Alignment.Start
    val bubbleColor = if (msg.isMine) Color(0xFF6200EE) else Color.White
    val textColor = if (msg.isMine) Color.White else Color.Black

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = alignment) {
        if (!msg.isMine) Text(msg.user, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 2.dp, start = 8.dp))
        Surface(color = bubbleColor, shape = RoundedCornerShape(8.dp), shadowElevation = 2.dp, modifier = Modifier.widthIn(max = 280.dp)) {
            Column(modifier = Modifier.padding(8.dp)) {
                if (!msg.image.isNullOrEmpty()) {
                    val bmp = remember(msg.image) { decodeBase64ToBitmap(msg.image) }
                    if (bmp != null) Image(bitmap = bmp, contentDescription = null, modifier = Modifier.height(200.dp))
                }
                if (!msg.fileData.isNullOrEmpty()) {
                    Text("📄 ${msg.fileName}\n(Chạm để tải)", color = textColor, modifier = Modifier.clickable { saveAndOpenFile(context, msg.fileData, msg.fileName!!) })
                }
                if (msg.content.isNotEmpty()) Text(msg.content, color = textColor)
            }
        }
    }
}