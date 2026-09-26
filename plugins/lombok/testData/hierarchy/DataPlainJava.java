import java.util.Objects;

public class DataPlainJava {
  private Long id;
  private String name;
  private boolean active;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DataPlainJava that = (DataPlainJava) o;
    return active == that.active && Objects.equals(id, that.id) && Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, active);
  }

  @Override
  public String toString() {
    return "DataPlainJava{" +
      "id=" + id +
      ", name='" + name + '\'' +
      ", active=" + active +
      '}';
  }
}
