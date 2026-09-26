import lombok.experimental.ExtensionMethod;

@ExtensionMethod(Test.Extensions.class)
class Test {
  void test(Object object) {
    object.<caret>
  }

  static class Extensions {
    public static void myExtensionMethod(String value) {
    }
  }
}
