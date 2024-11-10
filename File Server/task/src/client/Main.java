package client;

import server.Server;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;


public class Main {
    static Logger logger = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws InterruptedException {
        TimeUnit.SECONDS.sleep(1);
        Client client = new Client(Server.ADDRESS, Server.PORT);
        client.startCLI();
        }
}
