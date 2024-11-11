package server;


import java.util.logging.Logger;

public class Main {
    static Logger logger = Logger.getLogger("Server");

    public static void main(String[] args) {
        Server server = new Server();
        server.start();
    }

}