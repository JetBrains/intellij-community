import lombok.EqualsAndHashCode;

public class WithSuperTest {

  @EqualsAndHashCode
  private static class BasisClass {
    private int intProperty;
    private boolean booleanProperty;

    public int getIntProperty() {
      return intProperty;
    }

    public boolean isBooleanProperty() {
      return booleanProperty;
    }
  }

  @EqualsAndHashCode
  private static class ExtendedClass extends BasisClass {
    private double doubleProperty;
    private String stringProperty;

    public double getDoubleProperty() {
      return doubleProperty;
    }

    public String getStringProperty() {
      return stringProperty;
    }
  }

  public static void main(String[] args) {
    final ExtendedClass test = new ExtendedClass();
    System.out.println(test.hashCode());
  }
}