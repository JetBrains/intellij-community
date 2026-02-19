import lombok.CustomLog;

public class CustomLogTest {
  private static final pack.age.Outer.InnerLogger log = pack.age.Outer.Builder.create("outer", null);

  public void logSomething() {
    log.info("Hello World!");
  }

  public static void main(String[] args) {
    log.info("Test");
    new CustomLogTest().logSomething();
    new Inner().logSomething();
  }

  static class Nested {
    private static final pack.age.Outer.InnerLogger log = pack.age.Outer.Builder.create(Nested.class.getName(), Nested.class, Nested.class.getName());

    public void logSomething() {
      log.info("Hello World from Nested!");
    }
  }
}
