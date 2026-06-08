package cardGame;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ChatClient {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter server IP: ");
        String serverIp = scanner.nextLine();

        System.out.print("Enter server port: ");
        int port = Integer.parseInt(scanner.nextLine());

        System.out.print("Enter your name: ");
        String name = scanner.nextLine();

        try {
            Socket socket = new Socket(serverIp, port);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(
                    socket.getOutputStream(), true);

            // Thread to receive messages
            new Thread(() -> {
                try {
                    String msg;
                    while ((msg = in.readLine()) != null) {
                        System.out.println(msg);
                    }
                } catch (IOException e) {
                    System.out.println("Disconnected from server.");
                }
            }).start();

            // Send messages
            while (true) {
                String message = scanner.nextLine();
                out.println(name + ": " + message);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
