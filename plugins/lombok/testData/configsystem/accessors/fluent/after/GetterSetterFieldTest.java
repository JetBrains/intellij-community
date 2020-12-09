
public class GetterSetterFieldTest {
  private int intProperty;
  private double doubleProperty;
  private boolean booleanProperty;
  private String stringProperty;

  public static void main(String[] args) {
    final GetterSetterFieldTest test = new GetterSetterFieldTest();
    test.stringProperty("");
    test.intProperty(1);
    test.booleanProperty(true);
    test.doubleProperty(0.0);

    System.out.println(test);
  }

  public int intProperty() {
    return this.intProperty;
  }

  public double doubleProperty() {
    return this.doubleProperty;
  }

  public boolean booleanProperty() {
    return this.booleanProperty;
  }

  public String stringProperty() {
    return this.stringProperty;
  }

  public GetterSetterFieldTest intProperty(int intProperty) {
    this.intProperty = intProperty;
    return this;
  }

  public GetterSetterFieldTest doubleProperty(double doubleProperty) {
    this.doubleProperty = doubleProperty;
    return this;
  }

  public GetterSetterFieldTest booleanProperty(boolean booleanProperty) {
    this.booleanProperty = booleanProperty;
    return this;
  }

  public GetterSetterFieldTest stringProperty(String stringProperty) {
    this.stringProperty = stringProperty;
    return this;
  }
}