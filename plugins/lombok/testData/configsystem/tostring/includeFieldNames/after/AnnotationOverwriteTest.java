public class AnnotationOverwriteTest {

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
    final AnnotationOverwriteTest test = new AnnotationOverwriteTest();
    System.out.println(test.hashCode());
  }

  public String toString() {
    return "AnnotationOverwriteTest(intProperty=" + this.getIntProperty() + ", booleanProperty=" + this.isBooleanProperty() + ", doubleProperty=" + this.getDoubleProperty() + ", stringProperty=" + this.getStringProperty() + ")";
  }
}