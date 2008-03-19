class Mapping {
  private int myInt;
  private Mapping myMapping = new Mapping();
  public void <caret>method() {
    Mapping m = new Mapping();
    myInt += m.hashCode();
  }
  public void context() {
    myInt += myMapping.hashCode();  
  }
}