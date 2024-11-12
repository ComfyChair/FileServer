package server;

public final class Request {
    private final RequestType requestType;
    private FileIdentifier fileIdentifier = null;

    /** Constructor for EXIT requests */
    public Request(RequestType requestType) {
        this.requestType = requestType;
    }

    /** constructor for PUT, GET and DELETE requests */
    public Request(RequestType requestType, FileIdentifier fileIdentifier) {
        this.requestType = requestType;
        this.fileIdentifier = fileIdentifier;
    }

    static Request parse(String request) {
        String[] parts = request.split(" ", 2);
        try {
            RequestType requestType = RequestType.valueOf(parts[0].toUpperCase());
            return switch (requestType) {
                case EXIT -> new Request(RequestType.EXIT);
                case GET -> buildRequest(RequestType.GET, parts[1]);
                case PUT -> buildRequest(RequestType.PUT, parts[1]);
                case DELETE -> buildRequest(RequestType.DELETE, parts[1]);
            };
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid request: " + request);
        }
    }

    private static Request buildRequest(RequestType requestType, String args) {
        String[] parts = args.split(" ");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid arguments: " + args);
        }
        FileIdentifier.Type identifierType = FileIdentifier.Type.valueOf(parts[0].toUpperCase());
        String value = parts[1];
        return new Request(requestType, new FileIdentifier(identifierType, value));
    }

    @Override
    public String toString() {
        return fileIdentifier == null ? requestType.name() : String.format("%s %s", requestType.name(), fileIdentifier);
    }

    public RequestType getRequestType() {
        return requestType;
    }

    public FileIdentifier getFileIdentifier() {
        return fileIdentifier;
    }

    public enum RequestType {GET, PUT, DELETE, EXIT}

}
