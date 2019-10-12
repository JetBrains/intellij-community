public final class ValueStaticConstructor {
  private final int privateInt;

  // Custom private constructor
  private ValueStaticConstructor(int privateInt) {
    this.privateInt = -privateInt;
  }

  public static void main(String[] args) {
    final ValueStaticConstructor test = new ValueStaticConstructor.of(1);
    System.out.println(test);
  }

  public static ValueStaticConstructor of(final int privateInt) {
    return new ValueStaticConstructor(privateInt);
  }

  public int getPrivateInt() {
    return this.privateInt;
  }

  public boolean equals(final Object o) {
    if (o == this) return true;
    if (!(o instanceof ValueStaticConstructor)) return false;
    final ValueStaticConstructor other = (ValueStaticConstructor) o;
    if (this.getPrivateInt() != other.getPrivateInt()) return false;
    return true;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + this.getPrivateInt();
    return result;
  }

  public String toString() {
    return "ValueStaticConstructor(privateInt=" + this.getPrivateInt() + ")";
  }
}
