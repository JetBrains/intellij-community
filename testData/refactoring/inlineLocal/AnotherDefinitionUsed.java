class A {
    String s;
    boolean foo() {
      boolean bar = false;
      if (s == null) {
        ba<caret>r = true;
      }
      return bar;
    }
}