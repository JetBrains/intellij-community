import lombok.AccessLevel;
import lombok.Getter;

class Test {
  @Getter
  private float b;

  @lombok.Getter(AccessLevel.PROTECTED)
  private double c;

  @lombok.Getter(lombok.AccessLevel.NONE)
  private String d;

    public String getD() {
    System.out.println("Some custom code");
    return d;
  }
}
