package org.intellij.plugins.markdown.extensions

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.execution.RunManager
import com.intellij.execution.application.ApplicationConfigurationType
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import javax.swing.Icon

internal class MarkdownCodeSpanConfigurationRunnerTest : LightJavaCodeInsightFixtureTestCase() {

  fun `test code span run marker is shown for existing run configuration`() {
    addRunConfiguration()
    configureMarkdown("`Smoke Tests Dev`")

    val markers = findRunMarkers(AllIcons.RunConfigurations.TestState.Run)
    assertEquals(1, markers.size)
    assertEquals(1, getPopupActionCount(markers))
  }

  fun `test code span run marker is shown for Java class with main method`() {
    addJavaClassWithMainMethod("JavaClass")
    configureMarkdown("`JavaClass`")

    val markers = findRunMarkers()
    assertEquals(1, markers.size)
    assertEquals(1, getPopupActionCount(markers))
  }

  fun `test code span run marker is not shown for non-runnable Java class`() {
    myFixture.addClass("public class JavaClass {}")
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

  private fun addRunConfiguration() {
    val runManager = RunManager.getInstance(project)
    runManager.addConfiguration(
      runManager.createConfiguration("Smoke Tests Dev", ApplicationConfigurationType.getInstance().configurationFactories.single())
    )
  }

  private fun findRunMarkers(icon: Icon = AllIcons.RunConfigurations.TestState.Run_run): List<LineMarkerInfo<*>> {
    return DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.editor.document, project)
      .filter { it.icon == icon }
  }

  private fun getPopupActionCount(markers: List<LineMarkerInfo<*>>): Int {
    val renderer = markers.single().createGutterRenderer() as GutterIconRenderer
    return (renderer.popupMenuActions as? DefaultActionGroup)?.childActionsOrStubs?.count { it !is Separator } ?: 0
  }
}
