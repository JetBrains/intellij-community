public class Test {
  public String s;

  public void foo() {
     s = null;
     boolean b = s.equals(s);
  }
}