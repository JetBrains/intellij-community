class Mapping1 {
  private int myInt;
  public void <caret>method() {
    int i = myInt;
  }
  public void context() {
    int i = myInt;
  }
}

class Mapping2 {
  private int myInt;
  public void context() {
    int i = myInt;
  }
}
