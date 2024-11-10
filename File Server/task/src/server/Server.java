package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class Server {
    public static final String ADDRESS = "127.0.0.1";
    public static final int PORT = 23456;
    private final Map<String, Boolean> files = new HashMap<>();

    public Server() {
        initStorage();
        try (ServerSocket serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName(ADDRESS))) {
            System.out.println("Server started!");
            Socket socket = serverSocket.accept();
            DataInputStream input = new DataInputStream(socket.getInputStream());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            System.out.printf("Received: %s\n", input.readUTF());
            String answer = "All files were sent!";
            output.writeUTF(answer);
            System.out.printf("Sent: %s\n", answer);
        } catch (IOException e) {
            System.err.println("IO error: " + e.getMessage());
        }
    }

    private void initStorage() {
        for (int i = 1; i <= 10; i++) {
            String fileName = "file" + i;
            files.put(fileName, false);
        }
    }

    public String addFile(String fileName) {
        String feedback;
        if (files.containsKey(fileName) && !files.get(fileName)) {
            files.replace(fileName, true);
            feedback = String.format("The file %s added successfully", fileName);
        } else {
            feedback = String.format("Cannot add the file %s", fileName);
        }
        return feedback;
    }

    public String getFile(String query) {
        String feedback;
        if (files.containsKey(query) && files.get(query)) {
            feedback = String.format("The file %s was sent", query);
        } else {
            feedback = String.format("The file %s not found", query);
        }
        return feedback;
    }

    public String deleteFile(String query) {
        String feedback;
        if (files.containsKey(query) && files.get(query)) {
            files.replace(query, false);
            feedback = String.format("The file %s was deleted", query);
        } else {
            feedback = String.format("The file %s not found", query);
        }
        return feedback;
    }
}
