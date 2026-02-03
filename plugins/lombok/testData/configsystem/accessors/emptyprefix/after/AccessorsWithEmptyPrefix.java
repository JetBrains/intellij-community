public class AccessorsWithEmptyPrefix {
  private int _5gSomething;
  private double anyField;

  public static void main(String[] args) {
    final GetterSetterClassTest test = new GetterSetterClassTest();
    test.get5gSomething();
    test.getAnyField();

    System.out.println(test);
  }

  public int get5gSomething() {
    return this._5gSomething;
  }

  public void set5gSomething(int _5gSomething) {
    this._5gSomething = _5gSomething;
  }

  public double getAnyField() {
    return this.anyField;
  }

  public void setAnyField(double anyField) {
    this.anyField = anyField;
  }
}
