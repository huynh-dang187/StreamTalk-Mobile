const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const path = require('path');
const mongoose = require('mongoose'); 

const app = express();
const server = http.createServer(app);

// 👇 QUAN TRỌNG: Thay chuỗi kết nối MongoDB của bạn vào đây
// Ví dụ: "mongodb+srv://admin:matkhau123@cluster0.abcde.mongodb.net/?retryWrites=true&w=majority"
const MONGO_URI = "mongodb+srv://admin:huynhdang187@admin.gxovlx7.mongodb.net/?appName=admin";

// Kết nối MongoDB
mongoose.connect(MONGO_URI)
    .then(() => console.log('✅ Đã kết nối MongoDB Atlas'))
    .catch(err => console.error('❌ Lỗi kết nối MongoDB:', err));

// Định nghĩa bảng User
const UserSchema = new mongoose.Schema({
    username: { type: String, required: true, unique: true },
    password: { type: String, required: true }, 
    createdAt: { type: Date, default: Date.now }
});
const User = mongoose.model('User', UserSchema);

const io = new Server(server, {
    cors: { origin: "*", methods: ["GET", "POST"] },
    maxHttpBufferSize: 1e8 // Tăng giới hạn file lên 100MB
});

app.use(express.static(path.join(__dirname, 'public')));
app.use(express.json()); // Để đọc JSON từ Client gửi lên

// --- API ĐĂNG KÝ ---
app.post('/api/register', async (req, res) => {
    try {
        const { username, password } = req.body;
        if (!username || !password) return res.json({ success: false, message: "Thiếu thông tin!" });
        
        const existingUser = await User.findOne({ username });
        if (existingUser) return res.json({ success: false, message: "Tên này đã có người dùng!" });

        const newUser = new User({ username, password });
        await newUser.save();
        res.json({ success: true, message: "Đăng ký thành công! Hãy đăng nhập." });
    } catch (e) {
        res.json({ success: false, message: "Lỗi server: " + e.message });
    }
});

// --- API ĐĂNG NHẬP ---
app.post('/api/login', async (req, res) => {
    try {
        const { username, password } = req.body;
        const user = await User.findOne({ username, password });
        
        if (user) {
            res.json({ success: true, username: user.username });
        } else {
            res.json({ success: false, message: "Sai tên hoặc mật khẩu!" });
        }
    } catch (e) {
        res.json({ success: false, message: "Lỗi server" });
    }
});

// Trả về giao diện chính
app.get('/', (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

// --- SOCKET.IO LOGIC ---
io.on('connection', (socket) => {
    console.log('⚡ User connected:', socket.id);

    // 1. Chat (Text, Ảnh, File)
    socket.on('chat_message', (data) => {
        io.emit('chat_message', data);
    });

    // 2. Hiệu ứng Typing
    socket.on('typing', (data) => {
        socket.broadcast.emit('typing', data);
    });

    socket.on('stop_typing', () => {
        socket.broadcast.emit('stop_typing');
    });

    // 3. WebRTC Signaling (Video Call)
    socket.on('offer', (data) => { socket.broadcast.emit('offer', data); });
    socket.on('answer', (data) => { socket.broadcast.emit('answer', data); });
    socket.on('candidate', (data) => { socket.broadcast.emit('candidate', data); });
    socket.on('call_rejected', () => { socket.broadcast.emit('call_rejected'); });

    socket.on('disconnect', () => {
        console.log('❌ User disconnected:', socket.id);
    });
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
    console.log(`🚀 Server đang chạy tại port ${PORT}`);
});