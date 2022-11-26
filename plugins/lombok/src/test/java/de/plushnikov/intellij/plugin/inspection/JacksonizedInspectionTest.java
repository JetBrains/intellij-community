package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;

public class JacksonizedInspectionTest extends LombokInspectionTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/" + TEST_DATA_INSPECTION_DIRECTORY + "/jacksonized";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new LombokInspection();
  }

  public void testJacksonizedBuilderTest() {
    configureAndTest("""
                           @lombok.Builder
                           @lombok.extern.jackson.Jacksonized
                           public class %s {
                             private String someField;
                           }
                       """);
  }

  public void testJacksonizedSuperBuilderTest() {
    configureAndTest("""
                           @lombok.experimental.SuperBuilder
                           @lombok.extern.jackson.Jacksonized
                           public class %s {
                             private String someField;
                           }
                       """);
  }

  public void testJacksonizedWithoutBuilderTest() {
    configureAndTest("""
                           <warning descr="@Jacksonized requires @Builder or @SuperBuilder for it to mean anything.">@lombok.extern.jackson.Jacksonized</warning>
                           public class %s {
                             private String someField;
                           }
                       """);
  }

  public void testJacksonizedBuilderAndSuperBuilderTest() {
    configureAndTest("""
                           <error descr="@Jacksonized cannot process both @Builder and @SuperBuilder on the same class.">@lombok.extern.jackson.Jacksonized</error>
                           @lombok.Builder
                           @lombok.experimental.SuperBuilder
                           public class %s {
                             private String someField;
                           }
                       """);
  }

  public void testJacksonizedAbstractBuilderTest() {
    configureAndTest("""
                           <error descr="Builders on abstract classes cannot be @Jacksonized (the builder would never be used).">@lombok.extern.jackson.Jacksonized</error>
                           @lombok.Builder
                           public abstract class %s {
                             private String someField;
                           }
                       """);
  }

  public void testJacksonizedBuilderWithJsonDeserializeTest() {
    configureAndTest("""
                           <error descr="@JsonDeserialize already exists on class. Either delete @JsonDeserialize, or remove @Jacksonized and manually configure Jackson.">@lombok.extern.jackson.Jacksonized</error>
                           @lombok.Builder
                           @com.fasterxml.jackson.databind.annotation.JsonDeserialize
                           public class %s {
                             private String someField;
                           }
                       """);
  }
}
