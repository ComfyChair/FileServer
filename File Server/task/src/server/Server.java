package server;


import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.*;


public class Server {
    public static final String ADDRESS = "127.0.0.1";
    public static final int PORT = 23456;
    private static Storage fileStorage;
    static final Logger logger = Logger.getLogger(Server.class.getName());
    static {
        logger.setLevel(Level.ALL);
    }

    public static void main(String[] args) {
        Server server = new Server();
        server.start();
    }

    void start() {
        initStorage();
        System.out.println("Server started!");
        try (ServerSocket serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName(ADDRESS))) {
            boolean exitServer = false;
            while (!exitServer) {
                try (Socket socket = serverSocket.accept()) {
                    Session session = new Session(socket);
                    exitServer = session.start();
                    logger.info("Session disconnected!");
                }
                catch (IOException e) {
                    logger.info("Client connection was closed");
                }
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

    private void shutdown(){
        logger.fine("Server shutting down, saving index");
        fileStorage.saveIndex();
        logger.info("Storage index saved, server exiting.");
        System.exit(0);
    }
}
