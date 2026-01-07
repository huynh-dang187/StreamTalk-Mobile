const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const path = require('path');

const app = express();
const server = http.createServer(app);

// Cấu hình CORS để Mobile và Web khác IP vẫn gọi được nhau
const io = new Server(server, {
    cors: {
        origin: "*", 
        methods: ["GET", "POST"]
    }
});

// Trả về file giao diện Web khi truy cập vào IP máy tính
app.get('/', (req, res) => {
    // Đảm bảo bạn đã để file index.html cùng thư mục với server.js
   res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

io.on('connection', (socket) => {
    console.log('⚡ User connected:', socket.id);

    // 1. Chát
    socket.on('chat_message', (data) => {
        io.emit('chat_message', data);
    });

    // 2. WebRTC Signaling (Chuyển tiếp tín hiệu Video)
    socket.on('offer', (data) => {
        console.log("📡 Relaying Offer");
        socket.broadcast.emit('offer', data);
    });

    socket.on('answer', (data) => {
        console.log("📡 Relaying Answer");
        socket.broadcast.emit('answer', data);
    });

    socket.on('candidate', (data) => {
        // console.log("📡 Relaying Candidate");
        console.log("📡 Relaying Candidate:", data.candidate ? data.candidate.substring(0, 50) : 'null');
        socket.broadcast.emit('candidate', data);
    });

    socket.on('disconnect', () => {
        console.log('❌ User disconnected:', socket.id);
    });
});

// Lắng nghe trên tất cả các IP của máy (0.0.0.0)
server.listen(3000, '0.0.0.0', () => {
    console.log('🚀 Server is running on port 3000');
    console.log('👉 Mobile App should connect to: http://YOUR_PC_IP:3000');
});