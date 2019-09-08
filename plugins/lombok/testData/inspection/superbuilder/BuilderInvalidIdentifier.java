<error descr="builderMethod^identifier is not a valid identifier">@lombok.experimental.SuperBuilder(builderMethodName = "builderMethod^identifier")</error>
public class BuilderInvalidIdentifier {
  private String field;
}

<error descr="buildMethod^identifier is not a valid identifier">@lombok.experimental.SuperBuilder(buildMethodName = "buildMethod^identifier")</error>
class BuilderInvalidIdentifierBuildMethod {
  private String field;
}
