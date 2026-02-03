@lombok.Builder
@lombok.experimental.Accessors(prefix = "m")
public class BuilderWithFieldAccessors {

  @lombok.experimental.Accessors(prefix = "p")
  private final int pUpper;

  @lombok.experimental.Accessors(prefix = "_")
  private int _foo;

  private String mBar;
}
