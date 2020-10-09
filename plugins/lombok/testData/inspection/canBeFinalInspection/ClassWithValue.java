@lombok.Value
public class ClassWithValue {
  private int someFieldValue;

  protected ClassWithValue() {
    someFieldValue = 123;
  }
}
