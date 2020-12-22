@lombok.Getter
public class ClassWithGetter {
  private int <warning descr="Field 'someFieldGetter' may be 'final'">someFieldGetter</warning>;

  protected ClassWithGetter() {
    someFieldGetter = 123;
  }
}

