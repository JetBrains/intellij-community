@lombok.Builder
public class BuilderWithDeprecatedField {
  private String bar;

  @Deprecated
  private String foo;

  @Deprecated
  @lombok.Singular
  private java.util.List<String> xyzs;
}
