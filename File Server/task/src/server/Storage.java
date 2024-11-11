package server;

import java.io.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static server.Main.logger;

public class Storage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private static Storage instance = null;
    private static final Path storagePath = Path.of(System.getProperty("user.dir"),
            "src", "server", "data");
    private static final File mapFile = storagePath.resolve( "storage.idx").toFile();
    private final Map<Integer, String> idToName;
    private transient final Map<String, Integer> nameToId;
    private int fileIdCounter;

    private Storage(Map<Integer, String> idToName, int fileIdCounter) {
        this.idToName = idToName;
        this.fileIdCounter = fileIdCounter;
        nameToId = new HashMap<>();
        idToName.forEach((key, value) -> {nameToId.put(value, key);});
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

    private static Storage loadStorage(){
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
            instance = loadStorage();
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
            fileName = idToName.get(id);
        } else {
            fileName = fileIdentifier.value();
            id = nameToId.get(fileName);
        }
        idToName.remove(id);
        nameToId.remove(fileName);
        File file = storagePath.resolve(fileName).toFile();
        return file.exists() && file.delete();
    }

    public int saveFile(DataInputStream in, String name) {
        int fileId = nextFileId();
        idToName.put(fileId, name);
        nameToId.put(name, fileId);
        logger.info("Storage updated: " + showIndex());
        File file = storagePath.resolve(name).toFile();
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file))){
            int fileLength = in.readInt();
            logger.info("Reading from DataStream: " + fileLength + " bytes");
            byte[] buffer = new byte[fileLength];
            in.readFully(buffer, 0, fileLength);
            logger.info("Writing to output stream");
            bos.write(buffer, 0, fileLength);
            logger.info("Writing done for " + file.getName());
            return fileId;
        } catch (IOException e) {
            logger.warning("IO error: " + e.getMessage());
            return -1;
        }
    }

    private synchronized int nextFileId()
    {
        return fileIdCounter++;
    }

    public String showIndex() {
        return idToName.toString();
    }
}
