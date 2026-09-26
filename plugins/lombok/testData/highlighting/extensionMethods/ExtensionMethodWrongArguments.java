import lombok.experimental.ExtensionMethod;

@ExtensionMethod(ExtensionMethodWrongArguments.Extensions.class)
class ExtensionMethodWrongArguments {
  void test(String s) {
    s.extensionMethod<error descr="Expected 1 argument but found 0">()</error>;
  }

  static class Extensions {
    public static String extensionMethod(String receiver, int value) {
      return receiver + value;
    }
  }
}
