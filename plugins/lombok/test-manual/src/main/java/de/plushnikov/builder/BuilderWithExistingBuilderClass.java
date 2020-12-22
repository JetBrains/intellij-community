package de.plushnikov.builder;

class BuilderWithExistingBuilderClass {
  @lombok.Builder
  public static BuilderWithExistingBuilderClass staticMethod(Integer arg1, boolean arg2, String arg3) {
    return null;
  }

  public static class BuilderWithExistingBuilderClassBuilder {
    private Integer arg1;

    public void arg2(boolean arg2123) {
    }
  }

  public static void main(String[] args) {
    BuilderWithExistingBuilderClassBuilder builder = BuilderWithExistingBuilderClass.builder();
    builder.arg1(123).arg3("string").arg2(true);
    BuilderWithExistingBuilderClass result = builder.build();
    System.out.println(result);
  }
}
