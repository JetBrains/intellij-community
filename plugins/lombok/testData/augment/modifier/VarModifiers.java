import lombok.experimental.var;

class ValModifier {
  void test() {
    var foo<caret> = "123";
  }
}
