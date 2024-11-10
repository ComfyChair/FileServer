package client;

import server.Server;

import java.util.Scanner;


public class Main {

    public static void main(String[] args) {
        Client client = new Client(Server.ADDRESS, Server.PORT);
        String request = "Give me everything you have!";
        client.sendRequest(request);
        System.out.printf("Sent: %s\n", request);
        String response = client.getResponse();
        System.out.printf("Received: %s\n", response);
    }
}
