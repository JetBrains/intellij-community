class AnonymousTest1 {
  interface Thing {
    boolean thing();
  }

  void dupeHolder() {
    if (new Thing() {
      public boolean thing() {
        return false;
      }
    }.thing());
  }

  void <caret>duplicator(final boolean thingReturn) {
    if (new Thing() {
      public boolean thing() {
        return thingReturn;
      }
    }.thing());
  }
}