final class Value3 {
  private int x;
  private final int y;

  public Value3(int x, int y) {
    this.x = x;
    this.y = y;
  }

  public int getX() {
    return this.x;
  }

  public int getY() {
    return this.y;
  }

  public boolean equals(final Object o) {
    if (o == this) return true;
    if (!(o instanceof Value3)) return false;
    final Value3 other = (Value3) o;
    if (this.getX() != other.getX()) return false;
    if (this.getY() != other.getY()) return false;
    return true;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + this.getX();
    result = result * PRIME + this.getY();
    return result;
  }

  public String toString() {
    return "Value3(x=" + this.getX() + ", y=" + this.getY() + ")";
  }
}
