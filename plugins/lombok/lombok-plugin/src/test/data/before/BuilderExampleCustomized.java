import lombok.experimental.Builder;

@Builder(builderClassName = "HelloWorldBuilder", buildMethodName = "execute", builderMethodName = "helloWorld", fluent = false, chain = false)
public class BuilderExample {
  private String name;
  private int age;
}