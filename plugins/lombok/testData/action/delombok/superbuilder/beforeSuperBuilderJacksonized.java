@lombok.experimental.SuperBuilder
@lombok.extern.jackson.Jacksonized
@com.fasterxml.jackson.annotation.JsonRootName("RootName")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties("someInt")
public class SuperBuilderJacksonized {

  private int someInt;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "someProperty", required = true)
  private String someField;
}