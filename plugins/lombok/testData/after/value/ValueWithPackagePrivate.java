public final class ValueWithPackagePrivate {
  private final int privateInt;
  protected final int protectedInt;
  public final int publicInt;
  private final int anInt;

  final int annotatedInt;

  private int nonFinalInt;

  public final int shouldBePublicInt;

  @java.beans.ConstructorProperties({"privateInt", "protectedInt", "publicInt", "anInt", "annotatedInt", "nonFinalInt", "shouldBePublicInt"})
  public ValueWithPackagePrivate(int privateInt, int protectedInt, int publicInt, int anInt, int annotatedInt, int nonFinalInt, int shouldBePublicInt) {
    this.privateInt = privateInt;
    this.protectedInt = protectedInt;
    this.publicInt = publicInt;
    this.anInt = anInt;
    this.annotatedInt = annotatedInt;
    this.nonFinalInt = nonFinalInt;
    this.shouldBePublicInt = shouldBePublicInt;
  }

  public static void main(String[] args) {
    final ValueWithPackagePrivate test = new ValueWithPackagePrivate(1, 2, 3, 4, 5, 6, 7);
    System.out.println(test);
  }

  public int getPrivateInt() {
    return this.privateInt;
  }

  public int getProtectedInt() {
    return this.protectedInt;
  }

  public int getPublicInt() {
    return this.publicInt;
  }

  public int getAnInt() {
    return this.anInt;
  }

  public int getAnnotatedInt() {
    return this.annotatedInt;
  }

  public int getNonFinalInt() {
    return this.nonFinalInt;
  }

  public int getShouldBePublicInt() {
    return this.shouldBePublicInt;
  }

  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ValueWithPackagePrivate)) {
      return false;
    }
    final ValueWithPackagePrivate other = (ValueWithPackagePrivate) o;
    if (this.privateInt != other.privateInt) {
      return false;
    }
    if (this.protectedInt != other.protectedInt) {
      return false;
    }
    if (this.publicInt != other.publicInt) {
      return false;
    }
    if (this.anInt != other.anInt) {
      return false;
    }
    if (this.annotatedInt != other.annotatedInt) {
      return false;
    }
    if (this.nonFinalInt != other.nonFinalInt) {
      return false;
    }
    if (this.shouldBePublicInt != other.shouldBePublicInt) {
      return false;
    }

    return true;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + this.privateInt;
    result = result * PRIME + this.protectedInt;
    result = result * PRIME + this.publicInt;
    result = result * PRIME + this.anInt;
    result = result * PRIME + this.annotatedInt;
    result = result * PRIME + this.nonFinalInt;
    result = result * PRIME + this.shouldBePublicInt;
    return result;
  }

  public String toString() {
    return "ValueWithPackagePrivate(privateInt=" + this.privateInt + ", protectedInt=" + this.protectedInt + ", publicInt=" + this.publicInt
        + ", anInt=" + this.anInt + ", annotatedInt=" + this.annotatedInt + ", nonFinalInt=" + this.nonFinalInt + ", shouldBePublicInt=" + this.shouldBePublicInt +")";
  }
}
