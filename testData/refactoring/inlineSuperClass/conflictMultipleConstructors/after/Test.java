class Test {
  Test(String s, String s1){super(s);}

    void foo() {
      Test s = new Test("");
      s.bar();
    }

    void bar() {}
}