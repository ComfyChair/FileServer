package server;

/** Wrapper to unify file identification by name or by id
 * @param type type of file identification (BY_NAME or BY_ID)
 * @param value the id or the name */
public record FileIdentifier(Type type, String value) {
    /** Enum for identification variants */
    public enum Type {BY_ID, BY_NAME}

    /** Encodes the FileIdentifier as a string for sending or logging */
    @Override
    public String toString() {
        return String.format("%s %s", type.name(), value);
    }
}
