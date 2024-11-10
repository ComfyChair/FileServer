package client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class Client {
    Socket socket;
    DataInputStream in;
    DataOutputStream out;

    public Client(String address, int port) {
        try {
            this.socket = new Socket(InetAddress.getByName(address), port);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            System.out.println("Client started!");
        } catch (UnknownHostException e) {
            System.err.println("Unknown host: " + address);
        } catch (IOException e) {
            System.err.println("I/O Exception: " + e.getMessage());
        }
    }

    public void sendRequest(String request) {
        try {
            out.writeUTF(request);
        } catch (IOException e) {
            System.err.println("Client I/O Exception on send:\n" + e.getMessage());
        }
    }

    public String getResponse() {
        try {
            return in.readUTF();
        } catch (IOException e) {
            System.err.println("Client I/O Exception on receive:\n" + e.getMessage());
        }
        return null;
    }
}
