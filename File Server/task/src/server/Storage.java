package server;

import java.io.*;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;


/** Storage Singleton class
 * Manages saving and deleting file from "cwd/src/server/data", as well as queries for files by id and name.
 * Index management is delegated to a FileIndex,
 * which can be saved as "cwd/src/server/data/storage.idx".
 * If previously saved, the FileIndex is restored when the Singleton is created. */
public class Storage {
    private static final Logger logger = Logger.getLogger(Storage.class.getName());
    private static final Path storagePath = Path.of(System.getProperty("user.dir"),
            "src", "server", "data");
    private static final File indexFile = storagePath.resolve( "storage.idx").toFile();
    private static Storage instance = null;

    private final FileIndex index;

    /** private constructor, only used if instance is null */
    private Storage(FileIndex index) {
        this.index = index;
    }

    /** Provides access to the Storage Singleton instance
     * @return The one and only instance of Storage
     */
    static Storage getInstance(){
        if (instance == null){
            instance = new Storage(initIndex());
        }
        return instance;
    }

    /** Initializes the FileIndex either from a saved index or by creating an empty index
     * @return the initialized FileIndex
     */
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

    /** Returns the queried file if it is present in the index and null otherwise
     * @param fileIdentifier the identifier of the file
     * @return the file if it exists or null otherwise
     * */
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

    /** Deletes file if file is found in index and exists
     * @return true if successful and false otherwise */
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

    /** Saves file if file of the same name is not yet in index
     * @return assigned file index if successful and -1 otherwise */
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

    /** Checks if index file exists and tries to create it if not
     * @return true if file is present and false otherwise */
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

    /** Saves index to file; call when exiting */
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

    /** Shows content of index for logging purposes */
    public String showIndex() {
        return index.showContent();
    }
}
