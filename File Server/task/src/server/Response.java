package server;


import java.io.File;

/** Wrapper class for response code, additional String-encoded info (=file index) and file, if applicable */
public final class Response {
    private final int code;
    private final String info;
    private File file = null;

    /** Constructor for responses without attached files
     * @param code Response code (corresponding to java.net.HttpURLConnection codes)
     * @param info additional info: index of a saved file is response to GET request, empty string otherwise
     */
    public Response(int code, String info) {
        this.code = code;
        this.info = info;
    }

    /** Constructor for responses with attached files
     * @param code Response code (corresponding to java.net.HttpURLConnection codes)
     * @param info additional info: index of a saved file is response to GET request, empty string otherwise
     * @param file the file that is going to be sent back
     */
    public Response(int code, String info, File file) {
        this.code = code;
        this.info = info;
        this.file = file;
    }

    /** Reconstructs a string-encoded response
     * @param responseString the encoded Response
     * @return the reconstructed Response */
    public static Response parse(String responseString){
        String[] split = responseString.split(" ", 2);
        int code = Integer.parseInt(split[0]);
        String info = split.length == 2 ? split[1] : "";
        return new Response(code, info);
    }

    /** Encodes the response as a string for sending or logging */
    @Override
    public String toString() {
        return file == null ? String.format("%d %s", code, info)
                : String.format("%d %s %s", code, info, file.getName());
    }


    /** Returns the response code as int */
    public int getCode() {
        return code;
    }

    /** Returns file index as string or empty string */
    public String getInfo() {
        return info;
    }

    /** Returns the attached File object that is going to be attached */
    public File getFile() {
        return file;
    }
}
