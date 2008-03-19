class Mapping {
  private int myInt;

  public void <caret>method() {
    this.myInt++;
    Mapping.this.myInt--;
    myInt += super.hashCode();
  }

  public void context() {
    myInt++;
    this.myInt--;
    myInt += hashCode();
  }
}
