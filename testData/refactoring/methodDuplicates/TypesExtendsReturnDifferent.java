class Types {
  public Object <caret>method(Object v) {
    return v.toString();
  }

  public void context() {
    String v = "child type";
    v.toString().trim();
  }
}
