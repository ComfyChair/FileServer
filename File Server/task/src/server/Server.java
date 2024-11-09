package server;

import java.util.HashMap;
import java.util.Map;

public class Server {
    private final Map<String, Boolean> files = new HashMap<>();

    Server() {
        initStorage();
    }

    private void initStorage() {
        for (int i = 1; i <= 10; i++) {
            String fileName = "file" + i;
            files.put(fileName, false);
        }
    }

    public String addFile(String fileName) {
        String feedback;
        if (files.containsKey(fileName) && !files.get(fileName)) {
            files.replace(fileName, true);
            feedback = String.format("The file %s added successfully", fileName);
        } else {
            feedback = String.format("Cannot add the file %s", fileName);
        }
        return feedback;
    }

    public String getFile(String query) {
        String feedback;
        if (files.containsKey(query) && files.get(query)) {
            feedback = String.format("The file %s was sent", query);
        } else {
            feedback = String.format("The file %s not found", query);
        }
        return feedback;
    }

    public String deleteFile(String query) {
        String feedback;
        if (files.containsKey(query) && files.get(query)) {
            files.replace(query, false);
            feedback = String.format("The file %s was deleted", query);
        } else {
            feedback = String.format("The file %s not found", query);
        }
        return feedback;
    }
}
