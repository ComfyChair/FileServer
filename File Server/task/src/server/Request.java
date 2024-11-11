package server;

public final class Request {
    private int requestId = 0;
    private final RequestType requestType;
    private IdentifierType identifierType = null;
    private String fileIdentifier = "";

    /** Constructor for EXIT requests */
    public Request(RequestType requestType) {
        this.requestType = requestType;
    }

    /** constructor for PUT requests */
    public Request(RequestType requestType, String fileName) {
        this.requestType = requestType;
        this.fileIdentifier = fileName;
    }

    /** constructor for GET and DELETE requests */
    public Request(RequestType requestType, IdentifierType type, String fileIdentifier) {
        this.requestType = requestType;
        this.identifierType = type;
        this.fileIdentifier = fileIdentifier;
    }

    static Request parse(String request) {
        String[] parts = request.split(" ", 2);
        try {
            RequestType requestType = RequestType.valueOf(parts[0].toUpperCase());
            return switch (requestType) {
                case EXIT -> new Request(RequestType.EXIT);
                case GET -> buildAccessRequest(RequestType.GET, parts[1]);
                case PUT -> buildStoreRequest(parts[1]);
                case DELETE -> buildAccessRequest(RequestType.DELETE, parts[1]);
            };
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid request: " + request);
        }
    }

    private static Request buildStoreRequest(String args) {
        String[] parts = args.split(" ");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid arguments: " + args);
        }
        return new Request(RequestType.PUT, parts[0]);
    }

    private static Request buildAccessRequest(RequestType requestType, String args) {
        String[] parts = args.split(" ");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid arguments: " + args);
        }
        IdentifierType identifierType = IdentifierType.valueOf(parts[0].toUpperCase());
        String identifier = parts[1];
        return new Request(requestType, identifierType, identifier);
    }

    @Override
    public String toString() {
        String resultString;
        if (identifierType == null) {
            resultString = String.format("%s %s", requestType.name(), fileIdentifier);
        } else {
            resultString = String.format("%s %s %s", requestType.name(), identifierType, fileIdentifier);
        }
        return resultString;
    }

    public void setId(int id) {
        this.requestId = id;
    }

    public int getRequestId() {
        return requestId;
    }

    public RequestType getRequestType() {
        return requestType;
    }

    public IdentifierType getIdentifierType() {
        return identifierType;
    }

    public String getFileIdentifier() {
        return fileIdentifier;
    }

    public record Identifier(IdentifierType type, String value) {}

    public enum IdentifierType {BY_ID, BY_NAME}

    public enum RequestType {GET, PUT, DELETE, EXIT}

}
