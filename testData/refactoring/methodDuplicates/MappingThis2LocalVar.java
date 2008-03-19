class Mapping {
  private int myInt;
  public void <caret>method() {
    Mapping m = new Mapping();
    myInt += m.hashCode();
  }
  public void context() {
    myInt += hashCode();  
  }
}