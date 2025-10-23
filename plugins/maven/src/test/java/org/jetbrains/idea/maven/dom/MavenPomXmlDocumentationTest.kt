package org.jetbrains.idea.maven.dom

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.maven.testFramework.MavenDomTestCase
import com.intellij.openapi.application.readAction
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MavenPomXmlDocumentationTest : MavenDomTestCase() {
  @Test
  fun testDocumentation() = runBlocking {
    createProjectPom(
      """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
        <scm>
          <connection<caret>></connection>
        </scm>
        """.trimIndent())

    val expectedText_4_0 =

      """
        Tag name:&nbsp;<b>connection</b><br>Description  :&nbsp;The source control management system URL
                    that describes the repository and how to connect to the
                    repository. For more information, see the
                    <a href="https://maven.apache.org/scm/scm-url-format.html">URL format</a>
                    and <a href="https://maven.apache.org/scm/scms-overview.html">list of supported SCMs</a>.
                    This connection is read-only.
                    <br><b>Default value is</b>: parent value [+ path adjustment] + (artifactId or project.directory property), or just parent value if
                    scm&apos;s <code>child.scm.connection.inherit.append.path="false"</code><br>Version  :&nbsp;4.0.0+
                    """.trimIndent()

    val expectedText_4_1 =

      """Tag name:&nbsp;<b>connection</b><br>Description :&nbsp;The source control management system URL
 that describes the repository and how to connect to the
 repository.
 This connection is read-only.
 &lt;br&gt;&lt;b&gt;Default value is&lt;/b&gt;: parent value [+ path adjustment] + (artifactId or project.directory property), or just parent value if
 scm&apos;s {@code child.scm.connection.inherit.append.path=&quot;false&quot;}
 @see &lt;a href=&quot;https://maven.apache.org/scm/scm-url-format.html&quot;&gt;URL format&lt;/a&gt;
 @see &lt;a href=&quot;https://maven.apache.org/scm/scms-overview.html&quot;&gt;list of supported SCMs&lt;/a&gt;<br>Version :&nbsp;4.0.0+
                    """.trimIndent()

    configTest(projectPom)
    val originalElement = getElementAtCaret(projectPom)
    val documentationManager = DocumentationManager.getInstance(project)

    val generatedText = readAction {
      val targetElement = documentationManager.findTargetElement(fixture.editor, fixture.file, originalElement)
      val provider = DocumentationManager.getProviderFromElement(targetElement)
      provider.generateDoc(targetElement, originalElement)
    }


    forModel40{
      assertEquals(expectedText_4_0.replace(" +".toRegex(), " "), generatedText!!.replace(" +".toRegex(), " "))
    }
    forModel41 {
      assertEquals(expectedText_4_1.replace(" +".toRegex(), " "), generatedText!!.replace(" +".toRegex(), " "))

    }

  }
}
