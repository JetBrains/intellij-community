public class BuilderWithDeprecatedParam {

  @lombok.Builder
  private static java.util.Collection<String> creator(String bar, @Deprecated String foo) {
    return java.util.Arrays.asList(bar, foo);
  }
}
