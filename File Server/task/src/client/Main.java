package client;

import server.Request;
import server.Response;
import server.Server;

import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;


public class Main {
    static Logger logger = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws InterruptedException {
        TimeUnit.SECONDS.sleep(1);
        Client client = new Client(Server.ADDRESS, Server.PORT);
        Scanner scanner = new Scanner(System.in);
        Request.Type requestType = null;
        while (requestType == null) {
            System.out.println("Enter action (1 - get a file, 2 - create a file, 3 - delete a file): ");
            String action = scanner.nextLine();
            requestType = switch (action) {
                case "1" -> Request.Type.GET;
                case "2" -> Request.Type.PUT;
                case "3" -> Request.Type.DELETE;
                case "exit" -> Request.Type.EXIT;
                default -> null;
            };
        }
        if (requestType == Request.Type.EXIT) { exit(client); }
        System.out.println("Enter filename: ");
        String filename = scanner.nextLine();
        String data = "";
        if (requestType == Request.Type.PUT) {
            System.out.println("Enter file content: ");
            data = scanner.nextLine();
        }
        Request request = new Request(requestType, filename, data);
        Response response = client.sendRequest(request);
        System.out.println("The request was sent.");
        System.out.println(response.body());
    }

    private static void exit(Client client) {
        client.sendRequest(new Request(Request.Type.EXIT, "", ""));
        System.exit(0);
    }
}
