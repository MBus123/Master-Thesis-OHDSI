package org.ohdsi.hydra.actionHandlers;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import org.ohdsi.utilities.InMemoryFile;

public class AddCustomFile extends AbstractActionHandler {

    public AddCustomFile(JSONObject action, JSONObject studySpecs) {
        super(action, studySpecs);
    }

    List<InMemoryFile> customFiles = new ArrayList<>();

    @Override
    protected void init(JSONObject action, JSONObject studySpecs) {
    }

    @Override
    protected void modifyExistingInternal(InMemoryFile file) {
    }

    public void addCustomFiles(List<InMemoryFile> customFiles) {
        if (customFiles == null) {
            throw new NullPointerException("customFiles is null");
        }
        this.customFiles = customFiles;
    }

    @Override
    protected List<InMemoryFile> generateNewInternal() {
        return this.customFiles;
    }
    
}
