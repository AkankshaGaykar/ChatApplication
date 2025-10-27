
Web Chat Application (Java backend with Web UI)
---------------------------------------------
Structure:
 - src/chatapp/WebChatServer.java  (Java HTTP server + SSE)
 - web/index.html                 (Web UI)

How to compile & run:
1) From project root folder (where src and web folders are):
   javac -d out src/chatapp/WebChatServer.java
2) Run server:
   java -cp out chatapp.WebChatServer
3) Open browser: http://localhost:8000
4) Enter name + message. Open multiple browser tabs to chat between users.

Notes:
 - Uses Server-Sent Events (SSE) for real-time updates (works in modern browsers).
 - No external libraries required (uses com.sun.net.httpserver included in the JDK).
 - If port 8000 is busy, change PORT value in WebChatServer.java and recompile.
