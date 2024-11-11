package server;


import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.net.HttpURLConnection.*;
import static server.Main.logger;


public class Server {
    public static final String ADDRESS = "127.0.0.1";
    public static final int PORT = 23456;
    private Storage fileStorage;
    private ExecutorService threadPool;
    private DataInputStream fromClient;
    private DataOutputStream toClient;

    public Server() {
        initStorage();
        threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    void start() {
        System.out.println("Server started!");
        try (ServerSocket serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName(ADDRESS))) {
            boolean running = true;
            while (running) {
                try (Socket socket = serverSocket.accept()) {
                    fromClient = new DataInputStream(socket.getInputStream());
                    toClient = new DataOutputStream(socket.getOutputStream());
                    String rawRequest = fromClient.readUTF();
                    try {
                        Request request = Request.parse(rawRequest);
                        logger.info("Got request: " + request);
                        switch (request.getRequestType()) {
                            case GET -> serveGetRequest(request);
                            case DELETE -> serveDeleteRequest(request);
                            case PUT -> servePutRequest(request);
                            case EXIT -> running = false;
                        }
                    } catch (IllegalArgumentException e) {
                        logger.severe("Invalid request: " + rawRequest);
                    }
                }
                catch (IOException e) {
                    System.err.println("Client connection was closed");
                }
                fromClient.close();
                toClient.close();
            }
        } catch (IOException e) {
            logger.severe("Server couldn't listen on port " + PORT);
        } finally {
            shutdown();
        }
    }

    private void initStorage() {
        fileStorage = Storage.getInstance();
        logger.info("Storage initialized: " + fileStorage.showIndex());
    }

    public void servePutRequest(Request request) {
        logger.info("Put request: " + request);
        String fileName = request.getFileIdentifier().value();
        int fileId = fileStorage.saveFile(fromClient, fileName);
        logger.info("File saved with id: " + fileId);
        int requestId = request.getRequestId();
        Response response;
        if (fileId > -1) {
            response = new Response(requestId, HTTP_OK, String.valueOf(fileId));
        } else {
            response = new Response(requestId, HTTP_FORBIDDEN, "");
        }
        sendResponse(response, null);
    }

    public void serveGetRequest(Request request) {
        logger.info("Processing GET request: ");
        File file = fileStorage.getFile(request.getFileIdentifier());
        int requestId = request.getRequestId();
        if (!file.exists() || !file.isFile()) {
            sendResponse(new Response(requestId, HTTP_NOT_FOUND, ""), null);
        } else {
            sendResponse(new Response(requestId, HTTP_OK, ""), file);
        }
    }

    public void serveDeleteRequest(Request request) {
        logger.info("Processing DELETE request: ");
        int requestId = request.getRequestId();
        boolean wasDeleted = fileStorage.deleteFile(request.getFileIdentifier());
        Response response = wasDeleted ?
                new Response(requestId, HTTP_OK, "") : new Response(requestId, HTTP_NOT_FOUND, "");
        sendResponse(response, null);
    }

    private synchronized void sendResponse(Response response, File attachedFile) {
        logger.info("Sending response");
        try {
            toClient.writeUTF(response.toString());
            if (attachedFile != null) {
                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(attachedFile))){
                    int fileLength = (int) attachedFile.length();
                    logger.info(String.format("Sending file: %d bytes", fileLength));
                    toClient.writeInt(fileLength);
                    bis.transferTo(toClient);
                } catch (IOException e) {
                    logger.severe("Server couldn't send file");
                }
            }
        } catch (IOException e) {
            logger.warning("Could not send response for request(" + response +")");
        }
    }

    private void shutdown(){
        logger.info("Shutting down");
        fileStorage.saveStorage();
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
                if (!threadPool.awaitTermination(60, TimeUnit.SECONDS)) {
                    logger.warning("Pool did not terminate");
                }
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.exit(0);
    }
}
