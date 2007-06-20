class <caret>A {
  private int b;

  private class B {
    private int b;

    void doTest() {
      b = A.this.b;
    }
  }
}

class User {
  A a = new A();
}
