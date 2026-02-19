public class DataStaticConstructor2 {
  private int privateInt;
  private final int privateFinalInt;

  // Custom private constructor
  private DataStaticConstructor2(int privateFinalInt) {
    this.privateInt = 5;
    this.privateFinalInt = privateFinalInt;
  }

  public static void main(String[] args) {
    final DataStaticConstructor2 test = new DataStaticConstructor2.of(5);
    System.out.println(test);
  }

  public static DataStaticConstructor2 of(final int privateFinalInt) {
    return new DataStaticConstructor2(privateFinalInt);
  }

  public int getPrivateInt() {
    return this.privateInt;
  }

  public int getPrivateFinalInt() {
    return this.privateFinalInt;
  }

  public void setPrivateInt(int privateInt) {
    this.privateInt = privateInt;
  }

  public boolean equals(final Object o) {
    if (o == this) return true;
    if (!(o instanceof DataStaticConstructor2)) return false;
    final DataStaticConstructor2 other = (DataStaticConstructor2) o;
    if (!other.canEqual((Object) this)) return false;
    if (this.privateInt != other.privateInt) return false;
    if (this.privateFinalInt != other.privateFinalInt) return false;
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DataStaticConstructor2;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + this.privateInt;
    result = result * PRIME + this.privateFinalInt;
    return result;
  }

  public String toString() {
    return "DataStaticConstructor2(privateInt=" + this.privateInt + ", privateFinalInt=" + this.privateFinalInt + ")";
  }
}
