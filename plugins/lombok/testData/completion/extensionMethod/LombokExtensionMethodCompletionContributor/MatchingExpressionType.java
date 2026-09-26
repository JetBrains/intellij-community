import lombok.experimental.ExtensionMethod;

@ExtensionMethod(Test.Extensions.class)
class Test {
  void test(String string) {
    consume(string.<caret>);
  }

  static void consume(Integer i) {}

  static class Extensions {
    public static Integer myIntegerMethod(String string) {
      return 7;
    }
    public static String myStringMethod(String string) {
      return "";
    }
  }
}
