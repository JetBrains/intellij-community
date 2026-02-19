@lombok.Builder
@com.fasterxml.jackson.annotation.JsonRootName("RootName")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties("someFloat")
public class BuilderCopyableAnnotations {

  @javax.persistence.Column(name = "dont_copy_1")
  private float someFloat;

  @javax.persistence.Column(name = "dont_copy_2")
  @com.fasterxml.jackson.annotation.JsonAlias("someAlias")
  private int someInt;

  @javax.persistence.Column(name = "dont_copy_3")
  @com.fasterxml.jackson.annotation.JsonProperty(value = "someProperty", required = true)
  private String someField;

  @lombok.Singular(ignoreNullCollections = true)
  @javax.persistence.Column(name = "dont_copy_4")
  @com.fasterxml.jackson.annotation.JsonAnySetter
  private java.util.List<String> someStrings;
}