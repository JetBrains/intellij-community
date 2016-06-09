@lombok.experimental.Wither
@lombok.Value
@lombok.RequiredArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ValueAndWither {
  private final String myField;

  public void methodCallingWith() {
    this.withMyField("");
  }
}