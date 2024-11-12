package server;


import java.io.File;

public final class Response {
    private final int code;
    private final String info;
    private File file = null;

    public Response(int code, String info) {
        this.code = code;
        this.info = info;
    }

    public Response(int code, String info, File file) {
        this.code = code;
        this.info = info;
        this.file = file;
    }

    public static Response parse(String responseString){
        String[] split = responseString.split(" ", 2);
        int code = Integer.parseInt(split[0]);
        String info = split.length == 2 ? split[1] : "";
        return new Response(code, info);
    }

    public int getCode() {
        return code;
    }

    public String getInfo() {
        return info;
    }

    public File getFile() {
        return file;
    }

    @Override
    public String toString() {
        return file == null ? String.format("%d %s", code, info)
                : String.format("%d %s %s", code, info, file.getName());
    }
}
