const express = require('express');
const http = require('http');
const { Server } = require("socket.io");

const app = express();
const server = http.createServer(app);

// Cấu hình Socket.io
const io = new Server(server, {
    cors: {
        origin: "*", 
        methods: ["GET", "POST"]
    },
    // 👇 QUAN TRỌNG: Cho phép gói tin lên tới 50MB (để gửi ảnh/video)
    maxHttpBufferSize: 50 * 1024 * 1024 
});

// Cho phép truy cập thư mục public (nơi chứa file html, css)
app.use(express.static('public'));

// Lắng nghe kết nối
io.on('connection', (socket) => {
    console.log('⚡ Có người kết nối: ' + socket.id);

    // Lắng nghe sự kiện gửi tin nhắn (Gồm cả chữ và ảnh)
    socket.on('chat_message', (data) => {
        // data là object: { user: "Ten", content: "Noi dung", image: "base64..." }
        console.log(`📩 Tin nhắn từ ${data.user}`);
        
        // Gửi lại cho TẤT CẢ mọi người (Broadcast)
        io.emit('chat_message', data);
    });

    socket.on('disconnect', () => {
        console.log('❌ Một user đã thoát');
    });
});

// Chạy Server tại port 3000
server.listen(3000, () => {
    console.log('🚀 Server đang chạy tại http://localhost:3000');
});