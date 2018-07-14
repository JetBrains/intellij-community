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

    public void setName(String name) {
      this.name = name;
    }

    public void setAge(int age) {
      this.age = age;
    }

    public BuilderExampleCustomized execute() {
      return new BuilderExampleCustomized(name, age);
    }

    public String toString() {
      return "BuilderExampleCustomized.HelloWorldBuilder(name=" + this.name + ", age=" + this.age + ")";
    }
  }
}
