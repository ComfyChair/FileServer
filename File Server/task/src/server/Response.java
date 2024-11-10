package server;


public record Response(int code, String body) {
    public static Response builder(String responseString){
        String[] split = responseString.split(" ", 2);
        return new Response(Integer.parseInt(split[0]), split[1]);
    }
    @Override
    public String toString() {
        return String.format("%d %s", code, body);
    }
}
