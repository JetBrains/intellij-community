package org.jetbrains.idea.maven.dom

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.maven.testFramework.MavenDomTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MavenPomXmlDocumentationTest : MavenDomTestCase() {
  override fun runInDispatchThread() = true

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

    val expectedText =

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

    val originalElement = getElementAtCaret(projectPom)
    val documentationManager = DocumentationManager.getInstance(project)
    val targetElement = documentationManager.findTargetElement(editor, testPsiFile, originalElement)

    val provider = DocumentationManager.getProviderFromElement(targetElement)

    assert(
      expectedText.replace(" +".toRegex(), " ") == provider.generateDoc(targetElement, originalElement)!!.replace(" +".toRegex(), " "))
  }
}
