package server;


public final class Response {
    private final int code;
    private final String info;

    public Response(int code, String info) {
        this.code = code;
        this.info = info;
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

    @Override
    public String toString() {
        return String.format("%d %s", code, info);
    }
}
