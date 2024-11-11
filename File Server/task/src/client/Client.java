package client;

import server.Request;
import server.Request.RequestType;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static client.Main.logger;

public class Client {
    private static final int POOL_SIZE = Runtime.getRuntime().availableProcessors();
    //private static final Path dataPath = Path.of(System.getProperty("user.dir"),
            //"src", "client", "data");
    private static final Path dataPath = Path.of(System.getProperty("user.dir"),
            "File Server", "task", "src", "client", "data");
    private DataInputStream in;
    private DataOutputStream out;
    private final ExecutorService executor;
    private int requestIdCounter = 0;
    private final Map<Integer, Request> requests = new HashMap<>();

    public Client(String address, int port) {
        if (!dataPath.toFile().exists()) {
            dataPath.toFile().mkdirs();
        }
        executor = Executors.newFixedThreadPool(POOL_SIZE);
        try (Socket socket = new Socket(InetAddress.getByName(address), port)) {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            System.err.println("I/O Exception: " + e.getMessage());
            System.exit(1);
        }
    }

    public void startCLI() {
        Scanner scanner = new Scanner(System.in);
        String action = "";
        executor.submit(this::processResponses);
        while (!action.equals("exit")) {
            System.out.println("Enter action (1 - get a file, 2 - save a file, 3 - delete a file): ");
            action = scanner.nextLine();
            switch (action) {
                case "1" -> sendGetRequest(scanner);
                case "2" -> sendPutRequest(scanner);
                case "3" -> sendDeleteRequest(scanner);
                case "exit" -> exit();
            }
        }
    }

    private void sendPutRequest(Scanner scanner) {
        System.out.println("Enter identifier of the file: ");
        String srcName = scanner.nextLine();
        File file = dataPath.resolve(srcName).toFile();
        if (!file.exists() || !file.isFile()) {
            System.err.println("File does not exist: " + srcName);
        } else {
            System.out.println("Enter identifier of the file to be saved on server: ");
            String destName = scanner.nextLine();
            String name = String.format("%s %s", srcName, destName);
            Request request = new Request(RequestType.PUT, name);
            executor.submit(() -> sendRequest(request, file));
        }
    }

    private void sendGetRequest(Scanner scanner) {
        Request.Identifier identifier = readIdentifier(scanner);
        Request request = new Request(RequestType.GET, identifier.type(), identifier.value());
        executor.submit(() -> sendRequest(request, null));
    }

    private void sendDeleteRequest(Scanner scanner) {
        Request.Identifier identifier = readIdentifier(scanner);
        Request request = new Request(RequestType.DELETE, identifier.type(), identifier.value());
        executor.submit(() -> sendRequest(request, null));
    }

    private Request.Identifier readIdentifier(Scanner scanner) {
        System.out.println("Do you want to get the file by identifier or by id (1 - name, 2 - id):");
        String by = "";
        while (by.isEmpty()) {
            switch (scanner.nextLine()) {
                case "1" -> by = "name";
                case "2" -> by = "id";
                default -> System.out.println("Invalid input");
            }
        }
        Request.IdentifierType type = Request.IdentifierType.valueOf(String.format("BY_%s", by.toUpperCase()));
        System.out.printf("Enter %s: ", by);
        String identifier = scanner.nextLine();
        return new Request.Identifier(type, identifier);
    }

    private synchronized void sendRequest(Request request, File file) {
        try {
            int id = nextId();
            request.setId(id);
            out.writeUTF(request.toString());
            requests.put(id, request);
            if (file != null) {
                out.writeInt((int) file.length());
                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))
                ){
                    logger.info(String.format("Sending data: %d bytes", ((int) file.length())));
                    byte[] buffer = new byte[bis.available()];
                    while (bis.read(buffer, 0, bis.available()) > 0) {
                        out.write(buffer, 0, bis.available());
                    }
                } catch (IOException e) {
                    logger.warning("Error while writing data to output stream");
                }
            }
            logger.info("Sending done for request: " + request);
        } catch (IOException e) {
            System.err.println("I/O Exception: " + e.getMessage());
        }
    }

    private void processResponses() {
        while (true) {
            //TODO: Read responses from InputStream and output user feedback
        }
    }

    private void exit() {
        executor.shutdown();
        try {
            out.writeUTF(new Request(RequestType.EXIT).toString());
            in.close();
            out.close();
        } catch (IOException e) {
            System.err.println("I/O Exception: " + e.getMessage());
        }
        System.exit(0);
    }

    private synchronized int nextId()
    {
        return requestIdCounter++;
    }
}

