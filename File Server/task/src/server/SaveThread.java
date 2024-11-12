package server;

import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_OK;

public class SaveThread implements Runnable {
    String fileName;
    int length;
    byte[] contents;
    SaveThread(String fileName, int length, byte[] contents) {
        this.fileName = fileName;
        this.length = length;
        this.contents = contents;
    }

    @Override
    public void run() {
        Server.logger.info("Put request in " + Thread.currentThread().getName());
        int fileId = Storage.getInstance().saveFile(fileName, length, contents);
        Server.logger.info("File saved with id: " + fileId);
        Response response;
        if (fileId > -1) {
            response = new Response(HTTP_OK, String.valueOf(fileId));
        } else {
            response = new Response(HTTP_FORBIDDEN, "");
        }
        Server.sendResponse(response, null);
    }
}
