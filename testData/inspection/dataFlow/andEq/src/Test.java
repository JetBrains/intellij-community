public class Test {
  public void foo(boolean x, boolean y, boolean z) {
    boolean r = true;
    r &= x;
    r &= y;
    r &= z;
  }
}