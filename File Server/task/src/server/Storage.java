package server;

import java.io.*;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;


public class Storage implements Serializable {
    @Serial
    private static final long serialVersionUID = 2L;
    private static final Logger logger = Logger.getLogger(Storage.class.getName());
    private static final Path storagePath = Path.of(System.getProperty("user.dir"),
            "src", "server", "data");
    private static final File mapFile = storagePath.resolve( "storage.idx").toFile();
    private static Storage instance = null;

    private final ConcurrentMap<Integer, String> idToName;
    private final ConcurrentMap<String, Integer> nameToId;
    private AtomicInteger fileIdCounter;


    private Storage(ConcurrentMap<Integer, String> idToName, int fileIdCounter) {
        this.idToName = idToName;
        this.fileIdCounter = new AtomicInteger(fileIdCounter);
        nameToId = new ConcurrentHashMap<>();
        idToName.forEach((key, value) -> nameToId.put(value, key));
    }

    void saveStorage() {
        try{
            if (!mapFile.exists()){
                mapFile.getParentFile().mkdirs();
                mapFile.createNewFile();
            }
            try(ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(mapFile)))){
                oos.writeObject(this);
            }
        } catch (IOException e) {
            System.err.println("IO exception while writing index file: " + e.getMessage());
        }
    }

    private static Storage initStorage(){
        Storage storage = null;
        if (mapFile.exists()){
            try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(mapFile)))) {
                storage = (Storage) in.readObject();
            } catch (IOException e) {
                logger.warning("Error while reading serialized storage map file");
            } catch (ClassNotFoundException e) {
                logger.warning("Error while deserializing storage map");
            }
        }
        return storage == null ? new Storage(new ConcurrentHashMap<>(), 0) : storage;
    }

    static Storage getInstance(){
        if (instance == null){
            instance = initStorage();
        }
        return instance;
    }

    public File getFile(FileIdentifier fileIdentifier) {
        String fileName = fileIdentifier.type() == FileIdentifier.Type.BY_NAME ?
                fileIdentifier.value() : idToName.get(Integer.parseInt(fileIdentifier.value()));
        return storagePath.resolve(fileName).toFile();
    }

    public boolean deleteFile(FileIdentifier fileIdentifier) {
        int id;
        String fileName;
        if (fileIdentifier.type() == FileIdentifier.Type.BY_ID) {
            id = Integer.parseInt(fileIdentifier.value());
            if (!idToName.containsKey(id)) { return false; }
            fileName = idToName.get(id);
        } else {
            fileName = fileIdentifier.value();
            if (!nameToId.containsKey(fileName)) { return false; }
            id = nameToId.get(fileName);
        }
        File file = storagePath.resolve(fileName).toFile();
        boolean success;
        if (file.exists()){
            logger.fine("Deleting file " + file.getName());
            boolean wasDeleted = file.delete();
            if (wasDeleted){
                removeFromIndex(id, fileName);
                success = true;
            } else {
                logger.warning("Error while deleting file " + file.getAbsolutePath());
                success = false;
            }
        } else {
            logger.info("File not found " + file.getAbsolutePath());
            success = false;
        }
        return success;
    }

    public int saveFile(String name, int fileLength, byte[] content) {
        if (nameToId.containsKey(name)) { return -1; }
        File file = storagePath.resolve(name).toFile();
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file))){
            logger.fine("Writing to output stream");
            bos.write(content, 0, fileLength);
            logger.info("Writing done for " + file.getName());
            return addToIndex(name);
        } catch (IOException e) {
            logger.warning("IO error: " + e.getMessage());
            return -1;
        }
    }

    private int addToIndex(String name) {
        int fileId = fileIdCounter.getAndIncrement();
        idToName.put(fileId, name);
        nameToId.put(name, fileId);
        logger.info("Storage updated: " + showIndex());
        return fileId;
    }

    private void removeFromIndex(int id, String fileName) {
        idToName.remove(id);
        nameToId.remove(fileName);
        logger.info("Storage updated: " + showIndex());
    }

    public String showIndex() {
        return idToName.toString();
    }
}
