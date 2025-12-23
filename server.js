const express = require('express');
const app = express();
const http = require('http');
const server = http.createServer(app);
const { Server } = require("socket.io");

// Cấu hình Socket.io
const io = new Server(server, {
    maxHttpBufferSize: 55 * 1024 * 1024 // Chuẩn bị sẵn cho file 50MB
});

// Phục vụ file html tĩnh trong thư mục public
app.use(express.static('public'));

io.on('connection', (socket) => {
    console.log('Có người kết nối: ' + socket.id);

    // Lắng nghe sự kiện chat
    socket.on('chat_message', (data) => {
        // data bây giờ là một Object: { user: "Huy", content: "Xin chào" }
        console.log("Tin nhắn mới:", data);
        
        // Gửi nguyên cục data này cho tất cả mọi người (bao gồm cả người gửi)
        io.emit('chat_message', data);
    });

    socket.on('disconnect', () => {
        console.log('User đã thoát: ' + socket.id);
    });
});

// Chạy server ở cổng 3000
server.listen(3000, () => {
    console.log('Server đang chạy tại http://localhost:3000');
});