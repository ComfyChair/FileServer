package client;

import server.Request;
import server.Response;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

public class Client {
    Socket socket;
    DataInputStream in;
    DataOutputStream out;

    public Client(String address, int port) {
        try {
            this.socket = new Socket(InetAddress.getByName(address), port);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
        } catch (UnknownHostException e) {
            System.err.println("Unknown host: " + address);
        } catch (IOException e) {
            System.err.println("I/O Exception: " + e.getMessage());
        }
    }

    public Response sendRequest(Request request) {
        try {
            out.writeUTF(request.toString());
            if (request.type() != Request.Type.EXIT) {
                return Response.builder(in.readUTF());
            }
        } catch (IOException e) {
            System.err.println("Client I/O Exception on send:\n" + e.getMessage());
        }
        return null;
    }
}
