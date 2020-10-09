final class Value1 {
  private final int x;
  private final String name;

  public Value1(int x, String name) {
    this.x = x;
    this.name = name;
  }

  public int getX() {
    return this.x;
  }

  public String getName() {
    return this.name;
  }

  public boolean equals(final Object o) {
    if (o == this) return true;
    if (!(o instanceof Value1)) return false;
    final Value1 other = (Value1) o;
    if (this.getX() != other.getX()) return false;
    final Object this$name = this.getName();
    final Object other$name = other.getName();
    if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
    return true;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + this.getX();
    final Object $name = this.getName();
    result = result * PRIME + ($name == null ? 43 : $name.hashCode());
    return result;
  }

  public String toString() {
    return "Value1(x=" + this.getX() + ", name=" + this.getName() + ")";
  }
}

