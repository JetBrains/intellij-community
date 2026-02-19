import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public class Main {
  private boolean isOpen;

  public void open() {
    this.isOpen = true;
  }
}