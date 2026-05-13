package org.intellij.plugins.markdown.extensions

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

internal class MarkdownCodeSpanConfigurationRunnerTest : LightJavaCodeInsightFixtureTestCase() {

  fun `test code span run marker is shown for Java class with main method`() {
    addJavaClassWithMainMethod("JavaClass")
    configureMarkdown("`JavaClass`")

    val markers = findRunMarkers()
    assertEquals(1, markers.size)
    assertEquals(1, getPopupActionCount(markers))
  }

  fun `test code span run marker is not shown for absent Java class`() {
    configureMarkdown("`JavaClass`")

    val markers = findRunMarkers()
    assertEmpty(markers)
  }

  fun `test duplicate code spans on one line produce one Java run action`() {
    addJavaClassWithMainMethod("JavaClass")
    configureMarkdown("`JavaClass` `JavaClass` `JavaClass`")

    val markers = findRunMarkers()
    assertEquals(1, markers.size)
    assertEquals(1, getPopupActionCount(markers))
  }

  fun `test different code spans on one line produce separate Java run actions`() {
    addJavaClassWithMainMethod("JavaClass1")
    addJavaClassWithMainMethod("JavaClass2")
    configureMarkdown("`JavaClass1` `JavaClass2`")

    val markers = findRunMarkers()
    assertEquals(1, markers.size)
    assertEquals(2, getPopupActionCount(markers))
  }

  private fun configureMarkdown(text: String) {
    myFixture.configureByText("a.md", text)
    myFixture.checkHighlighting()
  }

  private fun addJavaClassWithMainMethod(name: String) {
    myFixture.addClass("""
      public class $name {
        static void main(String[] args) {}
      }
    """.trimIndent())
  }

  private fun findRunMarkers(): List<LineMarkerInfo<*>> {
    return DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.editor.document, project)
      .filter { it.icon == AllIcons.RunConfigurations.TestState.Run_run }
  }

  private fun getPopupActionCount(markers: List<LineMarkerInfo<*>>): Int {
    val renderer = markers.single().createGutterRenderer() as GutterIconRenderer
    return (renderer.popupMenuActions as? DefaultActionGroup)?.childActionsOrStubs?.count { it !is Separator } ?: 0
  }
}
