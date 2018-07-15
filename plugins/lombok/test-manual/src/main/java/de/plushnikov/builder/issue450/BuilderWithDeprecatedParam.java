package de.plushnikov.builder.issue450;

public class BuilderWithDeprecatedParam {

  @lombok.Builder
  private static java.util.Collection<String> creator(String bar, @Deprecated String foo) {
    return java.util.Arrays.asList(bar, foo);
  }

  public static void main(String[] args) {
    System.out.println(BuilderWithDeprecatedParam.builder().bar("bar").foo("foo").build());
  }
}
