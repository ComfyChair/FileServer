package server;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static java.net.HttpURLConnection.*;


public class Server {
    public static final String ADDRESS = "127.0.0.1";
    public static final int PORT = 23456;
    private static final Path path = Path.of(System.getProperty("user.dir"),
            "src", "server", "data");

    public Server() {
        if (!path.toFile().exists()) {
            path.toFile().mkdirs();
        }
        try (ServerSocket serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName(ADDRESS))) {
            System.out.println("Server started!");
            while (true) {
                try (Socket socket = serverSocket.accept();
                     DataInputStream input = new DataInputStream(socket.getInputStream());
                     DataOutputStream output = new DataOutputStream(socket.getOutputStream())
                ) {
                    RequestHeader request = RequestHeader.parse(input.readUTF());
                    if (request.type() != RequestHeader.Type.EXIT){
                        ResponseHeader response = switch (request.type()) {
                            case GET -> getFile(request.name());
                            case DELETE -> deleteFile(request.name());
                            case PUT -> putFile(request.name(), request.data());
                            case EXIT -> null;
                        };
                        //logger.info("Sending response: " + response);
                        output.writeUTF(response.toString());
                    } else {
                        System.exit(0);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("IO error: " + e.getMessage());
        }
    }

    public ResponseHeader putFile(String fileName, String data) {
        File file = path.resolve(fileName).toFile();
        ResponseHeader response;
        if (file.exists()) {
            response = new ResponseHeader(HTTP_FORBIDDEN, "The response says that creating the file was forbidden!");
        } else {
            try (FileWriter out = new FileWriter(file)){
                out.write(data);
                response = new ResponseHeader(HTTP_OK, "The response says that the file was created!");
            } catch (IOException e) {
                System.err.println("IO error: " + e.getMessage());
                response = new ResponseHeader(HTTP_INTERNAL_ERROR, "The response says that there was an internal error.");
            }
        }
        return response;
    }

    public ResponseHeader getFile(String fileName) {
        ResponseHeader response;
        File file = path.resolve(fileName).toFile();
        if (!file.exists()) {
            response = new ResponseHeader(HTTP_NOT_FOUND, "The response says that the file was not found!");
        } else {
            try (BufferedReader fileReader = new BufferedReader(new FileReader(file))){
                String content = fileReader.lines().collect(Collectors.joining());
                response = new ResponseHeader(HTTP_OK, String.format("The content of the file is: %s", content));
            } catch (IOException e) {
                response = new ResponseHeader(HTTP_INTERNAL_ERROR, "The response says that there was an internal error.");
            }
        }
        return response;
    }

    public ResponseHeader deleteFile(String fileName) {
        ResponseHeader response;
        File file = path.resolve(fileName).toFile();
        if (!file.exists()) {
            response = new ResponseHeader(HTTP_NOT_FOUND, "The response says that the file was not found!");
        } else {
            boolean isDeleted = file.delete();
            if (isDeleted) {
                response = new ResponseHeader(HTTP_OK, "The response says that the file was successfully deleted!");
            } else {
                response = new ResponseHeader(HTTP_INTERNAL_ERROR, "The response says that there was an internal error.");
            }
        }
        return response;
    }
}
