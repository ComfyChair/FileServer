package server;

import java.io.File;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;

public class GetThread implements Runnable {
    FileIdentifier identifier;
    GetThread(FileIdentifier identifier){
        this.identifier = identifier;
    }
    @Override
    public void run() {
        Server.logger.fine("Get request in " + Thread.currentThread().getName());
        File file = Storage.getInstance().getFile(identifier);
        Server.logger.fine("Found file: " + file.getName());
        if (!file.exists() || !file.isFile()) {
            Server.sendResponse(new Response(HTTP_NOT_FOUND, ""), null);
        } else {
            Server.sendResponse(new Response(HTTP_OK, ""), file);
        }
    }
}
