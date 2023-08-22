public final class ValueTest {
  private final int intProperty;
  private final String stringProperty;

  private ValueTest() {
    this.intProperty = 0;
    this.stringProperty = null;
  }

  public ValueTest(int intProperty, String stringProperty) {
    this.intProperty = intProperty;
    this.stringProperty = stringProperty;
  }

  public int getIntProperty() {
    return this.intProperty;
  }

  public String getStringProperty() {
    return this.stringProperty;
  }

  public boolean equals(Object o) {
    if (o == this) return true;

    if (!(o instanceof ValueTest)) return false;

    final ValueTest other = (ValueTest) o;
    if (this.getIntProperty() != other.getIntProperty()) return false;

    final Object this$stringProperty = this.getStringProperty();
    final Object other$stringProperty = other.getStringProperty();
    if (this$stringProperty == null ? other$stringProperty != null : !this$stringProperty.equals(other$stringProperty)) return false;

    return true;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + this.getIntProperty();
    final Object $stringProperty = this.getStringProperty();
    result = result * PRIME + ($stringProperty == null ? 43 : $stringProperty.hashCode());
    return result;
  }

  public String toString() {
    return "ValueTest(intProperty=" + this.getIntProperty() + ", stringProperty=" + this.getStringProperty() + ")";
  }
}
