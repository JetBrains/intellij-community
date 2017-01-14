@lombok.Setter
public class ClassWithSetter {
  private int someFieldSetter;

  protected ClassWithSetter() {
    someFieldSetter = 123;
  }
}
