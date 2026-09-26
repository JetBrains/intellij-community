import lombok.experimental.ExtensionMethod;

@ExtensionMethod(Test.Extensions.class)
class Test {
  void test(String text) {
    text.<caret>
  }

  static class Extensions {
    public void myExtensionMethod(Object value) {
    }
  }
}
