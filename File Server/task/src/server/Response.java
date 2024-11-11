package server;


public final class Response {
    private final int requestId;
    private final int code;
    private final String info;

    public Response(int requestId, int code, String info) {
        this.requestId = requestId;
        this.code = code;
        this.info = info;
    }

    public static Response parse(String responseString){
        String[] split = responseString.split(" ", 3);
        int requestId = Integer.parseInt(split[0]);
        int code = Integer.parseInt(split[1]);
        String info = split.length == 3 ? split[2] : "";
        return new Response(requestId, code, info);
    }

    public int getRequestId() {
        return requestId;
    }

    public int getCode() {
        return code;
    }

    public String getInfo() {
        return info;
    }

    @Override
    public String toString() {
        return String.format("%d %d %s", requestId, code, info);
    }
}
