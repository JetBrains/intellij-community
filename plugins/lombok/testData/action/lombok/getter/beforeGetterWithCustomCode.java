class Test {
  private float b;

  @lombok.Getter(lombok.AccessLevel.PROTECTED)
  private double c;

  @lombok.Getter(lombok.AccessLevel.NONE)
  private String d;

  public float getB() {
    return b;
  }

  protected double getC() {
    return c;
  }

  public String getD() {
    System.out.println("Some custom code");
    return d;
  }
}
