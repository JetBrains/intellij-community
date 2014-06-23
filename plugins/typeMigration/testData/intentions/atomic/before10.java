// "Convert to atomic" "true"
class Test {
  final int <caret>o = 0;

  void foo() {
    boolean b = this.o == 1;
  }
}