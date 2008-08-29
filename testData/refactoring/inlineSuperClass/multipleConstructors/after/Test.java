class Test {
  Test(String s){super(s);}

    void foo() {
      Test s = new Test("");
      s.bar();
    }

    void bar() {}
}