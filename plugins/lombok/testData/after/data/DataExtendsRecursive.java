import java.util.Date;

public class DataExtendsRecursive extends DataExtendsRecursive{
  private int someInt;
  private String someString;
  private Date someDate;

  public DataExtendsRecursive() {
  }

  public int getSomeInt() {
    return this.someInt;
  }

  public String getSomeString() {
    return this.someString;
  }

  public Date getSomeDate() {
    return this.someDate;
  }

  public void setSomeInt(int someInt) {
    this.someInt = someInt;
  }

  public void setSomeString(String someString) {
    this.someString = someString;
  }

  public void setSomeDate(Date someDate) {
    this.someDate = someDate;
  }

  public boolean equals(final Object o) {
    if (o == this) return true;
    if (!(o instanceof DataExtendsRecursive)) return false;
    final DataExtendsRecursive other = (DataExtendsRecursive) o;
    if (!other.canEqual((Object) this)) return false;
    if (this.getSomeInt() != other.getSomeInt()) return false;
    final Object this$someString = this.getSomeString();
    final Object other$someString = other.getSomeString();
    if (this$someString == null ? other$someString != null : !this$someString.equals(other$someString))
      return false;
    final Object this$someDate = this.getSomeDate();
    final Object other$someDate = other.getSomeDate();
    if (this$someDate == null ? other$someDate != null : !this$someDate.equals(other$someDate)) return false;
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DataExtendsRecursive;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + this.getSomeInt();
    final Object $someString = this.getSomeString();
    result = result * PRIME + ($someString == null ? 43 : $someString.hashCode());
    final Object $someDate = this.getSomeDate();
    result = result * PRIME + ($someDate == null ? 43 : $someDate.hashCode());
    return result;
  }

  public String toString() {
    return "DataExtendsRecursive(someInt=" + this.getSomeInt() + ", someString=" + this.getSomeString() + ", someDate=" + this.getSomeDate() + ")";
  }
}
