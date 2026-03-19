# 🔗 Reti Connection

Un server **multi-client** basato su **Java NIO** (Non-Blocking I/O) che gestisce connessioni in tempo reale sulla porta **8080**.

---

## 🚀 Caratteristiche

- ✅ **Server NIO** – Gestione asincrona delle connessioni
- ✅ **Multi-client** – Supporta molteplici client simultanei
- ✅ **Buffer efficiente** – Comunicazione tramite ByteBuffer

---

## 📦 Struttura

```
src/
├── server/    Server NIO
├── client/    Client
└── common/    Utilities condivise
```

---

## ▶️ Avvio

```bash
javac src/server/ServerMain.java
java -cp src server.ServerMain
```

Server in ascolto su `localhost:8080`

---

## 📝 Licenza

Progetto universitario - Lab Reti 3
