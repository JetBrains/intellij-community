import lombok.experimental.ExtensionMethod;

@ExtensionMethod(Test.Extensions.class)
class Test {
  void test(String[] array) {
    array.<caret>
  }

  static class Extensions {
    public static void myExtensionMethod(String... args) {
    }
  }
}
