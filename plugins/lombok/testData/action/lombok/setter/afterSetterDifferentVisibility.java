import lombok.AccessLevel;
import lombok.Setter;

class Test {
  @Setter
  private float b;
  @Setter(AccessLevel.PROTECTED)
  private double c;
  @Setter(AccessLevel.PRIVATE)
  private String d;

}
