public class Test {
  public Test(int anObject) {
    int i = anObject;
  }

  public Test get() {
    return new Test(0) {
    }
  }
}