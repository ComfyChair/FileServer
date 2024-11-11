package client;

import server.Request;
import server.Request.RequestType;
import server.Server;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;


public class Client {
    static Logger logger = Logger.getLogger(Client.class.getName());
    private static final int POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final Path dataPath = Path.of(System.getProperty("user.dir"),
             "src", "client", "data");
    private DataInputStream in;
    private DataOutputStream out;
    private final ExecutorService executor;
    private int requestIdCounter = 0;
    private final Map<Integer, Request> requests = new HashMap<>();

    public Client() {
        if (!dataPath.toFile().exists()) {
            dataPath.toFile().mkdirs();
        }
        executor = Executors.newFixedThreadPool(POOL_SIZE);
    }

    public void connect() {
        try (Socket socket = new Socket(InetAddress.getByName(Server.ADDRESS), Server.PORT)) {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
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
        } catch (IOException e) {
            System.err.println("I/O Exception: " + e.getMessage());
            System.exit(1);
        }
        System.exit(0);
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
        logger.info("Sending request");
        try {
            int id = nextId();
            request.setId(id);
            out.writeUTF(request.toString());
            logger.info("Send request");
            requests.put(id, request);
            if (file != null) {
                int fileLength = (int) file.length();
                out.writeInt(fileLength);
                logger.info("Sending file length: " + file.length());
                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
                    logger.info(String.format("Sending data: %d bytes", fileLength));
                    bis.transferTo(out);
                } catch (IOException e) {
                    logger.warning("Error while writing data to output stream");
                }
            }
            logger.info("Sending done for request: " + request.getRequestId());
        } catch (IOException e) {
            System.err.println("I/O Exception on sending request: " + e.getMessage());
        }
    }

    private void processResponses() {
        while (true) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {

            }
            //TODO: await responses
        }
    }

    private void exit() {
        logger.info("Exiting...");
        executor.shutdown();
        try {
            out.writeUTF(new Request(RequestType.EXIT).toString());
            logger.info("Send exit request");
        } catch (IOException e) {
            System.err.println("I/O Exception: " + e.getMessage());
        }
    }

    private synchronized int nextId()
    {
        return requestIdCounter++;
    }
}

