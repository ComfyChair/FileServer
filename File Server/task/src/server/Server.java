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
    private final ExecutorService threadPool;
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
                    while (running && !socket.isClosed()) {
                        try {
                            Request request = Request.parse(fromClient.readUTF());
                            logger.info("Got request: " + request);
                            switch (request.getRequestType()) {
                                case GET -> actionGet(request);
                                case DELETE -> actionDelete(request);
                                case PUT -> actionPut(request);
                                case EXIT -> running = false;
                            }
                        } catch (IllegalArgumentException e) {
                            logger.severe("Invalid request!");
                        }
                    }
                    logger.info("Socket disconnected!");
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

    public void actionPut(Request request) {
        logger.info("Put request: " + request);
        String fileName = request.getFileIdentifier().value();
        int fileId = fileStorage.saveFile(fromClient, fileName);
        logger.info("File saved with id: " + fileId);
        Response response;
        if (fileId > -1) {
            response = new Response(HTTP_OK, String.valueOf(fileId));
        } else {
            response = new Response(HTTP_FORBIDDEN, "");
        }
        sendResponse(response, null);
    }

    public void actionGet(Request request) {
        File file = fileStorage.getFile(request.getFileIdentifier());
        logger.info("Found file: " + file.getName());
        if (!file.exists() || !file.isFile()) {
            sendResponse(new Response(HTTP_NOT_FOUND, ""), null);
        } else {
            sendResponse(new Response(HTTP_OK, ""), file);
        }
    }

    public void actionDelete(Request request) {
        logger.info("Delete request for " + request.getFileIdentifier().toString());
        boolean wasDeleted = fileStorage.deleteFile(request.getFileIdentifier());
        logger.info("DELETE request successful: " + wasDeleted);
        Response response = wasDeleted ?
                new Response(HTTP_OK, "") : new Response(HTTP_NOT_FOUND, "");
        sendResponse(response, null);
    }

    private synchronized void sendResponse(Response response, File attachedFile) {
        try {
            toClient.writeUTF(response.toString());
            if (attachedFile != null) {
                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(attachedFile))){
                    int fileLength = (int) attachedFile.length();
                    toClient.writeInt(fileLength);
                    bis.transferTo(toClient);
                    logger.info(String.format("File sent: %d bytes", fileLength));
                } catch (IOException e) {
                    logger.severe("Server couldn't send file");
                }
            }
            logger.info("Response sent");
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
