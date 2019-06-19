package edu.mayo.dhs.ievaluate.plugins.es;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.mayo.dhs.ievaluate.api.IEvaluate;
import edu.mayo.dhs.ievaluate.api.plugins.IEvaluatePlugin;
import edu.mayo.dhs.ievaluate.plugins.es.config.ESPluginConfig;
import edu.mayo.dhs.ievaluate.plugins.es.storage.ESBackedStorageProvider;

import java.io.File;
import java.nio.file.Files;

public class IEvaluateESPlugin extends IEvaluatePlugin {

    private ESPluginConfig config;

    @Override
    public void loadConfig(File configDir) {
        if (!Files.exists(new File(configDir, "config.json").toPath())) {
            throw new IllegalArgumentException("No configuration supplied for IEvaluate-ES-Plugin!");
        }
        try {
            this.config = new ObjectMapper().readValue(new File(configDir, "config.json"), ESPluginConfig.class);
            this.config.checkValid();
        } catch (Throwable e) {
            throw new RuntimeException(e); // Logging handled by plugin manager so long as an exception is thrown
        }
    }

    @Override
    public void onEnable() {
        IEvaluate.getServer().registerStorageProvider(new ESBackedStorageProvider(this.config));
    }
}
