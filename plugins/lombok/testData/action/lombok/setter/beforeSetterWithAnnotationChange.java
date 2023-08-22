class Test {
  private float b;
  @lombok.Setter(lombok.AccessLevel.PRIVATE)
  private double c;
  @lombok.Setter(lombok.AccessLevel.PROTECTED)
  private String d;

  public void setB(float b) {
    this.b = b;
  }

  protected void setC(double c) {
    this.c = c;
  }

  public void setD(String d) {
    this.d = d;
  }
}
