package com.intellij.java.lomboktest;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

public class LombokRequiresStaticModulInfoTest extends LightJavaCodeInsightFixtureTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR;
  }

  public void testModulEntry() {
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
