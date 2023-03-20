@lombok.Builder
@lombok.extern.jackson.Jacksonized
@com.fasterxml.jackson.annotation.JsonRootName("RootName")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties("someFloat")
public class BuilderJacksonized {

  private int someInt;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "someProperty", required = true)
  private String someField;
}