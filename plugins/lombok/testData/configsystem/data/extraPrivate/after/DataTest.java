public class DataTest {
  private final int intProperty;
  private String stringProperty;

  private DataTest() {
    this.intProperty = 0;
  }

  public DataTest(int intProperty) {
    this.intProperty = intProperty;
  }

  public int getIntProperty() {
    return this.intProperty;
  }

  public String getStringProperty() {
    return this.stringProperty;
  }

  public void setStringProperty(String stringProperty) {
    this.stringProperty = stringProperty;
  }

  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof DataTest)) return false;
    final DataTest other = (DataTest) o;
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
    return other instanceof DataTest;
  }

  public String toString() {
    return "DataTest(intProperty=" + this.getIntProperty() + ", stringProperty=" + this.getStringProperty() + ")";
  }
}
