package server;

import java.io.Serial;
import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Class for handling indexing of files */
class FileIndex implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private final ConcurrentMap<Integer, String> idToName;
    private final ConcurrentMap<String, Integer> nameToId;
    private final AtomicInteger fileIdCounter;

    /** Constructor
     * @param idToName Mapping of file indices to file names
     * @param fileIdCounter the next available index */
    FileIndex(ConcurrentMap<Integer, String> idToName, int fileIdCounter) {
        this.idToName = idToName;
        this.fileIdCounter = new AtomicInteger(fileIdCounter);
        nameToId = new ConcurrentHashMap<>();
        idToName.forEach((key, value) -> nameToId.put(value, key));
    }

    /** Adds file to index by file name, and assigns a unique id to it
     * @param name the name of the file
     * @return assigned id for the file */
    int add(String name) {
        int id = fileIdCounter.getAndIncrement();
        nameToId.put(name, id);
        idToName.put(id, name);
        return id;
    }

    /** Checks for presence of a file
     * @param name the name of the file
     * @return true if file is present in the index, false otherwise */
    boolean contains(String name) {
        return nameToId.containsKey(name);
    }

    /** Returns the name of a file
     * @param fileIdentifier the identifier of the file, which can contain the name or the index
     * @return file name if file is in index, null otherwise */
    String getName(FileIdentifier fileIdentifier) {
        if (fileIdentifier.type() == FileIdentifier.Type.BY_NAME) {
            String name = fileIdentifier.value();
            return nameToId.containsKey(name) ? name : null;
        } else {
            int id = Integer.parseInt(fileIdentifier.value());
            return idToName.getOrDefault(id, null);
        }
    }

    /** Removes a file from the index
     * @param fileName the name of the file to be removed */
    void remove(String fileName) {
        int id = nameToId.get(fileName);
        nameToId.remove(fileName);
        idToName.remove(id);
    }

    /** Shows content of index for logging purposes */
    String showContent() {
        return idToName.toString();
    }
}
