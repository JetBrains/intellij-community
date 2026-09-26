import lombok.experimental.ExtensionMethod;

@ExtensionMethod(Test.Extensions.class)
class Test {
  void test(String s) {
    s.extensionMethod<caret>(1, 2, 3);
  }

  static final class Extensions {
    public static String extensionMethod(String receiver, int value) {
      return receiver + value;
    }

    public static String extensionMethod(String receiver, int first, int second) {
      return receiver + first + second;
    }
  }
}
