package de.plushnikov.intellij.plugin.inspection;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokTestUtil.lombokInspectionFixture;

@TestApplication
public class JacksonizedInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lombokInspectionFixture(projectFixture, () -> new LombokInspection(), "/inspection/jacksonized");

  @Test
  public void testJacksonizedBuilderTest() {
    LombokTestUtil.configureAndTest(inspectionFixture.get(), """
                           @lombok.Builder
                           @lombok.extern.jackson.Jacksonized
                           public class %s {
                             private String someField;
                           }
                       """);
  }

  @Test
  public void testJacksonizedSuperBuilderTest() {
    LombokTestUtil.configureAndTest(inspectionFixture.get(), """
                           @lombok.experimental.SuperBuilder
                           @lombok.extern.jackson.Jacksonized
                           public class %s {
                             private String someField;
                           }
                       """);
  }

  @Test
  public void testJacksonizedWithoutBuilderTest() {
    LombokTestUtil.configureAndTest(inspectionFixture.get(), """
                           <warning descr="@Jacksonized requires @Builder or @SuperBuilder for it to mean anything.">@lombok.extern.jackson.Jacksonized</warning>
                           public class %s {
                             private String someField;
                           }
                       """);
  }

  @Test
  public void testJacksonizedBuilderAndSuperBuilderTest() {
    LombokTestUtil.configureAndTest(inspectionFixture.get(), """
                           <error descr="@Jacksonized cannot process both @Builder and @SuperBuilder on the same class.">@lombok.extern.jackson.Jacksonized</error>
                           @lombok.Builder
                           @lombok.experimental.SuperBuilder
                           public class %s {
                             private String someField;
                           }
                       """);
  }

  @Test
  public void testJacksonizedAbstractBuilderTest() {
    LombokTestUtil.configureAndTest(inspectionFixture.get(), """
                           <error descr="Builders on abstract classes cannot be @Jacksonized (the builder would never be used).">@lombok.extern.jackson.Jacksonized</error>
                           @lombok.Builder
                           public abstract class %s {
                             private String someField;
                           }
                       """);
  }

  @Test
  public void testJacksonizedBuilderWithJsonDeserializeTest() {
    LombokTestUtil.configureAndTest(inspectionFixture.get(), """
                           <error descr="@JsonDeserialize already exists on class. Either delete @JsonDeserialize, or remove @Jacksonized and manually configure Jackson.">@lombok.extern.jackson.Jacksonized</error>
                           @lombok.Builder
                           @com.fasterxml.jackson.databind.annotation.JsonDeserialize
                           public class %s {
                             private String someField;
                           }
                       """);
  }
}
