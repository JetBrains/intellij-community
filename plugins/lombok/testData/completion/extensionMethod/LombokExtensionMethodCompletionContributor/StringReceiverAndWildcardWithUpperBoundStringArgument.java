 import java.util.List;

import lombok.experimental.ExtensionMethod;

@ExtensionMethod(Test.Extensions.class)
class Test {
  <T extends String> void test(T t) {
    t.<caret>
  }

  static class Extensions {
    public static void myExtensionMethod(String string) {
    }
  }
}
