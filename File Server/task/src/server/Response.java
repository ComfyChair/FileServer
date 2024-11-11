package server;


public final class Response {
    private final int requestId;
    private final int code;
    private final String message;

    public Response(int requestId, int code, String message) {
        this.requestId = requestId;
        this.code = code;
        this.message = message;
    }

    @Override
    public String toString() {
        return String.format("%d %d %s", requestId, code, message);
    }
}
