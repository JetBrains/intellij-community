package de.plushnikov.intellij.plugin.lombokconfig;

public class ConfigIndexKey {
  private final String directoryName;
  private final String configKey;

  public ConfigIndexKey(String directoryName, String configKey) {
    this.directoryName = directoryName;
    this.configKey = configKey;
  }

  public String getDirectoryName() {
    return directoryName;
  }

  public String getConfigKey() {
    return configKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ConfigIndexKey that = (ConfigIndexKey) o;

    if (configKey != null ? !configKey.equalsIgnoreCase(that.configKey) : that.configKey != null) {
      return false;
    }
    return directoryName != null ? directoryName.equals(that.directoryName) : that.directoryName == null;
  }

  @Override
  public int hashCode() {
    int result = directoryName != null ? directoryName.hashCode() : 0;
    result = 31 * result + (configKey != null ? configKey.toLowerCase().hashCode() : 0);
    return result;
  }
}
