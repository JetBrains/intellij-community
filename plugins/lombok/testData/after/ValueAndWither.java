public class ValueAndWither {
  private final String myField;

  @java.beans.ConstructorProperties({"myField"})
  public ValueAndWither(String myField) {
    this.myField = myField;
  }

  public void methodCallingWith() {
    this.withMyField("");
  }

  public String getMyField() {
    return this.myField;
  }

  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof ValueAndWither)) return false;
    final ValueAndWither other = (ValueAndWither) o;
    final Object this$myField = this.myField;
    final Object other$myField = other.myField;
    if (this$myField == null ? other$myField != null : !this$myField.equals(other$myField)) return false;
    return true;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $myField = this.myField;
    result = result * PRIME + ($myField == null ? 0 : $myField.hashCode());
    return result;
  }

  public String toString() {
    return "ValueAndWither(myField=" + this.myField + ")";
  }

  public ValueAndWither withMyField(String myField) {
    return this.myField == myField ? this : new ValueAndWither(myField);
  }
}