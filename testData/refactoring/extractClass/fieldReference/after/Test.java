class Test {
  int myField;
    private final Extracted extracted = new Extracted(this);

    Test(){
    myField = 7;
  }

  void foo() {
      extracted.foo();
  }

  void bar() {
      extracted.foo();
  }

    public int getMyField() {
        return myField;
    }
}