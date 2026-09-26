import lombok.experimental.ExtensionMethod;

@ExtensionMethod(Test.Extensions.class)
class Test {
  void test(Receiver receiver) {
    receiver.<caret>
  }

  static class Receiver {
    void myInstanceMethod(String value) {
    }
  }

  static class Extensions {
    public static void myInstanceMethod(Receiver receiver, String value, int x) {
    }
  }
}
