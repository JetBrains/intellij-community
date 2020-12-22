public class SomeTest {

  private int intProperty;
  private boolean booleanProperty;
  private double doubleProperty;
  private String stringProperty;

  public int getIntProperty() {
    return intProperty;
  }

  public boolean isBooleanProperty() {
    return booleanProperty;
  }

  public double getDoubleProperty() {
    return doubleProperty;
  }

  public String getStringProperty() {
    return stringProperty;
  }

  public static void main(String[] args) {
    final SomeTest test = new SomeTest();
    System.out.println(test.hashCode());
  }

  public String toString() {
    return "SomeTest(" + this.getIntProperty() + ", " + this.isBooleanProperty() + ", " + this.getDoubleProperty() + ", " + this.getStringProperty() + ")";
  }
}