import lombok.experimental.ExtensionMethod;

@ExtensionMethod(Test.Extensions.class)
class Test {
  void test(String text) {
    text.myExtension<caret>
  }

  static class Extensions {
    public static void myExtensionMethod(String text) {
    }
    public static void myExtensionMethod2(String text) {
    }
    public static void someOtherExtensionMethod(String text) {
    }
  }
}
