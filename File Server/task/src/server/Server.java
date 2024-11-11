package server;

import server.Request.IdentifierType;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;

import static java.net.HttpURLConnection.*;
import static server.Main.logger;


public class Server {
    public static final String ADDRESS = "127.0.0.1";
    public static final int PORT = 23456;
    private static final Path storagePath = Path.of(System.getProperty("user.dir"),
            "src", "server", "data");
    private Storage fileStorage;

    public Server() {
        initStorage();
        openConnection();
    }

    private void openConnection() {
        System.out.println("Server started!");
        while(true) {
            try (ServerSocket serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName(ADDRESS))) {
                while (true) {
                    try (Socket socket = serverSocket.accept();
                         DataInputStream input = new DataInputStream(socket.getInputStream());
                         DataOutputStream output = new DataOutputStream(socket.getOutputStream())
                    ) {
                        Request request = Request.parse(input.readUTF());
                        switch (request.getRequestType()) {
                            case GET -> getFile(output, request);
                            case DELETE -> deleteFile(output, request);
                            case PUT -> putFile(input, output, request);
                            case EXIT -> shutdown();
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Client connection was closed");
            }
        }
    }

    private void initStorage() {
        if (!storagePath.toFile().exists()) {
            storagePath.toFile().mkdirs();
        }
        fileStorage = Storage.getInstance();
    }

    private String getFileName(IdentifierType identifierType, String identifier) {
        return switch (identifierType) {
            case BY_NAME -> identifier;
            case BY_ID -> {
                try {
                    if (fileStorage.files.containsKey(Integer.parseInt(identifier))) {
                        yield fileStorage.files.get(Integer.parseInt(identifier));
                    } else {
                        logger.warning("No such file id: " + identifier);
                        yield "";
                    }
                } catch (NumberFormatException e) {
                    logger.warning("Not a valid BY_ID identifier: " + identifier);
                    yield "";
                }
            }
        };
    }

    public void putFile(DataInputStream fromClient, DataOutputStream toClient, Request request) {
        String fileName = request.getFileIdentifier();
        File file = storagePath.resolve(fileName).toFile();
        int requestId = request.getRequestId();
        logger.info("Put request: " + request);
        Response response;
        if (file.exists()) {
            response = new Response(requestId, HTTP_FORBIDDEN, "");
        } else {
            response = saveFile(fromClient, file, requestId);
        }
        sendResponse(toClient, requestId, response);
    }

    public void getFile(DataOutputStream toClient, Request request) {
        String fileName = getFileName(request.getIdentifierType(), request.getFileIdentifier());
        File file = storagePath.resolve(fileName).toFile();
        int requestId = request.getRequestId();
        Response response;
        if (!file.exists() || !file.isFile()) {
            response = new Response(requestId, HTTP_NOT_FOUND, "");
        } else {
            response = sendFile(toClient, file, requestId);
        }
        sendResponse(toClient, requestId, response);
    }

    public void deleteFile(DataOutputStream toClient, Request request) {
        String fileName = getFileName(request.getIdentifierType(), request.getFileIdentifier());
        File file = storagePath.resolve(fileName).toFile();
        int requestId = request.getRequestId();
        Response response;
        if (!file.exists()) {
            response = new Response(requestId, HTTP_NOT_FOUND, "");
        } else {
            boolean isDeleted = file.delete();
            if (isDeleted) {
                response = new Response(requestId, HTTP_OK, "");
            } else {
                response = new Response(requestId, HTTP_INTERNAL_ERROR, "");
            }
        }
        sendResponse(toClient, requestId, response);
    }

    private static void sendResponse(DataOutputStream toClient, int requestId, Response response) {
        try {
            toClient.writeUTF(response.toString());
        } catch (IOException e) {
            logger.warning("Could not send response for request(id=" + requestId +")");
        }
    }

    private Response saveFile(DataInputStream fromClient, File file, int requestId) {
        Response response;
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file))){
            int fileId = fileStorage.nextFileId();
            int fileLength = fromClient.readInt();
            byte[] buffer = new byte[fileLength];
            fromClient.readFully(buffer, 0, fileLength);
            bos.write(buffer, 0, fileLength);
            fileStorage.files.put(fileId, file.getName());
            response = new Response(requestId, HTTP_OK, String.valueOf(fileId));
        } catch (IOException e) {
            logger.warning("IO error: " + e.getMessage());
            response = new Response(requestId, HTTP_INTERNAL_ERROR, "");
        }
        return response;
    }

    private Response sendFile(DataOutputStream toClient, File file, int requestId) {
        Response response;
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))){
            logger.info(String.format("Delivering data: %d bytes", ((int) file.length())));
            byte[] buffer = new byte[bis.available()];
            while (bis.read(buffer, 0, bis.available()) > 0) {
                toClient.write(buffer, 0, bis.available());
            }
            response = new Response(requestId, HTTP_OK, "");
        } catch (IOException e) {
            response = new Response(requestId, HTTP_INTERNAL_ERROR, "");
        }
        return response;
    }

    private void shutdown(){
        fileStorage.saveStorage();
        System.exit(0);
    }
}
