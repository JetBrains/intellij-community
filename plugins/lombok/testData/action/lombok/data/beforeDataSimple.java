import java.util.Objects;

class Test<caret> {
  private float b;
  private double c;
  private String d;

  public float getB() {
    return b;
  }

  public double getC() {
    return c;
  }

  public String getD() {
    return d;
  }

  public void setB(float b) {
    this.b = b;
  }

  public void setC(double c) {
    this.c = c;
  }

  public void setD(String d) {
    this.d = d;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Test test = (Test)o;
    return Float.compare(b, test.b) == 0 && Double.compare(c, test.c) == 0 && Objects.equals(d, test.d);
  }

  @Override
  public int hashCode() {
    return Objects.hash(b, c, d);
  }

  @Override
  public String toString() {
    return "Test{" +
           "b=" + b +
           ", c=" + c +
           ", d='" + d + '\'' +
           '}';
  }
}
