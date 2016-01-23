import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;

public /*final*/ class Val {

  String nonFinal;

  /*final*/ String otherFinal;

  public Val(String otherFinal) {
    this.otherFinal = otherFinal;
  }

  public String getNonFinal() {
    return this.nonFinal;
  }

  public String getOtherFinal() {
    return this.otherFinal;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Val val = (Val) o;

    if (nonFinal != null ? !nonFinal.equals(val.nonFinal) : val.nonFinal != null) return false;
    if (otherFinal != null ? !otherFinal.equals(val.otherFinal) : val.otherFinal != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = nonFinal != null ? nonFinal.hashCode() : 0;
    result = 31 * result + (otherFinal != null ? otherFinal.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("Val{");
    sb.append("nonFinal=").append(nonFinal);
    sb.append(", otherFinal=").append(otherFinal);
    sb.append('}');
    return sb.toString();
  }

  public void test() {
    Val val = new Val("otherFinal");
  }
}