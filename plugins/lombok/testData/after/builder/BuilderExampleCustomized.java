public class BuilderExampleCustomized {
  private String name;
  private int age;

  @java.beans.ConstructorProperties({"name", "age"})
  BuilderExampleCustomized(String name, int age) {
    this.name = name;
    this.age = age;
  }

  public static HelloWorldBuilder helloWorld() {
    return new HelloWorldBuilder();
  }

  public static class HelloWorldBuilder {
    private String name;
    private int age;

    HelloWorldBuilder() {
    }

    public HelloWorldBuilder name(String name) {
      this.name = name;
      return this;
    }

    public HelloWorldBuilder age(int age) {
      this.age = age;
      return this;
    }

    public BuilderExampleCustomized execute() {
      return new BuilderExampleCustomized(name, age);
    }

    public String toString() {
      return "BuilderExampleCustomized.HelloWorldBuilder(name=" + this.name + ", age=" + this.age + ")";
    }
  }
}
