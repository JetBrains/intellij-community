<error descr="The syntax is either @ObtainVia(field = \"fieldName\") or @ObtainVia(method = \"methodName\").">@lombok.Builder</error>
class BuilderObtainVia {

  private String field1;

  @lombok.Builder.ObtainVia
  private String field2;

  public String someMethod() {
    return "someValue";
  }
}

<error descr="The syntax is either @ObtainVia(field = \"fieldName\") or @ObtainVia(method = \"methodName\").">@lombok.Builder</error>
class BuilderObtainViaFieldAndMethod {

  private String field1;

  @lombok.Builder.ObtainVia(field = "field1", method = "someMethod")
  private String field2;

  public String someMethod() {
    return "someValue";
  }
}

<error descr="@ObtainVia(isStatic = true) is not valid unless 'method' has been set.">@lombok.Builder</error>
class BuilderObtainViaFieldStatic {

  private static String field1;

  @lombok.Builder.ObtainVia(field = "field1", isStatic=true)
  private String field2;

  public String someMethod() {
    return "someValue";
  }
}

