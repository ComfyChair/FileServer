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
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.logging.*;

import static java.net.HttpURLConnection.*;

/** Client for connecting to the FileServer */
public class Client {
    private static final String EXPLAIN_RESPONSE = "The response says that";
    private static final Path dataPath = Path.of(System.getProperty("user.dir"),
            "src", "client", "data");
    private final Scanner scanner;
    private DataInputStream serverIn;
    private DataOutputStream serverOut;
    private static final Logger logger = Logger.getLogger(Client.class.getName());

    /** Entry point
     * sleeps for half a second before connecting to allow for server startup during tests  */
    public static void main(String[] args) throws InterruptedException {
        Client client = new Client();
        TimeUnit.MILLISECONDS.sleep(500);
        client.connect();
    }

    /** Constructor
     * creates data directory, if necessary, and checks for writing privileges */
    public Client() {
        if (!dataPath.toFile().exists()) {
            boolean created = dataPath.toFile().mkdirs();
            if (!created ) {
                logger.severe("Data directory could not be created in " + dataPath.toAbsolutePath());
                System.exit(1);
            }
        }
        if (!dataPath.toFile().canWrite()) {
            logger.severe("No write access for data directory " + dataPath.toAbsolutePath());
        }
        scanner = new Scanner(System.in);
    }

    /** Establishes a connection the FileServer,
     * prompts the user for a single request and
     * initiates sending the request.
     * Waits for the server to close its side of the connection after sending an EXIT request,
     * or just closes the client connection for after sending any other request. */
    public void connect() {
        try (Socket socket = new Socket(InetAddress.getByName(Server.ADDRESS), Server.PORT)) {
            serverIn = new DataInputStream(socket.getInputStream());
            serverOut = new DataOutputStream(socket.getOutputStream());
            String action = "";
            System.out.println("Enter action (1 - get a file, 2 - save a file, 3 - delete a file): ");
            action = scanner.nextLine();
            switch (action) {
                case "1" -> sendGetRequest();
                case "2" -> sendPutRequest();
                case "3" -> sendDeleteRequest();
                case "exit" -> {
                    serverOut.writeUTF(new Request(RequestType.EXIT).toString());
                    if (serverIn.read() == -1) { socket.close(); }
                }
            }
        } catch (IOException e) {
            logger.info("Connection closed.\nCause: " + e.getCause().getMessage());
        }
        scanner.close();
        logger.info("Exit: Client");
        System.exit(0);
    }

    /** PUT request processing
     * Prompts the user for source and destination file names and initiates sending the request */
    private void sendPutRequest() throws IOException {
        System.out.println("Enter name of the file: ");
        String srcName = scanner.nextLine();
        File file = dataPath.resolve(srcName).toFile();
        if (!file.exists() || !file.isFile()) {
            System.err.println("File does not exist: " + srcName);
        } else {
            System.out.println("Enter name of the file to be saved on server: ");
            String destName = scanner.nextLine();
            destName = destName.isEmpty() ? srcName : destName;
            Request request = new Request(RequestType.PUT, new FileIdentifier(FileIdentifier.Type.BY_NAME, destName));
            sendRequest(request, file);
        }
    }

    /** GET request processing
     * gathers information regarding the file and initiates sending the request */
    private void sendGetRequest() throws IOException {
        FileIdentifier identifier = readIdentifier();
        Request request = new Request(RequestType.GET, identifier);
        sendRequest(request, null);
    }

    /** DELETE request processing
     * gathers information regarding the file and initiates sending the request */
    private void sendDeleteRequest() throws IOException {
        FileIdentifier identifier = readIdentifier();
        Request request = new Request(RequestType.DELETE, identifier);
        sendRequest(request, null);
    }

    /** Prompts the user for the information necessary to identify a file;
     * used for GET and DELETE requests
     * @return id or name of the file wrapped in a FileIdentifier object * */
    private FileIdentifier readIdentifier() {
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

    /** Send a Request to the FileServer
     * @param request the request to be sent
     * @param file the file that should be sent with the request, or null otherwise */
    private void sendRequest(Request request, File file) throws IOException {
        logger.info("Sending request");
        try {
            serverOut.writeUTF(request.toString());
            if (file != null) {
                int fileLength = (int) file.length();
                serverOut.writeInt(fileLength);
                logger.info("Sending file length: " + file.length());
                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
                    bis.transferTo(serverOut);
                } catch (IOException e) {
                    logger.warning("Error while writing data to output stream");
                }
            }
            System.out.println("The request was sent.");
            processResponse(request);
        } catch (IOException e) {
            throw new IOException("I/O Exception while sending request: " + e.getCause().getMessage());
        }
    }

    /** Waits for a Response, prints information on the content, and acts upon it if applicable
     * @param request the request to which the response belongs */
    private void processResponse(Request request) throws IOException {
        try {
            String rawResponse;
            do {
                rawResponse = serverIn.readUTF();
            } while (rawResponse.isEmpty());
            Response response = Response.parse(rawResponse);
            logger.info("Received response: " + response);
            switch (request.getRequestType()){
                case GET -> {
                    switch (response.getCode()){
                        case HTTP_OK -> saveFile();
                        case HTTP_NOT_FOUND -> System.out.printf("%s this file is not found!%n", EXPLAIN_RESPONSE);
                        default -> System.out.println("Invalid response");
                    }
                }
                case PUT -> {
                    switch (response.getCode()){
                        case HTTP_OK -> System.out.printf("Response says that file is saved! ID = %s%n", response.getInfo());
                        case HTTP_FORBIDDEN -> System.out.printf("%s file is not saved!%n", EXPLAIN_RESPONSE);
                        default -> System.out.println("Invalid response");
                    }
                }
                case DELETE -> {
                    switch (response.getCode()) {
                        case HTTP_OK -> System.out.printf("%s this file was deleted successfully!%n", EXPLAIN_RESPONSE);
                        case HTTP_NOT_FOUND -> System.out.printf("%s this file is not found!%n", EXPLAIN_RESPONSE);
                        default -> System.out.println("Invalid response");
                    }
                }
                default -> System.out.println("Unexpected response for request type " + request.getRequestType());
            }
        } catch (IOException e) {
            throw new IOException("Lost connection while reading response");
        }
    }

    /** Saves a file to cwd/src/client/data as a result of a successful GET request */
    private void saveFile() {
        System.out.println("The file was downloaded! Specify a name for it: ");
        String fileName = scanner.nextLine();
        File file = dataPath.resolve(fileName).toFile();
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file))){
            int fileLength = serverIn.readInt();
            logger.info("Reading from DataStream: " + fileLength + " bytes");
            byte[] buffer = new byte[fileLength];
            serverIn.readFully(buffer, 0, fileLength);
            logger.info("Writing to output stream");
            bos.write(buffer, 0, fileLength);
            logger.info("Writing done for " + file.getName());
        } catch (IOException e) {
            logger.warning("IO error: " + e.getMessage());
        }
        System.out.println("File saved on the hard drive!");
    }
}

