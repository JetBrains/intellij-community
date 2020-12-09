public class MultipleMethodBuilderWithExistedClass {
  private final long id;
  private final String name;

  public MultipleMethodBuilderWithExistedClass(long id, String name) {
    this.id = id;
    this.name = name;
  }

  private static MultipleMethodBuilderWithExistedClass builderB(long id) {
    return new MultipleMethodBuilderWithExistedClass(id, "");
  }

  private static MultipleMethodBuilderWithExistedClass builderA(String name) {
    return new MultipleMethodBuilderWithExistedClass(0L, name);
  }

  static {
    (new MultipleMethodBuilderWithExistedClass.BuilderA()).name("");
    (new MultipleMethodBuilderWithExistedClass.BuilderB()).id(0L);
  }

  public static class BuilderB {

    private long id;


    BuilderB() {
    }


    public MultipleMethodBuilderWithExistedClass.BuilderB id(long id) {
      this.id = id;
      return this;
    }


    public MultipleMethodBuilderWithExistedClass build() {
      return MultipleMethodBuilderWithExistedClass.builderB(id);
    }


    public String toString() {
      return "MultipleMethodBuilderWithExistedClass.BuilderB(id=" + this.id + ")";
    }
  }

  public static class BuilderA {

    private String name;


    BuilderA() {
    }


    public MultipleMethodBuilderWithExistedClass.BuilderA name(String name) {
      this.name = name;
      return this;
    }


    public MultipleMethodBuilderWithExistedClass build() {
      return MultipleMethodBuilderWithExistedClass.builderA(name);
    }


    public String toString() {
      return "MultipleMethodBuilderWithExistedClass.BuilderA(name=" + this.name + ")";
    }
  }
}
