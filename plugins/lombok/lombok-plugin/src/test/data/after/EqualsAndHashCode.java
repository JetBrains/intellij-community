class EqualsAndHashCode {

  int x;
  boolean[] y;
  Object[] z;
  String a;

  @java.lang.Override
  @java.lang.SuppressWarnings("all")
  public boolean equals(final java.lang.Object o) {
    if (o == this) return true;
    if (!(o instanceof EqualsAndHashCode)) return false;
    final EqualsAndHashCode other = (EqualsAndHashCode) o;
    if (!other.canEqual((java.lang.Object) this)) return false;
    if (this.x != other.x) return false;
    if (!java.util.Arrays.equals(this.y, other.y)) return false;
    if (!java.util.Arrays.deepEquals(this.z, other.z)) return false;
    if (this.a == null ? other.a != null : !this.a.equals((java.lang.Object) other.a)) return false;
    return true;
  }

  @java.lang.SuppressWarnings("all")
  public boolean canEqual(final java.lang.Object other) {
    return other instanceof EqualsAndHashCode;
  }

  @java.lang.Override
  @java.lang.SuppressWarnings("all")
  public int hashCode() {
    final int PRIME = 31;
    int result = 1;
    result = result * PRIME + this.x;
    result = result * PRIME + java.util.Arrays.hashCode(this.y);
    result = result * PRIME + java.util.Arrays.deepHashCode(this.z);
    result = result * PRIME + (this.a == null ? 0 : this.a.hashCode());
    return result;
  }
}

final class EqualsAndHashCode2 {

  int x;

  @java.lang.Override
  @java.lang.SuppressWarnings("all")
  public boolean equals(final java.lang.Object o) {
    if (o == this) return true;
    if (!(o instanceof EqualsAndHashCode2)) return false;
    final EqualsAndHashCode2 other = (EqualsAndHashCode2) o;
    if (this.x != other.x) return false;
    return true;
  }

  @java.lang.Override
  @java.lang.SuppressWarnings("all")
  public int hashCode() {
    final int PRIME = 31;
    int result = 1;
    result = result * PRIME + this.x;
    return result;
  }
}

final class EqualsAndHashCode3 extends EqualsAndHashCode {


  @java.lang.Override
  @java.lang.SuppressWarnings("all")
  public boolean equals(final java.lang.Object o) {
    if (o == this) return true;
    if (!(o instanceof EqualsAndHashCode3)) return false;
    final EqualsAndHashCode3 other = (EqualsAndHashCode3) o;
    if (!other.canEqual((java.lang.Object) this)) return false;
    return true;
  }

  @java.lang.SuppressWarnings("all")
  public boolean canEqual(final java.lang.Object other) {
    return other instanceof EqualsAndHashCode3;
  }

  @java.lang.Override
  @java.lang.SuppressWarnings("all")
  public int hashCode() {
    int result = 1;
    return result;
  }
}

class EqualsAndHashCode4 extends EqualsAndHashCode {


  @java.lang.Override
  @java.lang.SuppressWarnings("all")
  public boolean equals(final java.lang.Object o) {
    if (o == this) return true;
    if (!(o instanceof EqualsAndHashCode4)) return false;
    final EqualsAndHashCode4 other = (EqualsAndHashCode4) o;
    if (!other.canEqual((java.lang.Object) this)) return false;
    if (!super.equals(o)) return false;
    return true;
  }

  @java.lang.SuppressWarnings("all")
  public boolean canEqual(final java.lang.Object other) {
    return other instanceof EqualsAndHashCode4;
  }

  @java.lang.Override
  @java.lang.SuppressWarnings("all")
  public int hashCode() {
    final int PRIME = 31;
    int result = 1;
    result = result * PRIME + super.hashCode();
    return result;
  }
}