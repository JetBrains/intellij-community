import lombok.experimental.ExtensionMethod;

@ExtensionMethod(Test.Extensions.class)
class Test {
  void test(Receiver receiver) {
    receiver.<caret>
  }

  static class SuperClass {
    void myInstanceMethod(String value) {
    }
  }

  static class Receiver extends SuperClass {
  }

  static class Extensions {
    public static void myInstanceMethod(Receiver receiver, String value) {
    }
  }
}
