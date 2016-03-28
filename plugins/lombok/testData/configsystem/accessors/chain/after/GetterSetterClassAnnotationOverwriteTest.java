public class GetterSetterClassAnnotationOverwriteTest {
  private int intProperty;
  private double doubleProperty;
  private boolean booleanProperty;
  private String stringProperty;

  public static void main(String[] args) {
    final GetterSetterClassAnnotationOverwriteTest test = new GetterSetterClassAnnotationOverwriteTest();
    test.setStringProperty("");
    test.setIntProperty(1)
    test.setBooleanProperty(true)
    test.setDoubleProperty(0.0);

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

  public void setIntProperty(int intProperty) {
    this.intProperty = intProperty;
  }

  public void setDoubleProperty(double doubleProperty) {
    this.doubleProperty = doubleProperty;
  }

  public void setBooleanProperty(boolean booleanProperty) {
    this.booleanProperty = booleanProperty;
  }

  public void setStringProperty(String stringProperty) {
    this.stringProperty = stringProperty;
  }
}