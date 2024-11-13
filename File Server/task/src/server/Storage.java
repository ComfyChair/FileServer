package server;

import java.io.*;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;


public class Storage {
    private static final Logger logger = Logger.getLogger(Storage.class.getName());
    private static final Path storagePath = Path.of(System.getProperty("user.dir"),
            "src", "server", "data");
    private static final File indexFile = storagePath.resolve( "storage.idx").toFile();
    private static Storage instance = null;

    private final FileIndex index;

    private Storage(FileIndex index) {
        this.index = index;
    }

    static Storage getInstance(){
        if (instance == null){
            instance = new Storage(initIndex());
        }
        return instance;
    }

    private static FileIndex initIndex(){
        FileIndex index = null;
        if (indexFile.exists()){
            try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(indexFile)))) {
                index = (FileIndex) in.readObject();
            } catch (IOException e) {
                logger.warning("Error while reading serialized storage map file");
            } catch (ClassNotFoundException e) {
                logger.warning("Error while deserializing storage map");
            }
        }
        return index == null ? new FileIndex(new ConcurrentHashMap<>(), 0) : index;
    }

    /** Returns File if file is in index and actually present, null otherwise */
    public File getFile(FileIdentifier fileIdentifier) {
        String fileName = index.getName(fileIdentifier);
        if (fileName != null){
            File file = storagePath.resolve(fileName).toFile();
            if (file.exists() && file.isFile()){
                return storagePath.resolve(fileName).toFile();
            }
        }
        return null;
    }

    public boolean deleteFile(FileIdentifier fileIdentifier) {
        boolean success;
        String fileName = index.getName(fileIdentifier);
        if (fileName != null && storagePath.resolve(fileName).toFile().exists()){
            File file = storagePath.resolve(fileName).toFile();
            logger.fine("Deleting file " + file.getName());
            boolean wasDeleted = file.delete();
            if (wasDeleted){
                index.remove(fileName);
                success = true;
            } else {
                logger.warning("Error while deleting file " + file.getAbsolutePath());
                success = false;
            }
        } else {
            logger.info("File not found.");
            success = false;
        }
        return success;
    }

    public int saveFile(String name, int fileLength, byte[] content) {
        if (index.contains(name)) { return -1; }
        File file = storagePath.resolve(name).toFile();
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file))){
            logger.fine("Writing to output stream");
            bos.write(content, 0, fileLength);
            logger.info("Saved " + file.getName());
            return index.add(name);
        } catch (IOException e) {
            logger.warning("Error while saving file");
            return -1;
        }
    }

    public String showIndex() {
        return index.idToName.toString();
    }

    void saveIndex() {
        boolean isFileCreated = verifyOrCreateIndexFile();
        if (isFileCreated) {
            try(ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(indexFile)))){
                oos.writeObject(index);
            } catch (IOException e) {
                logger.warning("Could not save index to file.");
            }
        }
    }

    private boolean verifyOrCreateIndexFile() {
        boolean fileExists = indexFile.exists();
        if (!fileExists){
            boolean folderExists= indexFile.getParentFile().exists();
            if (!folderExists){
                folderExists = indexFile.getParentFile().mkdirs();
            }
            if (folderExists){
                try {
                    fileExists = indexFile.createNewFile();
                } catch (IOException e) {
                    logger.warning("Could not create index file.");
                }
            }
        }
        return fileExists;
    }

    private static class FileIndex implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private final ConcurrentMap<Integer, String> idToName;
        private final ConcurrentMap<String, Integer> nameToId;
        private final AtomicInteger fileIdCounter;

        FileIndex(ConcurrentMap<Integer, String> idToName, int fileIdCounter) {
            this.idToName = idToName;
            this.fileIdCounter = new AtomicInteger(fileIdCounter);
            nameToId = new ConcurrentHashMap<>();
            idToName.forEach((key, value) -> nameToId.put(value, key));
        }

        public int add(String name) {
            int id = fileIdCounter.getAndIncrement();
            nameToId.put(name, id);
            idToName.put(id, name);
            return id;
        }

        public boolean contains(String name) {
            return nameToId.containsKey(name);
        }

        String getName(FileIdentifier fileIdentifier) {
            if (fileIdentifier.type() == FileIdentifier.Type.BY_NAME) {
                String name = fileIdentifier.value();
                return nameToId.containsKey(name) ? name : null;
            } else {
                int id = Integer.parseInt(fileIdentifier.value());
                return idToName.getOrDefault(id, null);
            }
        }

        public void remove(String fileName) {
            int id = nameToId.get(fileName);
            nameToId.remove(fileName);
            idToName.remove(id);
        }
    }
}
