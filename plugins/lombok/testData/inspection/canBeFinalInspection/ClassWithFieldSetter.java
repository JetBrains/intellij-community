public class ClassWithFieldSetter {
  private int <warning descr="Field 'someFieldNoSetter' may be 'final'">someFieldNoSetter</warning>;

  @lombok.Getter
  private int <warning descr="Field 'someFieldGetter' may be 'final'">someFieldGetter</warning>;

  @lombok.Setter
  private int someFieldSetter;

  protected ClassWithFieldSetter() {
    someFieldNoSetter = 123;
    someFieldGetter = 123;
    someFieldSetter = 123;
  }
}





