import lombok.experimental.ExtensionMethod;

@ExtensionMethod(Test.Extensions.class)
class Test {
  void test(String text) {
    text.<caret>
  }

  static class Extensions {
    public static void myExtensionMethod(String a, String b, String c) {
    }
  }
}
