public class BuilderExample {
  private String name;
  private int age;

  BuilderExample(String name, int age) {
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

    public BuilderExample execute() {
      return new BuilderExample(name, age);
    }

    @java.lang.Override
    public String toString() {
      return "BuilderExample.HelloWorld(name = " + this.name + ", age = " + this.age + ")";
    }
  }
}