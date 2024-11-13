package server;

import java.io.*;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.logging.Logger;

import static java.net.HttpURLConnection.*;

/** Class for managing a single Client-Server session */
public class Session {
    static final Logger logger = Logger.getLogger(Session.class.getName());
    private final ExecutorService threadPool;
    private final Socket socket;
    private final DataInputStream fromClient;
    private final DataOutputStream toClient;
    private boolean exitServer;
    private final Set<Future<Response>> pendingResponses = new HashSet<>();

    /** Session constructor
     * @param socket The socket by which the client is connected
     * @throws IOException if the client has already disconnected and data streams are therefore closed */
    public Session(Socket socket) throws IOException {
        this.socket = socket;
        threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        fromClient = new DataInputStream(socket.getInputStream());
        toClient = new DataOutputStream(socket.getOutputStream());
        exitServer = false;
    }

    /** Starts response handling thread, parses client requests and handles client disconnects
     * @return true if client requested server should be shut down, false otherwise */
    public boolean startLifecycle() {
        threadPool.submit(this::responseHandler);
        String rawRequest;
        while (!exitServer && !socket.isClosed()) {
            try {
                logger.fine("Session waiting for request in thread " + Thread.currentThread().getName());
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
                if (!exitServer && fromClient.read() == -1) {
                    logger.info("Client disconnected. Closing socket...");
                    socket.close();
                }
            } catch (IOException e) {
                logger.info("Lost connection to client.");
                return exitServer;
            }
        }
        terminateThreads();
        return exitServer;
    }

    /** PUT request action
     * reads file from stream and initiates saving to Storage in a separate thread */
    public void actionPut(Request request) throws IOException {
        String fileName = request.getFileIdentifier().value();
        int fileLength = fromClient.readInt();
        byte[] contents = new byte[fileLength];
        fromClient.readFully(contents, 0, fileLength);
        Future<Response> futureResponse = threadPool.submit(() -> {
            Server.logger.fine("Put request in " + Thread.currentThread().getName());
            int fileId = Storage.getInstance().saveFile(fileName, fileLength, contents);
            if (fileId > -1) {
                return new Response(HTTP_OK, String.valueOf(fileId));
            } else {
                return new Response(HTTP_FORBIDDEN, "");
            }
        });
        pendingResponses.add(futureResponse);
    }

    /** GET request action
     * initiates file query from Storage in a separate thread */
    public void actionGet(Request request) {
        Future<Response> futureResponse = threadPool.submit(() -> {
            Server.logger.fine("Get request in " + Thread.currentThread().getName());
            File file = Storage.getInstance().getFile(request.getFileIdentifier());
            if (file == null) {
                return new Response(HTTP_NOT_FOUND, "");
            } else {
                Server.logger.fine("Found file: " + file.getName());
                return new Response(HTTP_OK, "", file);
            }
        });
        pendingResponses.add(futureResponse);
    }

    /** DELETE request action
     * initiates deletion from Storage in a separate thread */
    public void actionDelete(Request request) {
        Future<Response> futureResponse = threadPool.submit(() -> {
            Server.logger.fine("Delete request in " + Thread.currentThread().getName());
            boolean wasDeleted = Storage.getInstance().deleteFile(request.getFileIdentifier());
            return wasDeleted ? new Response(HTTP_OK, "") : new Response(HTTP_NOT_FOUND, "");
        });
        pendingResponses.add(futureResponse);
    }

    /** Manages asynchronous response generation
     *  by checking Future Responses for completion and calling sendResponse with them */
    private void responseHandler() {
        while (!threadPool.isShutdown()) {
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

    /** Sends a response to the client, including a requested file if applicable
     * @param response the response that should be returned */
    private void sendResponse(Response response) {
        logger.fine("Sending response in thread " + Thread.currentThread().getName());
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
                        logger.warning("Server couldn't send file");
                    }
                }
                logger.info("Response sent");
            } catch (IOException e) {
                logger.warning("Could not send response for request(" + response +")");
            }
        }
    }

    /** Tries to terminate any running threads at the end of the session */
    private void terminateThreads() {
        logger.info("Shutting down thread pool");
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
        }
    }
}
