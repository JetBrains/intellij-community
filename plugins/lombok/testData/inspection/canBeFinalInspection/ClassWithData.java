@lombok.Data
public class ClassWithData {
  private int someFieldData;

  protected ClassWithData() {
    someFieldData = 123;
  }
}
