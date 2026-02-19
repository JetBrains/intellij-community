import lombok.ToString;

@ToString
public class ToStringLombok {
  private String description;
  private int priority;
  private boolean completed;

  public ToStringLombok(String description, int priority, boolean completed) {
    this.description = description;
    this.priority = priority;
    this.completed = completed;
  }
}
