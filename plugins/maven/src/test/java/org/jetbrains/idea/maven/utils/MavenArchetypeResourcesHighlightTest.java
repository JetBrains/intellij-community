package org.jetbrains.idea.maven.utils;

import com.intellij.codeInsight.daemon.ProblemHighlightFilter;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import junit.framework.TestCase;
import org.junit.Test;

public class MavenArchetypeResourcesHighlightTest extends LightJavaCodeInsightFixtureTestCase {
  @Test
  public void testHighlight() throws Exception {
    PsiFile file = myFixture.addFileToProject("src/main/resources/archetype-resources/src/main/java/A.java", """
      import ${package};
      class A {
      }
      """);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    TestCase.assertFalse(ProblemHighlightFilter.shouldHighlightFile(file));

    file = myFixture.addFileToProject("src/main/resources/B.java", """
      import <error>$</error><error><error>{</error>package};</error>
      class B {
      }
      """);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.checkHighlighting();
  }
}
