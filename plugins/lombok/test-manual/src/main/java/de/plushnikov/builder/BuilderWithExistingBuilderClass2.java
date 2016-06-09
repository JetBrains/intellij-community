package de.plushnikov.builder;

public class BuilderWithExistingBuilderClass2<K extends Number> {
  @lombok.experimental.Builder
  public static <Z extends Number> BuilderWithExistingBuilderClass2<Z> staticMethod(Z arg1, boolean arg2, String arg3) {
    return new BuilderWithExistingBuilderClass2<>();
  }

  public static class BuilderWithExistingBuilderClass2Builder<Z extends Number> {
    private Z arg1;

    public void arg2(boolean args) {

    }
  }

  public static void main(String[] args) {
    BuilderWithExistingBuilderClass2Builder<Integer> builder = BuilderWithExistingBuilderClass2.builder();
    builder.arg1(123).arg3("stripng").arg2(true);
    BuilderWithExistingBuilderClass2 result = builder.build();
    System.out.println(result);
  }
}