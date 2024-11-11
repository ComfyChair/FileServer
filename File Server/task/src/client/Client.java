package client;

import server.FileIdentifier;
import server.Request;
import server.Request.RequestType;
import server.Response;
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
import java.util.logging.Logger;

import static java.net.HttpURLConnection.*;

public class Client {
    static Logger logger = Logger.getLogger(Client.class.getName());
    private static final String EXPLAIN_RESPONSE = "The response says that";
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
            Request request = new Request(RequestType.PUT, new FileIdentifier(FileIdentifier.Type.BY_NAME, destName));
            executor.submit(() -> sendRequest(request, file));
        }
    }

    private void sendGetRequest(Scanner scanner) {
        FileIdentifier identifier = readIdentifier(scanner);
        Request request = new Request(RequestType.GET, identifier);
        executor.submit(() -> sendRequest(request, null));
    }

    private void sendDeleteRequest(Scanner scanner) {
        FileIdentifier identifier = readIdentifier(scanner);
        Request request = new Request(RequestType.DELETE, identifier);
        executor.submit(() -> sendRequest(request, null));
    }

    private FileIdentifier readIdentifier(Scanner scanner) {
        System.out.println("Do you want to get the file by identifier or by id (1 - name, 2 - id):");
        String by = "";
        while (by.isEmpty()) {
            switch (scanner.nextLine()) {
                case "1" -> by = "name";
                case "2" -> by = "id";
                default -> System.out.println("Invalid input");
            }
        }
        FileIdentifier.Type type = FileIdentifier.Type.valueOf(String.format("BY_%s", by.toUpperCase()));
        System.out.printf("Enter %s: ", by);
        String identifier = scanner.nextLine();
        return new FileIdentifier(type, identifier);
    }

    private synchronized void sendRequest(Request request, File file) {
        logger.info("Sending request");
        try {
            int id = nextId();
            request.setId(id);
            out.writeUTF(request.toString());
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
            System.out.println("The request was sent.");
        } catch (IOException e) {
            System.err.println("I/O Exception on sending request: " + e.getMessage());
        }
    }

    private void processResponses() {
        while (Thread.currentThread().isAlive()) {
            try {
                logger.info(String.format("Thread %s Waiting for response", Thread.currentThread().getName()));
                Response response = Response.parse(in.readUTF());
                logger.info("Received response: " + response);
                Request request = requests.get(response.getRequestId());
                logger.info("   belongs to requestType: " + request.getRequestType());
                switch (request.getRequestType()){
                    case GET -> {
                        switch (response.getCode()){
                            case HTTP_OK -> executor.submit(this::saveFile);
                            case HTTP_NOT_FOUND -> System.out.printf("%s  this file is not found!%n", EXPLAIN_RESPONSE);
                            default -> System.out.println("Invalid response");
                        }
                    }
                    case PUT -> {
                        switch (response.getCode()){
                            case HTTP_OK -> System.out.printf("%s file is saved! ID = %s%n", EXPLAIN_RESPONSE, response.getInfo());
                            case HTTP_FORBIDDEN -> System.out.printf("%s file is not saved!%n", EXPLAIN_RESPONSE);
                            default -> System.out.println("Invalid response");
                        }
                    }
                    case DELETE -> {
                        switch (response.getCode()) {
                            case HTTP_OK -> System.out.printf("%s this file was deleted successfully!%n", EXPLAIN_RESPONSE);
                            case HTTP_NOT_FOUND -> System.out.printf("%s  this file is not found!%n", EXPLAIN_RESPONSE);
                            default -> System.out.println("Invalid response");
                        }
                    }
                    default -> System.out.println("Unexpected response for request type " + request.getRequestType());
                }
            } catch (IOException e) {
                logger.severe("I/O Exception while waiting for response: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    private void saveFile() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("The file was downloaded! Specify a name for it: ");
        String fileName = scanner.nextLine();
        File file = dataPath.resolve(fileName).toFile();
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file))){
            int fileLength = in.readInt();
            logger.info("Reading from DataStream: " + fileLength + " bytes");
            byte[] buffer = new byte[fileLength];
            in.readFully(buffer, 0, fileLength);
            logger.info("Writing to output stream");
            bos.write(buffer, 0, fileLength);
            logger.info("Writing done for " + file.getName());
        } catch (IOException e) {
            logger.warning("IO error: " + e.getMessage());
        }
        System.out.println("File saved on the hard drive!");
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

