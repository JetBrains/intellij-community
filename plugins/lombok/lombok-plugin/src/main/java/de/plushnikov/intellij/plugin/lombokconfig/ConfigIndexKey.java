package de.plushnikov.intellij.plugin.lombokconfig;

public class ConfigIndexKey {
  private final String packageName;
  private final String configKey;

  public ConfigIndexKey(String packageName, String configKey) {
    this.packageName = packageName;
    this.configKey = configKey;
  }

  public String getPackageName() {
    return packageName;
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

    if (configKey != null ? !configKey.equals(that.configKey) : that.configKey != null) {
      return false;
    }
    if (packageName != null ? !packageName.equals(that.packageName) : that.packageName != null) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = packageName != null ? packageName.hashCode() : 0;
    result = 31 * result + (configKey != null ? configKey.hashCode() : 0);
    return result;
  }
}
