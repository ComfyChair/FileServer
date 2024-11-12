package server;

import java.io.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;


public class Storage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private static Storage instance = null;
    private static final Path storagePath = Path.of(System.getProperty("user.dir"),
            "src", "server", "data");
    private static final File mapFile = storagePath.resolve( "storage.idx").toFile();
    private final Map<Integer, String> idToName;
    private final Map<String, Integer> nameToId;
    private int fileIdCounter;
    private static final Logger logger = Logger.getLogger(Storage.class.getName());;

    private Storage(Map<Integer, String> idToName, int fileIdCounter) {
        this.idToName = idToName;
        this.fileIdCounter = fileIdCounter;
        nameToId = new HashMap<>();
        idToName.forEach((key, value) -> nameToId.put(value, key));
    }

    void saveStorage() {
        try{
            if (!mapFile.exists()){
                mapFile.getParentFile().mkdirs();
                mapFile.createNewFile();
            }
            ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(mapFile)));
            oos.writeObject(this);
            oos.close();
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
        return storage == null ? new Storage(new HashMap<>(), 0) : storage;
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
        int fileId = nextFileId();
        idToName.put(fileId, name);
        nameToId.put(name, fileId);
        logger.info("Storage updated: " + showIndex());
        return fileId;
    }

    private void removeFromIndex(int id, String fileName) {
        idToName.remove(id);
        nameToId.remove(fileName);
    }

    private synchronized int nextFileId()
    {
        return fileIdCounter++;
    }

    public String showIndex() {
        return idToName.toString();
    }
}
