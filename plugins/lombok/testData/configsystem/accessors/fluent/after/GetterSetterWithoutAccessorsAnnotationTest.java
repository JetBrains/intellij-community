
public class GetterSetterWithoutAccessorsAnnotationTest {
  private int intProperty;
  private double doubleProperty;
  private boolean booleanProperty;
  private String stringProperty;

  public static void main(String[] args) {
    final GetterSetterWithoutAccessorsAnnotationTest test = new GetterSetterWithoutAccessorsAnnotationTest();
    test.setStringProperty("")
        .setIntProperty(1)
        .setBooleanProperty(true)
        .setDoubleProperty(0.0);

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

  public GetterSetterWithoutAccessorsAnnotationTest intProperty(int intProperty) {
    this.intProperty = intProperty;
    return this;
  }

  public GetterSetterWithoutAccessorsAnnotationTest doubleProperty(double doubleProperty) {
    this.doubleProperty = doubleProperty;
    return this;
  }

  public GetterSetterWithoutAccessorsAnnotationTest booleanProperty(boolean booleanProperty) {
    this.booleanProperty = booleanProperty;
    return this;
  }

  public GetterSetterWithoutAccessorsAnnotationTest stringProperty(String stringProperty) {
    this.stringProperty = stringProperty;
    return this;
  }
}