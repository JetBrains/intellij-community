public class ClassNormal {
  private int <warning descr="Field 'someFieldNormal' may be 'final'">someFieldNormal</warning>;

  protected ClassNormal() {
    someFieldNormal = 123;
  }
}
