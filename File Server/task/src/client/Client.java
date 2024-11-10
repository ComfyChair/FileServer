package client;

import server.RequestHeader;
import server.ResponseHeader;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Client {
    private static final int POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final Path dataPath = Path.of(System.getProperty("user.dir"),
            "src", "client", "data");
    private DataInputStream in;
    private DataOutputStream out;
    private final ExecutorService executor;

    public Client(String address, int port) {
        if (!dataPath.toFile().exists()) {
            dataPath.toFile().mkdirs();
        }
        executor = Executors.newFixedThreadPool(POOL_SIZE);
        try (Socket socket = new Socket(InetAddress.getByName(address), port)) {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
        } catch (UnknownHostException e) {
            System.err.println("Unknown host: " + address);
        } catch (IOException e) {
            System.err.println("I/O Exception: " + e.getMessage());
        }
    }

    public void startCLI() {
        Scanner scanner = new Scanner(System.in);
        String action = "";
        while (!action.equals("exit")) {
            System.out.println("Enter action (1 - get a file, 2 - save a file, 3 - delete a file): ");
            action = scanner.nextLine();
            switch (action) {
                case "1" -> executor.submit(getRequest(scanner));
                case "2" -> executor.submit(putRequest(scanner));
                case "3" -> executor.submit(deleteRequest(scanner));
                case "exit" -> exit();
            }
        }
    }

    private Runnable putRequest(Scanner scanner) {
        System.out.println("Enter name of the file: ");
        String srcName = scanner.nextLine();
        File file = dataPath.resolve(srcName).toFile();
        System.out.println("Enter name of the file to be saved on server: ");
        String destName = scanner.nextLine();
        String name = String.format("%s %s", srcName, destName);
        return () -> {
            // TODO: Add data
            RequestHeader request = new RequestHeader(RequestHeader.Type.PUT, name, "");
        };
    }

    private Runnable getRequest(Scanner scanner) {
        String identifier = readIdentifier(scanner);
        return () -> {
            RequestHeader request = new RequestHeader(RequestHeader.Type.GET, identifier, "");
            send(request);
            //TODO: process response
        };
    }


    private Runnable deleteRequest(Scanner scanner) {
        String identifier = readIdentifier(scanner);
        return () -> {
            RequestHeader request = new RequestHeader(RequestHeader.Type.DELETE, identifier, "");
            send(request);
            //TODO: process response
        };
    }

    private String readIdentifier(Scanner scanner) {
        System.out.println("Do you want to get the file by name or by id (1 - name, 2 - id):");
        String by = "";
        while (by.isEmpty()) {
            switch (scanner.nextLine()) {
                case "1" -> by = "name";
                case "2" -> by = "id";
                default -> System.out.println("Invalid input");
            }
        }
        System.out.printf("Enter %s: ", by);
        String identifier = scanner.nextLine();
        return String.format("BY_%s %s", by.toUpperCase(), identifier);
    }

    private void send(RequestHeader request) {
        try {
            out.writeUTF(request.toString());
            System.out.println("The request was sent.");
        } catch (IOException e) {
            System.err.println("I/O Exception: " + e.getMessage());
        }
    }

    private void exit() {
        executor.shutdown();
        try {
            out.writeUTF(new RequestHeader(RequestHeader.Type.EXIT, "", "").toString());
            in.close();
            out.close();
        } catch (IOException e) {
            System.err.println("I/O Exception: " + e.getMessage());
        }
        System.exit(0);
    }
}

