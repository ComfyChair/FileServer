package server;


import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.*;

import static java.net.HttpURLConnection.*;


public class Server {
    public static final String ADDRESS = "127.0.0.1";
    public static final int PORT = 23456;
    private static Storage fileStorage;
    private final ExecutorService threadPool;
    private DataInputStream fromClient;
    private static DataOutputStream toClient;
    static final Logger logger = Logger.getLogger(Server.class.getName());
    static {
        logger.setLevel(Level.ALL);
    }

    public static void main(String[] args) {
        Server server = new Server();
        server.start();
    }

    public Server() {
        threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    void start() {
        initStorage();
        System.out.println("Server started!");
        try (ServerSocket serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName(ADDRESS))) {
            boolean keepRunning = true;
            while (keepRunning) {
                try (Socket socket = serverSocket.accept()) {
                    logger.info("Connection accepted from server");
                    keepRunning = connected(socket, keepRunning);
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

    private boolean connected(Socket socket, boolean running) throws IOException {
        fromClient = new DataInputStream(socket.getInputStream());
        toClient = new DataOutputStream(socket.getOutputStream());
        while (running && !socket.isClosed()) {
            logger.info("Serving waiting for request in thread " + Thread.currentThread().getName());
            String rawRequest;
            do {
                rawRequest = fromClient.readUTF();
            } while (rawRequest.isEmpty());
            logger.info("Server received: " + rawRequest);
            try {
                Request request = Request.parse(rawRequest);
                logger.info("Server received request: " + request + ", type " + request.getRequestType());
                switch (request.getRequestType()) {
                    case GET -> actionGet(request);
                    case DELETE -> actionDelete(request);
                    case PUT -> actionPut(request);
                    case EXIT -> running = false;
                }
            } catch (IllegalArgumentException e) {
                logger.info("Invalid request: " + rawRequest);
            }
        }
        return running;
    }

    private void initStorage() {
        fileStorage = Storage.getInstance();
        logger.info("Storage initialized: " + fileStorage.showIndex());
    }

    public void actionPut(Request request) throws IOException {
        logger.info("Put request: " + request);
        String fileName = request.getFileIdentifier().value();
        int fileLength = fromClient.readInt();
        byte[] buffer = new byte[fileLength];
        fromClient.readFully(buffer, 0, fileLength);
        threadPool.submit(createSaveRunnable(fileName, fileLength, buffer));
    }

    public void actionGet(Request request) {
        logger.fine("Delegating get request: " + request);
        threadPool.submit(createGetRunnable(request.getFileIdentifier()));
    }

    public void actionDelete(Request request) {
        logger.fine("Delegating delete request: " + request);
        threadPool.submit(createDeleteRunnable(request.getFileIdentifier()));

    }

    private Runnable createGetRunnable(FileIdentifier identifier) {
        return () -> {
            Server.logger.fine("Get request in " + Thread.currentThread().getName());
            File file = Storage.getInstance().getFile(identifier);
            Server.logger.fine("Found file: " + file.getName());
            if (!file.exists() || !file.isFile()) {
                Server.sendResponse(new Response(HTTP_NOT_FOUND, ""), null);
            } else {
                Server.sendResponse(new Response(HTTP_OK, ""), file);
            }
        };
    }

    private Runnable createDeleteRunnable(FileIdentifier identifier) {
        return () -> {
            Server.logger.info("Delete request in " + Thread.currentThread().getName());
            boolean wasDeleted = Storage.getInstance().deleteFile(identifier);
            Response response = wasDeleted ?
                    new Response(HTTP_OK, "") : new Response(HTTP_NOT_FOUND, "");
            Server.sendResponse(response, null);
        };
    }

    private Runnable createSaveRunnable(String fileName, int length, byte[] contents) {
        return () -> {
            Server.logger.info("Put request in " + Thread.currentThread().getName());
            int fileId = Storage.getInstance().saveFile(fileName, length, contents);
            Response response;
            if (fileId > -1) {
                response = new Response(HTTP_OK, String.valueOf(fileId));
            } else {
                response = new Response(HTTP_FORBIDDEN, "");
            }
            Server.sendResponse(response, null);
        };
    }

    static void sendResponse(Response response, File attachedFile) {
        logger.info("Sending response in thread " + Thread.currentThread().getName());
        try {
            toClient.writeUTF(response.toString());
            if (attachedFile != null) {
                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(attachedFile))){
                    int fileLength = (int) attachedFile.length();
                    toClient.writeInt(fileLength);
                    bis.transferTo(toClient);
                    logger.fine(String.format("File sent: %d bytes", fileLength));
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
        logger.info("Server shutting down");
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
        logger.info("Server shut down.");
        System.exit(0);
    }
}
