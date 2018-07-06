public class DataNegativeTest {
  private int intProperty;
  private String stringProperty;

  public DataNegativeTest() {
  }

  public int getIntProperty() {
    return this.intProperty;
  }

  public void setIntProperty(int intProperty) {
    this.intProperty = intProperty;
  }

  public String getStringProperty() {
    return this.stringProperty;
  }

  public void setStringProperty(String stringProperty) {
    this.stringProperty = stringProperty;
  }

  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof DataNegativeTest)) return false;
    final DataNegativeTest other = (DataNegativeTest) o;
    if (!other.canEqual((Object) this)) return false;
    if (this.getIntProperty() != other.getIntProperty()) return false;
    final Objectthis$stringProperty = this.getStringProperty();
    final Objectother$stringProperty = other.getStringProperty();
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

  protected boolean canEqual(Object other) {
    return other instanceof DataNegativeTest;
  }

  public String toString() {
    return "DataNegativeTest(intProperty=" + this.getIntProperty() + ", stringProperty=" + this.getStringProperty() + ")";
  }
}
