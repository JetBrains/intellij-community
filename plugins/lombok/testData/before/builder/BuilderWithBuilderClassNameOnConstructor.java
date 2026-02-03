import lombok.Builder;
import lombok.Value;

@Value
public class BuilderWithBuilderClassNameOnConstructor {

  String name;
  int age;

  @Builder(builderClassName = "Builder")
  public BuilderWithBuilderClassNameOnConstructor(String name, int age) {
    this.name = name;
    this.age = age;
  }
}
