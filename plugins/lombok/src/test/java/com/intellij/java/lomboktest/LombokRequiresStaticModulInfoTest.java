package com.intellij.java.lomboktest;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class LombokRequiresStaticModulInfoTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testModulEntry() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final @Language("JAVA") String testFileData = """
         import lombok.Data;

         @Data
         public class Test {
          public static void main(String[] args){

          }
      }
      """;

    myFixture.configureByText("Test.java", testFileData);
    myFixture.addFileToProject("module-info.java", "module test { }");

    String actualPreview =
      myFixture.getIntentionPreviewText(myFixture.findSingleIntention("Add 'requires lombok' directive to module-info.java"));
    assertEquals("""
                   module test {
                       requires static lombok;
                   }""", actualPreview);
  }
}
