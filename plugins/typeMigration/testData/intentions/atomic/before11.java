// "Convert to atomic" "false"
class Test {
  void foo() {
    try (AutoCloseable <caret>r = null) {
      System.out.println(r);
    }
  }
}