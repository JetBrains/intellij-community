public class GetterSetterWithoutAccessorsAnnotationClassTest {
  private int mIntProperty;
  private double pDoubleProperty;
  private boolean m_BooleanProperty;
  private String aStringProperty;

  public static void main(String[] args) {
    final GetterSetterWithoutAccessorsAnnotationClassTest test = new GetterSetterWithoutAccessorsAnnotationClassTest();
    test.getIntProperty();
    test.setIntProperty(1);
    test.getDoubleProperty();
    test.setDoubleProperty(0.0);
    test.isBooleanProperty();
    test.setBooleanProperty(true);
    test.getAStringProperty();
    test.setAStringProperty("");

    System.out.println(test);
  }

  public int getIntProperty() {
    return this.mIntProperty;
  }

  public double getDoubleProperty() {
    return this.pDoubleProperty;
  }

  public boolean isBooleanProperty() {
    return this.m_BooleanProperty;
  }

  public String getAStringProperty() {
    return this.aStringProperty;
  }

  public void setIntProperty(int mIntProperty) {
    this.mIntProperty = mIntProperty;
  }

  public void setDoubleProperty(double pDoubleProperty) {
    this.pDoubleProperty = pDoubleProperty;
  }

  public void setBooleanProperty(boolean m_BooleanProperty) {
    this.m_BooleanProperty = m_BooleanProperty;
  }

  public void setAStringProperty(String aStringProperty) {
    this.aStringProperty = aStringProperty;
  }
}