const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const path = require('path');
const mongoose = require('mongoose'); 

const app = express();
const server = http.createServer(app);

// 👇 MongoDB của bạn
const MONGO_URI = "mongodb+srv://admin:huynhdang187@admin.gxovlx7.mongodb.net/?appName=admin";

mongoose.connect(MONGO_URI)
    .then(() => console.log('✅ Đã kết nối MongoDB Atlas'))
    .catch(err => console.error('❌ Lỗi kết nối MongoDB:', err));

const UserSchema = new mongoose.Schema({
    username: { type: String, required: true, unique: true },
    password: { type: String, required: true },
    buddyId: { type: String, unique: true },
    avatar: { type: Number, default: 1 },
    createdAt: { type: Date, default: Date.now }
});
const User = mongoose.model('User', UserSchema);

const io = new Server(server, { cors: { origin: "*" }, maxHttpBufferSize: 1e8 });

app.use(express.static(path.join(__dirname, 'public')));
app.use(express.json());

// API Đăng ký
app.post('/api/register', async (req, res) => {
    try {
        const { username, password } = req.body;
        if (!username || !password) return res.json({ success: false, message: "Thiếu thông tin!" });
        const existing = await User.findOne({ username });
        if (existing) return res.json({ success: false, message: "Tên đã tồn tại!" });
        
        const newUser = new User({ 
            username, password,
            buddyId: Math.floor(100000 + Math.random() * 900000).toString(),
            avatar: Math.floor(Math.random() * 70) + 1
        });
        await newUser.save();
        res.json({ success: true, message: "Đăng ký thành công!" });
    } catch (e) { res.json({ success: false, message: e.message }); }
});

// API Đăng nhập
app.post('/api/login', async (req, res) => {
    try {
        const { username, password } = req.body;
        const user = await User.findOne({ username, password });
        if (user) res.json({ success: true, username: user.username, buddyId: user.buddyId, avatar: user.avatar });
        else res.json({ success: false, message: "Sai thông tin!" });
    } catch (e) { res.json({ success: false, message: "Lỗi server" }); }
});

app.get('/', (req, res) => res.sendFile(path.join(__dirname, 'public', 'index.html')));

// --- SOCKET.IO REALTIME ROUTING ---
let onlineUsers = {}; // Map: socket.id -> user info

io.on('connection', (socket) => {
    console.log('⚡ User connected:', socket.id);

    // 1. Báo danh khi online
    socket.on('register_user', (userData) => {
        onlineUsers[socket.id] = { ...userData, socketId: socket.id };
        io.emit('online_users', Object.values(onlineUsers));
    });

    // Helper tìm socketId theo buddyId
    const findSocketById = (buddyId) => {
        return Object.keys(onlineUsers).find(key => onlineUsers[key].id == buddyId);
    };

    // 2. Chat riêng tư (Private Message)
    socket.on('private_message', (data) => {
        // data = { to: targetBuddyId, content, type... }
        const targetSocket = findSocketById(data.to);
        if (targetSocket) {
            io.to(targetSocket).emit('private_message', { ...data, from: onlineUsers[socket.id].id });
        }
    });

    // 3. Kết bạn
    socket.on('send_friend_request', ({ toId, fromUser }) => {
        const targetSocket = findSocketById(toId);
        if (targetSocket) io.to(targetSocket).emit('incoming_friend_request', fromUser);
    });

    socket.on('accept_friend_request', ({ toId, fromUser }) => {
        const targetSocket = findSocketById(toId);
        if (targetSocket) io.to(targetSocket).emit('friend_request_accepted', fromUser);
    });

    // 4. Video Call (Signaling P2P)
    // Chỉ gửi cho đúng người nhận (toId), không broadcast
    socket.on('video_offer', ({ to, offer }) => {
        const targetSocket = findSocketById(to);
        if (targetSocket) io.to(targetSocket).emit('video_offer', { offer, from: onlineUsers[socket.id].id, user: onlineUsers[socket.id] });
    });

    socket.on('video_answer', ({ to, answer }) => {
        const targetSocket = findSocketById(to);
        if (targetSocket) io.to(targetSocket).emit('video_answer', { answer });
    });

    socket.on('video_candidate', ({ to, candidate }) => {
        const targetSocket = findSocketById(to);
        if (targetSocket) io.to(targetSocket).emit('video_candidate', { candidate });
    });
    
    socket.on('video_reject', ({ to }) => {
        const targetSocket = findSocketById(to);
        if (targetSocket) io.to(targetSocket).emit('video_reject');
    });

    socket.on('disconnect', () => {
        delete onlineUsers[socket.id];
        io.emit('online_users', Object.values(onlineUsers));
    });
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => console.log(`Server chạy tại port ${PORT}`));