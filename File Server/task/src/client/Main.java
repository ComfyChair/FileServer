package client;

import java.util.concurrent.TimeUnit;


public class Main {

    public static void main(String[] args) throws InterruptedException {
        TimeUnit.SECONDS.sleep(1);
        Client client = new Client();
        client.connect();
        }
}
