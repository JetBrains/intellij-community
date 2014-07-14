// "Convert to atomic" "true"
class Test {
  int <caret>o = 0;
  int j = o;

  void foo() {
    while ((o = j) != 0) {}
  }
}