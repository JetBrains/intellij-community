package de.plushnikov.intellij.plugin.lombokconfig;

public class ConfigValue {
  private final String value;
  private final boolean stopBubbling;

  public ConfigValue(String value, boolean stopBubbling) {
    this.value = value;
    this.stopBubbling = stopBubbling;
  }

  public String getValue() {
    return value;
  }

  public boolean isStopBubbling() {
    return stopBubbling;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ConfigValue that = (ConfigValue) o;

    if (stopBubbling != that.stopBubbling) return false;
    return value != null ? value.equals(that.value) : that.value == null;

  }

  @Override
  public int hashCode() {
    int result = value != null ? value.hashCode() : 0;
    result = 31 * result + (stopBubbling ? 1 : 0);
    return result;
  }
}
