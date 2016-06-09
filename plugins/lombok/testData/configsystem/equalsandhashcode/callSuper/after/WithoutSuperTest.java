import lombok.EqualsAndHashCode;

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

  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof WithoutSuperTest)) return false;
    final WithoutSuperTest other = (WithoutSuperTest) o;
    if (!other.canEqual((java.lang.Object) this)) return false;
    if (!super.equals(o)) return false;
    if (this.getIntProperty() != other.getIntProperty()) return false;
    if (this.isBooleanProperty() != other.isBooleanProperty()) return false;
    if (java.lang.Double.compare(this.getDoubleProperty(), other.getDoubleProperty()) != 0) return false;
    final java.lang.Object this$stringProperty = this.getStringProperty();
    final java.lang.Object other$stringProperty = other.getStringProperty();
    if (this$stringProperty == null ? other$stringProperty != null : !this$stringProperty.equals(other$stringProperty))
      return false;
    return true;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + super.hashCode();
    result = result * PRIME + this.getIntProperty();
    result = result * PRIME + (this.isBooleanProperty() ? 79 : 97);
    final long $doubleProperty = java.lang.Double.doubleToLongBits(this.getDoubleProperty());
    result = result * PRIME + (int) ($doubleProperty >>> 32 ^ $doubleProperty);
    final java.lang.Object $stringProperty = this.getStringProperty();
    result = result * PRIME + ($stringProperty == null ? 0 : $stringProperty.hashCode());
    return result;
  }

  protected boolean canEqual(Object other) {
    return other instanceof WithoutSuperTest;
  }
}
