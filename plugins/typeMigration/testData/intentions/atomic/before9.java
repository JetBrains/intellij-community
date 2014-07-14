// "Convert to atomic" "true"
class Test {
  final int <caret>o;
  int j = o;

  Test(int o) {
    this.o = o;
  }

  void foo() {
    while ((o = j) != 0) {}
  }
}