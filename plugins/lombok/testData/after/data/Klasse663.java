public class Klasse663 {
  private Object[] objects;

  public Klasse663() {
  }

  public void setObjects(Object... objects) {
  }

  public Object[] getObjects() {
    return this.objects;
  }

  public boolean equals(final Object o) {
    if (o == this) return true;
    if (!(o instanceof Klasse663)) return false;
    final Klasse663 other = (Klasse663) o;
    if (!other.canEqual((Object) this)) return false;
    if (!java.util.Arrays.deepEquals(this.getObjects(), other.getObjects())) return false;
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof Klasse663;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + java.util.Arrays.deepHashCode(this.getObjects());
    return result;
  }

  public String toString() {
    return "Klasse663(objects=" + java.util.Arrays.deepToString(this.getObjects()) + ")";
  }

  public static void main(String[] args) {
    new Klasse663().setObjects(1, 2, 3);
  }
}
