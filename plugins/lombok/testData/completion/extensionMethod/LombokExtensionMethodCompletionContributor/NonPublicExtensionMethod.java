import lombok.experimental.ExtensionMethod;

@ExtensionMethod(Test.Extensions.class)
class Test {
  void test(String text) {
    text.<caret>
  }

  static class Extensions {
    static void myExtensionMethod(Object value) {
    }
  }
}
