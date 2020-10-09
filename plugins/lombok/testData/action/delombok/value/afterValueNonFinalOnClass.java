class Value2 {
  public final int x;
  private final String name;
  
  public Value2(int x, String name) {
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
    if (!(o instanceof Value2)) return false;
    final Value2 other = (Value2) o;
    if (!other.canEqual((Object) this)) return false;
    if (this.getX() != other.getX()) return false;
    final Object this$name = this.getName();
    final Object other$name = other.getName();
    if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
    return true;
  }
  
  protected boolean canEqual(final Object other) {
    return other instanceof Value2;
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
    return "Value2(x=" + this.getX() + ", name=" + this.getName() + ")";
  }
}
