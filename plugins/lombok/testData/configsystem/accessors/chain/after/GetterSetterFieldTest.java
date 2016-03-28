
public class GetterSetterFieldTest {
  private int intProperty;
  private double doubleProperty;
  private boolean booleanProperty;
  private String stringProperty;

  public static void main(String[] args) {
    final GetterSetterFieldTest test = new GetterSetterFieldTest();
    test.setStringProperty("")
        .setIntProperty(1)
        .setBooleanProperty(true)
        .setDoubleProperty(0.0);

    System.out.println(test);
  }

  public int getIntProperty() {
    return this.intProperty;
  }

  public double getDoubleProperty() {
    return this.doubleProperty;
  }

  public boolean isBooleanProperty() {
    return this.booleanProperty;
  }

  public String getStringProperty() {
    return this.stringProperty;
  }

  public GetterSetterFieldTest setIntProperty(int intProperty) {
    this.intProperty = intProperty;
    return this;
  }

  public GetterSetterFieldTest setDoubleProperty(double doubleProperty) {
    this.doubleProperty = doubleProperty;
    return this;
  }

  public GetterSetterFieldTest setBooleanProperty(boolean booleanProperty) {
    this.booleanProperty = booleanProperty;
    return this;
  }

  public GetterSetterFieldTest setStringProperty(String stringProperty) {
    this.stringProperty = stringProperty;
    return this;
  }
}