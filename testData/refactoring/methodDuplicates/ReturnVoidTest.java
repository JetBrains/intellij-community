class ReturnVoidTest {
  interface Thing {
    boolean thing();
  }

  void dupeHolder() {
    if (false) return;
  }

  void <caret>duplicator(final boolean thingReturn) {
    if (thingReturn ) return;
  }
}