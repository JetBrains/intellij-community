package de.plushnikov.builder;

public class BuilderWithExistingBuilderClass2<K extends Number> {
  @lombok.Builder
  public static <K extends Number> BuilderWithExistingBuilderClass2<K> staticMethod(K arg1, boolean arg2, String arg3) {
    return new BuilderWithExistingBuilderClass2<K>();
  }

  public static class BuilderWithExistingBuilderClass2Builder<K extends Number> {
    private K arg1;

    public void arg2(boolean args) {

    }
  }

  public static void main(String[] args) {
    BuilderWithExistingBuilderClass2<Integer> class2 = staticMethod(123, true, "");
    BuilderWithExistingBuilderClass2Builder<Integer> builder = BuilderWithExistingBuilderClass2.<Integer>builder();
    builder.arg1(123).arg3("stripng").arg2(true);
    BuilderWithExistingBuilderClass2 result = builder.build();
    System.out.println(result);
  }
}
