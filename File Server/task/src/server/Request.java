package server;

import static server.Main.logger;

public record Request (Type type, String name, String data) {

    public static Request parse(String request) {
        String[] parts = request.split(" ", 3);
        try {
            Type requestType = Type.valueOf(parts[0].toUpperCase());
            //logger.info("Request type: " + requestType);
                String name = parts.length > 1 ? parts[1] : "";
                String data = parts.length > 2 ? parts[2] : "";
                return new Request(requestType, name, data);
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid request: " + request);
            throw new IllegalArgumentException("Invalid request: " + request);
        }
    }

    public enum Type {GET, PUT, DELETE, EXIT}

    @Override
    public String toString() {
        return String.format("%s %s %s", type.name(), name, data);
    }
}
