package server;


import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
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
            logger.info("Serving waiting for request");
            String rawRequest = fromClient.readUTF();
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
        logger.info("Reading from DataStream: " + fileLength + " bytes");
        byte[] buffer = new byte[fileLength];
        fromClient.readFully(buffer, 0, fileLength);
        logger.info("Delegating to PutThread with length = " + fileLength);
        Runnable runnable =new SaveThread(fileName, fileLength, buffer);
        threadPool.submit(runnable);
    }

    public void actionGet(Request request) {
        logger.fine("Delegating get request: " + request);
        threadPool.submit(new GetThread(request.getFileIdentifier()));
    }

    public void actionDelete(Request request) {
        logger.fine("Delegating delete request: " + request);
        threadPool.submit(new DeleteThread(request.getFileIdentifier()));
    }

    static synchronized void sendResponse(Response response, File attachedFile) {
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
