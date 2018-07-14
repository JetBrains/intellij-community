@lombok.experimental.Builder(builderClassName = "HelloWorldBuilder", buildMethodName = "execute",
  builderMethodName = "helloWorld", fluent = false, chain = false)
public class BuilderExampleCustomized {
  private String name;
  private int age;
}
