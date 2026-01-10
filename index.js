const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const path = require('path');

const app = express();
const server = http.createServer(app);

const io = new Server(server, {
    cors: { origin: "*", methods: ["GET", "POST"] },
    maxHttpBufferSize: 1e8 // Tăng giới hạn gửi file lên 100MB (đề phòng ảnh lớn)
});

app.use(express.static(path.join(__dirname, 'public')));

app.get('/', (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

io.on('connection', (socket) => {
    console.log('⚡ User connected:', socket.id);

    // 1. CHAT (Hỗ trợ cả Text, Ảnh, File)
    socket.on('chat_message', (data) => {
        // data bao gồm: { user, content, type: 'text'|'image'|'file', fileName, fileData }
        io.emit('chat_message', data);
    });

    // 2. HIỆU ỨNG TYPING (SOẠN TIN)
    socket.on('typing', (data) => {
        socket.broadcast.emit('typing', data); // Gửi cho người khác (trừ mình)
    });

    socket.on('stop_typing', () => {
        socket.broadcast.emit('stop_typing');
    });

    // 3. WEBRTC SIGNALING
    socket.on('offer', (data) => {
        socket.broadcast.emit('offer', data);
    });

    socket.on('answer', (data) => {
        socket.broadcast.emit('answer', data);
    });

    socket.on('candidate', (data) => {
        socket.broadcast.emit('candidate', data);
    });

    socket.on('call_rejected', () => {
        socket.broadcast.emit('call_rejected');
    });

    socket.on('disconnect', () => {
        console.log('❌ User disconnected:', socket.id);
    });
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
    console.log(`🚀 Server đang chạy tại port ${PORT}`);
});