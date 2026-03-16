import java.util.Objects;

public final class ValuePlainJava {
  private final String code;
  private final int version;
  private final boolean enabled;

  public ValuePlainJava(String code, int version, boolean enabled) {
    this.code = code;
    this.version = version;
    this.enabled = enabled;
  }

  public String getCode() {
    return code;
  }

  public int getVersion() {
    return version;
  }

  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ValuePlainJava that = (ValuePlainJava) o;
    return version == that.version && enabled == that.enabled && Objects.equals(code, that.code);
  }

  @Override
  public int hashCode() {
    return Objects.hash(code, version, enabled);
  }

  @Override
  public String toString() {
    return "ValuePlainJava{" +
      "code='" + code + '\'' +
      ", version=" + version +
      ", enabled=" + enabled +
      '}';
  }
}
