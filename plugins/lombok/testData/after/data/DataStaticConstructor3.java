public class DataStaticConstructor3 {
  private int privateInt;

  // Custom private constructor
  private DataStaticConstructor3() {
    this.privateInt = 5;
  }

  public static void main(String[] args) {
    final DataStaticConstructor3 test = new DataStaticConstructor3.of();
    System.out.println(test);
  }

  public static DataStaticConstructor3 of() {
    return new DataStaticConstructor3();
  }

  public int getPrivateInt() {
    return this.privateInt;
  }

  public void setPrivateInt(int privateInt) {
    this.privateInt = privateInt;
  }

  public boolean equals(final Object o) {
    if (o == this) return true;
    if (!(o instanceof DataStaticConstructor3)) return false;
    final DataStaticConstructor3 other = (DataStaticConstructor3) o;
    if (!other.canEqual((Object) this)) return false;
    if (this.privateInt != other.privateInt) return false;
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DataStaticConstructor3
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + this.privateInt;
    return result;
  }

  public String toString() {
    return "DataStaticConstructor3(privateInt=" + this.privateInt + ")";
  }
}
