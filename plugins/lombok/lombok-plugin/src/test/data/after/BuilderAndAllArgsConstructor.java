public class BuilderAndAllArgsConstructor {

  private String field1;
  private String field2;

  @java.beans.ConstructorProperties({"field1", "field2"})
  private BuilderAndAllArgsConstructor(String field1, String field2) {
    this.field1 = field1;
    this.field2 = field2;
  }

  public static BuilderAndAllArgsConstructorBuilder builder() {
    return new BuilderAndAllArgsConstructorBuilder();
  }

  public static class BuilderAndAllArgsConstructorBuilder {
    private String field1;
    private String field2;

    BuilderAndAllArgsConstructorBuilder() {
    }

    public BuilderAndAllArgsConstructor.BuilderAndAllArgsConstructorBuilder field1(String field1) {
      this.field1 = field1;
      return this;
    }

    public BuilderAndAllArgsConstructor.BuilderAndAllArgsConstructorBuilder field2(String field2) {
      this.field2 = field2;
      return this;
    }

    public BuilderAndAllArgsConstructor build() {
      return new BuilderAndAllArgsConstructor(field1, field2);
    }

    public String toString() {
      return "BuilderAndAllArgsConstructor.BuilderAndAllArgsConstructorBuilder(field1=" + this.field1 + ", field2=" + this.field2 + ")";
    }
  }
}

