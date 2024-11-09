package server;

import java.util.Scanner;


public class Main {
    enum Command {ADD, GET, DELETE, EXIT}

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        Server server = new Server();
        while (true) {
            String[] command = scanner.nextLine().split(" ");
            try {
                Command commandEnum = Command.valueOf(command[0].toUpperCase());
                switch (commandEnum) {
                    case ADD -> addFile(command, server);
                    case GET -> getFile(command, server);
                    case DELETE -> deleteFile(command, server);
                    case EXIT -> System.exit(0);
                }
            } catch (IllegalArgumentException e) {
                System.out.println("Unknown command: " + command[0]);
            }

        }
    }

    private static void getFile(String[] command, Server server) {
        try {
            String feedback = server.getFile(command[1]);
            System.out.println(feedback);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.printf("Command %s does not specify the file\n", command[0]);
        }
    }

    private static void deleteFile(String[] command, Server server) {
        try {
            String feedback = server.deleteFile(command[1]);
            System.out.println(feedback);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.printf("Command %s does not specify the file\n", command[0]);
        }
    }

    private static void addFile(String[] command, Server server) {
        try {
            String feedback = server.addFile(command[1]);
            System.out.println(feedback);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.printf("Command %s does not specify the file\n", command[0]);
        }
    }
}