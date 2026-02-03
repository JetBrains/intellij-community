import lombok.Data;

@Data
class Test {
  private float b;

  @lombok.Getter(lombok.AccessLevel.PROTECTED)
  private double c;

  @lombok.Setter(lombok.AccessLevel.NONE)
  private String d;

    protected double getC() {
    System.out.println("Some custom code");
    return c;
  }

  public void setD(String d) {
    System.out.println("Some custom code");
    this.d = d;
  }
}
