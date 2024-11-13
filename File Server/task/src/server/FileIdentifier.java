package server;

public record FileIdentifier(Type type, String value) {
    public enum Type {BY_ID, BY_NAME}

    @Override
    public String toString() {
        return String.format("%s %s", type.name(), value);
    }
}
