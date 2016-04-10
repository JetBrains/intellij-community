import lombok.Value;

public final class Foo {
  private final String one;
  private final String two = "foo";

  @java.beans.ConstructorProperties({"one"})
  public Foo(String one) {
    this.one = one;
  }

  public static void main(String[] args) {
    System.out.println(new Foo("one"));
  }

  public String getOne() {
    return this.one;
  }

  public String getTwo() {
    return this.two;
  }

  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof Foo)) return false;
    final Foo other = (Foo) o;
    final Object this$one = this.one;
    final Object other$one = other.one;
    if (this$one == null ? other$one != null : !this$one.equals(other$one)) return false;
    final Object this$two = this.two;
    final Object other$two = other.two;
    if (this$two == null ? other$two != null : !this$two.equals(other$two)) return false;
    return true;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $one = this.one;
    result = result * PRIME + ($one == null ? 0 : $one.hashCode());
    final Object $two = this.two;
    result = result * PRIME + ($two == null ? 0 : $two.hashCode());
    return result;
  }

  public String toString() {
    return "Foo(one=" + this.one + ", two=" + this.two + ")";
  }
}