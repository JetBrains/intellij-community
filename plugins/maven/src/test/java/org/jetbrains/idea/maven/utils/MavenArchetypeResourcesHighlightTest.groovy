package org.jetbrains.idea.maven.utils

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.psi.PsiFile

/**
 * @author Sergey Evdokimov
 */
class MavenArchetypeResourcesHighlightTest extends LightCodeInsightFixtureTestCase {

  public void testHighlight() throws Exception {
    PsiFile file = myFixture.addFileToProject("src/main/resources/archetype-resources/src/main/java/A.java", """
import \${package};
class A {
}
""");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.checkHighlighting();

    file = myFixture.addFileToProject("src/main/resources/B.java", """
import <error>\$</error><error><error>{</error>package};</error>
class B {
}
""");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.checkHighlighting();
  }

}

