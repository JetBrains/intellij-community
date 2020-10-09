import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public class WithoutSuperTest {

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
    final WithoutSuperTest test = new WithoutSuperTest();
    System.out.println(test.hashCode());
  }
}
