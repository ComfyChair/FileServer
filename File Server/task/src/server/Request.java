package server;

/** Wrapper class for Requests, containing RequestType and FileIdentifier, if applicable */
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

    /** Reconstructs a Request from its string-serialized form
     * @param requestString the string encoding the request
     * @return the reconstructed request
     * @throws IllegalArgumentException if the requestString does not represent a valid Request
     */
    static Request parse(String requestString) {
        String[] parts = requestString.split(" ", 2);
        try {
            RequestType requestType = RequestType.valueOf(parts[0].toUpperCase());
            return switch (requestType) {
                case EXIT -> new Request(RequestType.EXIT);
                case GET -> buildRequest(RequestType.GET, parts[1]);
                case PUT -> buildRequest(RequestType.PUT, parts[1]);
                case DELETE -> buildRequest(RequestType.DELETE, parts[1]);
            };
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid request: " + requestString);
        }
    }

    /** Helper function for reconstructing Requests that contain a FileIdentifier
     * @param requestType the request type
     * @param identifierString the string-encoded FileIdentifier, e.g. "BY_ID 2" or "BY_NAME awesome.jpg"
     * @return the reconstructed Request
     */
    private static Request buildRequest(RequestType requestType, String identifierString) {
        String[] parts = identifierString.split(" ");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid arguments: " + identifierString);
        }
        FileIdentifier.Type identifierType = FileIdentifier.Type.valueOf(parts[0].toUpperCase());
        String value = parts[1];
        return new Request(requestType, new FileIdentifier(identifierType, value));
    }

    /** Encodes the Request as a string for sending or logging */
    @Override
    public String toString() {
        return fileIdentifier == null ? requestType.name() : String.format("%s %s", requestType.name(), fileIdentifier);
    }

    /** Returns the RequestType */
    public RequestType getRequestType() {
        return requestType;
    }

    /** Returns the FileIdentifier */
    public FileIdentifier getFileIdentifier() {
        return fileIdentifier;
    }

    /** Enum class of implemented RequestTypes */
    public enum RequestType {GET, PUT, DELETE, EXIT}
}
