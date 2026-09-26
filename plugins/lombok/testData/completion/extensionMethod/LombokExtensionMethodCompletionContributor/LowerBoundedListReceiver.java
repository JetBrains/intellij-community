import java.util.List;

import lombok.experimental.ExtensionMethod;

@ExtensionMethod(Test.Extensions.class)
class Test {
  void test(List<? super String> values) {
    values.<caret>
  }

  static class Extensions {
    public static void myExtensionMethod(List<? extends String> values) {
    }
  }
}
