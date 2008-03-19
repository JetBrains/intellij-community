class Mapping {
  private Mapping myMapping;

  public void <caret>method() {
    Mapping m2 = myMapping;
  }

  public void context() {
    Mapping m2 = new Mapping();
  }
}
