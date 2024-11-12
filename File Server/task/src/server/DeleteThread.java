package server;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;

public class DeleteThread implements Runnable {
    FileIdentifier identifier;
    public DeleteThread(FileIdentifier fileIdentifier) {
        this.identifier = fileIdentifier;
    }

    @Override
    public void run() {
        Server.logger.fine("Delete request in " + Thread.currentThread().getName());
        boolean wasDeleted = Storage.getInstance().deleteFile(identifier);
        Server.logger.info("DELETE request successful: " + wasDeleted);
        Response response = wasDeleted ?
                new Response(HTTP_OK, "") : new Response(HTTP_NOT_FOUND, "");
        Server.sendResponse(response, null);
    }
}
