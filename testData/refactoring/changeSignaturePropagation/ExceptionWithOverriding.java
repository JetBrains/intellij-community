class ChangeSignatureTest {
    void <caret>foo() {
    }

    void bar() {
      foo();
    }

    {
        bar();
    }
}

class Derived extends ChangeSignatureTest {
  void bar () {
    super.bar();
  }
}