import lombok.AccessLevel;
import lombok.Setter;

class Test {
  @Setter
  private float b;
  @lombok.Setter(AccessLevel.PROTECTED)
  private double c;
  @lombok.Setter()
  private String d;

}
