class Test {
  private float b;
  @lombok.Getter(lombok.AccessLevel.PRIVATE)
  private double c;
  @lombok.Getter(lombok.AccessLevel.PROTECTED)
  private String d;

  public float getB() {
    return b;
  }

  protected double getC() {
    return c;
  }

  public String getD() {
    return d;
  }
}
