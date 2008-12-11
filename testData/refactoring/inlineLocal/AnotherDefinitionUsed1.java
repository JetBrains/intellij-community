class A {
    String s;
    boolean foo() {
      boolean b<caret>ar = false;
      if (s == null) {
        bar = true;
      }
      return bar;
    }
}