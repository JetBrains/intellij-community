import lombok.AccessLevel;
import lombok.Setter;

class Test {
  @Setter
  private float b;

  @lombok.Setter(AccessLevel.PROTECTED)
  private double c;
  @lombok.Setter(lombok.AccessLevel.NONE)
  private String d;

    public void setD(String d) {
    System.out.println("Some custom code");
    this.d = d;
  }
}
