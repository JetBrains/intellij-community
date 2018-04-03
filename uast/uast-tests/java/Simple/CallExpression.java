public class Foo {
  public static String foo = String.format("q");

  static {
    String.format("%d %s", 1, "asd");
    String.format("%s", (Object[])new String[]{"a", "b", "c"});
    String.format("%s", new String[]{"d", "e", "f"});
  }
}
