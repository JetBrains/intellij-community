import lombok.val;

class ValModifier {
  void test() {
    val foo<caret> = "123";
  }
}