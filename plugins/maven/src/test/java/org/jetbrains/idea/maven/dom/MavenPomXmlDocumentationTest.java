package org.jetbrains.idea.maven.dom;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.maven.testFramework.MavenDomTestCase;
import com.intellij.psi.PsiElement;
import org.junit.Test;

public class MavenPomXmlDocumentationTest extends MavenDomTestCase {
  @Test
  public void testDocumentation() {
    createProjectPom(
      """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
        <scm>
          <connection<caret>></connection>
        </scm>""");

    String expectedText =
      """
        Tag name:&nbsp;<b>connection</b><br>Description  :&nbsp;The source control management system URL
                    that describes the repository and how to connect to the
                    repository. For more information, see the
                    <a href="https://maven.apache.org/scm/scm-url-format.html">URL format</a>
                    and <a href="https://maven.apache.org/scm/scms-overview.html">list of supported SCMs</a>.
                    This connection is read-only.
                    <br><b>Default value is</b>: parent value [+ path adjustment] + (artifactId or project.directory property), or just parent value if
                    scm&apos;s <code>child.scm.connection.inherit.append.path="false"</code><br>Version  :&nbsp;4.0.0+""";

    PsiElement originalElement = getElementAtCaret(myProjectPom);
    DocumentationManager documentationManager = DocumentationManager.getInstance(myProject);
    PsiElement targetElement = documentationManager.findTargetElement(getEditor(), getTestPsiFile(), originalElement);

    DocumentationProvider provider = DocumentationManager.getProviderFromElement(targetElement);

    assert expectedText.replaceAll(" +", " ").equals(provider.generateDoc(targetElement, originalElement).replaceAll(" +", " "));
  }
}
