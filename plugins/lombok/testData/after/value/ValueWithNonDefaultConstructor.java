public final class ValueWithNonDefaultConstructor {
  public ValueWithNonDefaultConstructor(String msg) {
  }

  public boolean equals(final Object o) {
    if (o == this) return true;
    if (!(o instanceof ValueWithNonDefaultConstructor)) return false;
    final ValueWithNonDefaultConstructor other = (ValueWithNonDefaultConstructor) o;
    return true;
  }

  public int hashCode() {
    int result = 1;
    return result;
  }

  public String toString() {
    return "ValueWithNonDefaultConstructor()";
  }
}
