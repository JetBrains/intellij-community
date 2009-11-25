class Test {
  int i = 0;

  void foo() {
    i += 2;
    i -= 5;
    if (i == 0) {
      i = 9;
    }

    System.out.println(i + 9);
    System.out.println(i - 9);
  }
}