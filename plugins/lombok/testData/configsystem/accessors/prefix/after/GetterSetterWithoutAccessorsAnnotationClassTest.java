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
    //test.isBooleanProperty();
    //test.setBooleanProperty(true);
    //test.getAStringProperty();
    //test.setAStringProperty("");

    System.out.println(test);
  }

  public int getIntProperty() {
    return this.mIntProperty;
  }

  public double getDoubleProperty() {
    return this.pDoubleProperty;
  }

  public boolean is_BooleanProperty() {
    return this.m_BooleanProperty;
  }

  public void setIntProperty(int mIntProperty) {
    this.mIntProperty = mIntProperty;
  }

  public void setDoubleProperty(double pDoubleProperty) {
    this.pDoubleProperty = pDoubleProperty;
  }

  public void set_BooleanProperty(boolean m_BooleanProperty) {
    this.m_BooleanProperty = m_BooleanProperty;
  }
}