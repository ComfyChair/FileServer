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
            "src", "server", "data", "idx");
    private static final File mapFile = storagePath.resolve("storage.idx").toFile();
    final Map<Integer, String> files;
    private int fileIdCounter;

    private Storage(Map<Integer, String> files, int fileIdCounter) {
        this.files = files;
        this.fileIdCounter = fileIdCounter;
    }

    void saveStorage(){
        if (!mapFile.exists()){
            mapFile.mkdirs();
        }
        try(ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(mapFile)))){
            oos.writeObject(this);
        } catch (IOException e) {
            throw new RuntimeException(e);
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

    synchronized int nextFileId()
    {
        return fileIdCounter++;
    }

}
