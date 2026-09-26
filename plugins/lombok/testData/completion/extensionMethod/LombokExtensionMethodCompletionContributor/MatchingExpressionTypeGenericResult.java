import lombok.experimental.ExtensionMethod;

@ExtensionMethod(Test.Extensions.class)
class Test {
  void consume(String value) {
  }

  void test(String receiver) {
    consume(receiver.<caret>);
  }

  static class Extensions {
    public static <T> T myGenericMethod(String receiver, Class<T> type) {
      return null;
    }
  }
}
