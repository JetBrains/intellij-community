import org.jetbrains.annotations.Nullable;

public class Test {
  @Nullable public final String o;

  public Test(String q) { o = q; }

  public void test() {
    if (o != null) {
      bar();
      o.hashCode();
    }
  }

  public void bar() {}
}