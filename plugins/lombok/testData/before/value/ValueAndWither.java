@lombok.experimental.Wither
@lombok.Value
public class ValueAndWither {
  private final String myField;

  public void methodCallingWith() {
    this.withMyField("");
  }
}