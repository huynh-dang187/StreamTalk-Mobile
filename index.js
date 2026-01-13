const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const path = require('path');
const mongoose = require('mongoose'); 

const app = express();
const server = http.createServer(app);

// 👇 MongoDB của bạn (Giữ nguyên)
const MONGO_URI = "mongodb+srv://admin:huynhdang187@admin.gxovlx7.mongodb.net/?appName=admin";

mongoose.connect(MONGO_URI)
    .then(() => console.log('✅ Đã kết nối MongoDB Atlas'))
    .catch(err => console.error('❌ Lỗi kết nối MongoDB:', err));

// --- 1. CẬP NHẬT SCHEMA (Thêm buddyId & Avatar) ---
const UserSchema = new mongoose.Schema({
    username: { type: String, required: true, unique: true },
    password: { type: String, required: true },
    buddyId: { type: String, unique: true }, // ID 6 số để kết bạn
    avatar: { type: Number, default: 1 },    // Lưu ID ảnh đại diện
    createdAt: { type: Date, default: Date.now }
});
const User = mongoose.model('User', UserSchema);

const io = new Server(server, {
    cors: { origin: "*", methods: ["GET", "POST"] },
    maxHttpBufferSize: 1e8 
});

app.use(express.static(path.join(__dirname, 'public')));
app.use(express.json());

// --- 2. API ĐĂNG KÝ (Tự tạo Buddy ID) ---
app.post('/api/register', async (req, res) => {
    try {
        const { username, password } = req.body;
        if (!username || !password) return res.json({ success: false, message: "Thiếu thông tin!" });
        
        const existingUser = await User.findOne({ username });
        if (existingUser) return res.json({ success: false, message: "Tên này đã có người dùng!" });

        // Tạo Buddy ID ngẫu nhiên (6 số)
        const randomBuddyId = Math.floor(100000 + Math.random() * 900000).toString();
        const randomAvatar = Math.floor(Math.random() * 12) + 1;

        const newUser = new User({ 
            username, 
            password,
            buddyId: randomBuddyId,
            avatar: randomAvatar
        });
        
        await newUser.save();
        res.json({ success: true, message: "Đăng ký thành công! Hãy đăng nhập." });
    } catch (e) {
        res.json({ success: false, message: "Lỗi server: " + e.message });
    }
});

// --- 3. API ĐĂNG NHẬP (Trả về cả Buddy ID) ---
app.post('/api/login', async (req, res) => {
    try {
        const { username, password } = req.body;
        const user = await User.findOne({ username, password });
        
        if (user) {
            // Trả về full thông tin để Client lưu vào localStorage
            res.json({ 
                success: true, 
                username: user.username,
                buddyId: user.buddyId,
                avatar: user.avatar
            });
        } else {
            res.json({ success: false, message: "Sai tên hoặc mật khẩu!" });
        }
    } catch (e) {
        res.json({ success: false, message: "Lỗi server" });
    }
});

app.get('/', (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'index.html'));
});
// --- 4. SOCKET.IO (CẬP NHẬT LOGIC KẾT BẠN) ---
let onlineUsers = {}; // { socketId: { id, username, avatar, socketId } }

io.on('connection', (socket) => {
    console.log('⚡ User connected:', socket.id);

    // 1. Báo danh
    socket.on('register_user', (userData) => {
        onlineUsers[socket.id] = { ...userData, socketId: socket.id };
        io.emit('online_users', Object.values(onlineUsers)); // Báo cho mọi người
    });

    // 2. Chat & Call
    socket.on('chat_message', (data) => { io.emit('chat_message', data); });
    socket.on('offer', (data) => { socket.broadcast.emit('offer', data); });
    socket.on('answer', (data) => { socket.broadcast.emit('answer', data); });
    socket.on('candidate', (data) => { socket.broadcast.emit('candidate', data); });

    // --- 3. LOGIC KẾT BẠN (MỚI) ---
    
    // A gửi lời mời cho B
    socket.on('send_friend_request', ({ toId, fromUser }) => {
        // Tìm socket của người nhận (B) dựa trên ID
        const receiverSocketId = Object.keys(onlineUsers).find(
            key => onlineUsers[key].id === toId
        );

        if (receiverSocketId) {
            // Gửi thông báo riêng cho B
            io.to(receiverSocketId).emit('incoming_friend_request', fromUser);
        }
    });

    // B chấp nhận lời mời của A
    socket.on('accept_friend_request', ({ toId, fromUser }) => {
        const receiverSocketId = Object.keys(onlineUsers).find(
            key => onlineUsers[key].id === toId
        );

        if (receiverSocketId) {
            // Báo lại cho A biết là B đã đồng ý
            io.to(receiverSocketId).emit('friend_request_accepted', fromUser);
        }
    });

    // 4. Ngắt kết nối
    socket.on('disconnect', () => {
        delete onlineUsers[socket.id];
        io.emit('online_users', Object.values(onlineUsers));
    });
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
    console.log(`🚀 Server đang chạy tại port ${PORT}`);
});