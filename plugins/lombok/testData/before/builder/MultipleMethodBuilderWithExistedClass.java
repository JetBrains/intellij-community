import lombok.Builder;

public class MultipleMethodBuilderWithExistedClass {
  private final long id;
  private final String name;

  public MultipleMethodBuilderWithExistedClass(long id, String name) {
    this.id = id;
    this.name = name;
  }

  // (1)
  @Builder(builderMethodName = "builderB", builderClassName = "BuilderB")
  private static MultipleMethodBuilderWithExistedClass builderB(long id) {
    return new MultipleMethodBuilderWithExistedClass(id, "");
  }

  // (2)
  @Builder(builderMethodName = "builderA", builderClassName = "BuilderA")
  private static MultipleMethodBuilderWithExistedClass builderA(String name) {
    return new MultipleMethodBuilderWithExistedClass(0L, name);
  }

  static {
    new BuilderA().name("");
    new BuilderB().id(0L);
  }

  // (3)
  public static class BuilderA {
  }

  // (4)
  public static class BuilderB {
  }
}
