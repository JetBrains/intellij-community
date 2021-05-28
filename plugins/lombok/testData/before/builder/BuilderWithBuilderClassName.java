import lombok.Builder;
import lombok.Value;

@Value
@Builder(builderClassName = "Builder")
public class BuilderWithBuilderClassName {

  String name;
  int age;
}
