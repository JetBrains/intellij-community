class ChangeSignatureTest {
    public <caret>ChangeSignatureTest() {
    }

    void foo() {
        new ChangeSignatureTest();
    }

    {
      foo();
    }
}
