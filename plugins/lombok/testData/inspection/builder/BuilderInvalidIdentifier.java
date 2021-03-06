<error descr="invalid^identifier is not a valid identifier">@lombok.Builder(builderClassName = "invalid^identifier")</error>
public class BuilderInvalidIdentifier {
  private String field;
}

<error descr="builderMethod^identifier is not a valid identifier">@lombok.Builder(builderMethodName = "builderMethod^identifier")</error>
class BuilderInvalidIdentifierBuilderMethod {
  private String field;
}

<error descr="buildMethod^identifier is not a valid identifier">@lombok.Builder(buildMethodName = "buildMethod^identifier")</error>
class BuilderInvalidIdentifierBuildMethod {
  private String field;
}
