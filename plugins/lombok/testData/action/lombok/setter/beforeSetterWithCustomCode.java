class Test {
  private float b;

  @lombok.Setter(lombok.AccessLevel.PROTECTED)
  private double c;
  @lombok.Setter(lombok.AccessLevel.NONE)
  private String d;

  public void setB(float b) {
    this.b = b;
  }

  protected void setC(double c) {
    this.c = c;
  }

  public void setD(String d) {
    System.out.println("Some custom code");
    this.d = d;
  }
}
