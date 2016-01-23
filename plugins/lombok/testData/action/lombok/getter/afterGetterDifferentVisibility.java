import lombok.AccessLevel;
import lombok.Getter;

class Test {
  @Getter
  private float b;
  @Getter(AccessLevel.PROTECTED)
  private double c;
  @Getter(AccessLevel.PRIVATE)
  private String d;

}
