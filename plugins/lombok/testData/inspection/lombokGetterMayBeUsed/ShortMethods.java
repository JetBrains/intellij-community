public class ShortMethods {
  private int someInt;
  private boolean someBoolean;
  private boolean b;
  private boolean x;

  <warning descr="Field 'someInt' may have Lombok @Getter">public int getSomeInt() {
    return someInt;
  }</warning>

  <warning descr="Field 'someBoolean' may have Lombok @Getter">public boolean isSomeBoolean() {
    return someBoolean;
  }</warning>

  <warning descr="Field 'b' may have Lombok @Getter">public boolean isB() {
    return b;
  }</warning>

  public int getX() {
    return someInt;
  }

  public int get() {
    return someInt;
  }

  public boolean is() {
    return b;
  }

  public boolean b() {
    return b;
  }
}