public class EqualsAndHashCodeIncludeExclude {
  int x;
  boolean bool;
  String a;

  int getX() {
    return x;
  }

  boolean isBool() {
    return bool;
  }

  String getA() {
    return a;
  }

  public boolean equals(final Object o) {
    if (o == this) return true;
    if (!(o instanceof EqualsAndHashCodeIncludeExclude)) return false;
    final EqualsAndHashCodeIncludeExclude other = (EqualsAndHashCodeIncludeExclude) o;
    if (!other.canEqual((Object) this)) return false;
    if (this.getX() != other.getX()) return false;
    final Object this$aaa = this.getA();
    final Object other$aaa = other.getA();
    if (this$aaa == null ? other$aaa != null : !this$aaa.equals(other$aaa)) return false;
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EqualsAndHashCodeIncludeExclude;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + this.getX();
    final Object $aaa = this.getA();
    result = result * PRIME + ($aaa == null ? 43 : $aaa.hashCode());
    return result;
  }
}
