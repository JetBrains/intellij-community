package org.editorconfig.configmanagement;

public class InvalidConfigException extends Exception {
    private final String configKey;
    private final String configValue;
    private final String filePath;

    public InvalidConfigException(String configKey, String configValue, String filePath) {
        this.configKey = configKey;
        this.configValue = configValue;
        this.filePath = filePath;
    }

    @Override
    public String getMessage() {
        return "\"" + configValue + "\" is not a valid value for " + configKey + " for file " + filePath;
    }
}
