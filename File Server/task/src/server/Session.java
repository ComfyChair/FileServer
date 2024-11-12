package server;

import java.io.*;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.logging.Logger;

import static java.net.HttpURLConnection.*;

public class Session {
    static final Logger logger = Logger.getLogger(Session.class.getName());
    private final ExecutorService threadPool;
    private final Socket socket;
    private final DataInputStream fromClient;
    private final DataOutputStream toClient;
    private boolean exitServer;
    private final Set<Future<Response>> pendingResponses = new HashSet<>();

    public Session(Socket socket) throws IOException {
        this.socket = socket;
        threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        fromClient = new DataInputStream(socket.getInputStream());
        toClient = new DataOutputStream(socket.getOutputStream());
        exitServer = false;
    }

    public boolean start() {
        threadPool.submit(this::futureCollector);

        logger.info("Session waiting for request in thread " + Thread.currentThread().getName());
        String rawRequest;
        try {
            rawRequest = fromClient.readUTF();
            logger.info("Received request: " + rawRequest);
            try {
                Request request = Request.parse(rawRequest);
                switch (request.getRequestType()) {
                    case GET -> actionGet(request);
                    case DELETE -> actionDelete(request);
                    case PUT -> actionPut(request);
                    case EXIT -> exitServer = true;
                }
            } catch (IllegalArgumentException e) {
                logger.info("Invalid request: " + rawRequest);
            }
        } catch (IOException e) {
            logger.info("Lost connection to client: " + e.getCause());
            return exitServer;
        }
        terminateThreads();
        return exitServer;
    }

    public void actionPut(Request request) throws IOException {
        String fileName = request.getFileIdentifier().value();
        int fileLength = fromClient.readInt();
        byte[] contents = new byte[fileLength];
        fromClient.readFully(contents, 0, fileLength);
        Future<Response> futureResponse = threadPool.submit(() -> {
            Server.logger.info("Put request in " + Thread.currentThread().getName());
            int fileId = Storage.getInstance().saveFile(fileName, fileLength, contents);
            if (fileId > -1) {
                return new Response(HTTP_OK, String.valueOf(fileId));
            } else {
                return new Response(HTTP_FORBIDDEN, "");
            }
        });
        pendingResponses.add(futureResponse);
    }

    public void actionGet(Request request) {
        Future<Response> futureResponse = threadPool.submit(() -> {
            Server.logger.fine("Get request in " + Thread.currentThread().getName());
            File file = Storage.getInstance().getFile(request.getFileIdentifier());
            Server.logger.fine("Found file: " + file.getName());
            if (!file.exists() || !file.isFile()) {
                return new Response(HTTP_NOT_FOUND, "");
            } else {
                return new Response(HTTP_OK, "", file);
            }
        });
        pendingResponses.add(futureResponse);
    }

    public void actionDelete(Request request) {
        Server.logger.info("Delete request in " + Thread.currentThread().getName());
        boolean wasDeleted = Storage.getInstance().deleteFile(request.getFileIdentifier());
        Response response = wasDeleted ?
                new Response(HTTP_OK, "") : new Response(HTTP_NOT_FOUND, "");
        sendResponse(response);
    }


    private void futureCollector() {
        while (!exitServer && !threadPool.isShutdown()) {
            try {
                for (Future<Response> future : pendingResponses) {
                    if (future.isDone()) {
                        sendResponse(future.get());
                        pendingResponses.remove(future);
                    }
                }
                TimeUnit.MILLISECONDS.sleep(300);
            }
            catch (ExecutionException | InterruptedException e) {
                logger.warning(e.getCause().getMessage());
            }
        }
    }

    private void sendResponse(Response response) {
        logger.info("Sending response in thread " + Thread.currentThread().getName());
        synchronized (threadPool) {
            try {
                toClient.writeUTF(response.toString());
                if (response.getFile() != null) {
                    try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(response.getFile()))) {
                        int fileLength = (int) response.getFile().length();
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
    }

    private void terminateThreads() {
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(2, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
                if (!threadPool.awaitTermination(2, TimeUnit.SECONDS)) {
                    logger.warning("Pool did not terminate");
                }
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
