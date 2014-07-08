// "Convert to atomic" "true"
class Test {
  int[] <caret>field=foo();
  int[] foo() {
    return null;
  }
}