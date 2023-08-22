import lombok.AccessLevel;
import lombok.Getter;

class Test {
  @Getter
  private float b;
  @lombok.Getter(AccessLevel.PROTECTED)
  private double c;
  @lombok.Getter()
  private String d;

}
