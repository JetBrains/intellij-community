class Types {
  public void <caret>method(Object v) {
    int i = v.hashCode();
  }

  public void context() {
    String v = "child type";
    int i = v.hashCode();
  }
}
