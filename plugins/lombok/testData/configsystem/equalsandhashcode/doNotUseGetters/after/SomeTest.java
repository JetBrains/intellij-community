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

  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof SomeTest)) return false;
    final SomeTest other = (SomeTest) o;
    if (!other.canEqual((java.lang.Object) this)) return false;
    if (this.intProperty != other.intProperty) return false;
    if (this.booleanProperty != other.booleanProperty) return false;
    if (java.lang.Double.compare(this.doubleProperty, other.doubleProperty) != 0) return false;
    final java.lang.Object this$stringProperty = this.stringProperty;
    final java.lang.Object other$stringProperty = other.stringProperty;
    if (this$stringProperty == null ? other$stringProperty != null : !this$stringProperty.equals(other$stringProperty))
      return false;
    return true;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + this.intProperty;
    result = result * PRIME + (this.booleanProperty ? 79 : 97);
    final long $doubleProperty = java.lang.Double.doubleToLongBits(this.doubleProperty);
    result = result * PRIME + (int) ($doubleProperty >>> 32 ^ $doubleProperty);
    final java.lang.Object $stringProperty = this.stringProperty;
    result = result * PRIME + ($stringProperty == null ? 0 : $stringProperty.hashCode());
    return result;
  }

  protected boolean canEqual(java.lang.Object other) {
    return other instanceof SomeTest;
  }
}