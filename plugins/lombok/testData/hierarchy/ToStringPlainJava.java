public class ToStringPlainJava {
  private String description;
  private int priority;
  private boolean completed;

  public ToStringPlainJava(String description, int priority, boolean completed) {
    this.description = description;
    this.priority = priority;
    this.completed = completed;
  }

  @Override
  public String toString() {
    return "ToStringPlainJava{" +
      "description='" + description + '\'' +
      ", priority=" + priority +
      ", completed=" + completed +
      '}';
  }
}
