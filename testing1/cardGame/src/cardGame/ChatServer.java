package cardGame;

import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {

	private static int PORT;
    private static final int MAX_CLIENTS = 4;
    private static Set<ClientHandler> clients = Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) {
        PORT = Integer.parseInt(
                System.getenv().getOrDefault("PORT", "5000")
        );

        System.out.println("Chat server starting on port " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

            while (true) {
                Socket socket = serverSocket.accept();

                if (clients.size() >= MAX_CLIENTS) {
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    out.println("Server full.");
                    socket.close();
                    continue;
                }

                ClientHandler client = new ClientHandler(socket);
                clients.add(client);
                new Thread(client).start();

                broadcast("A user has joined the chat.");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void broadcast(String message) {
        synchronized (clients) {
            for (ClientHandler client : new HashSet<>(clients)) { // copy to avoid concurrent modification
                if (client != null && client.out != null) { // <-- safe check
                    client.sendMessage(message);
                }
            }
        }
    }

    static void removeClient(ClientHandler client) {
        clients.remove(client);
        broadcast("A user has left the chat.");
    }

    static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                String message;
                while ((message = in.readLine()) != null) {
                    broadcast(message);
                }

            } catch (IOException e) {
                System.out.println("A client disconnected.");
            } finally {
                removeClient(this);
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }

        void sendMessage(String message) {
            if (out != null) { // <-- safe null check
                out.println(message);
            }
        }
    }
}
