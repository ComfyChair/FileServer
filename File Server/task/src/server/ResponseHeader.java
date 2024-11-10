package server;


public record ResponseHeader(int code, String... headers) {
    @Override
    public String toString() {
        return String.format("%d %s", code, String.join(" ", headers));
    }
}
