package org.intellij.plugins.markdown.extensions

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.impl.LineMarkersPass.getDisplayedLineMarkers
import com.intellij.execution.RunManager
import com.intellij.execution.application.ApplicationConfigurationType
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import javax.swing.Icon

@TestApplication
internal class MarkdownCodeSpanConfigurationRunnerTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun `test code span run marker is shown for existing run configuration`() {
    addRunConfiguration()
    configureMarkdown("`Smoke Tests Dev`")

    val markers = findRunMarkers(AllIcons.RunConfigurations.TestState.Run)
    assertEquals(1, markers.size)
    assertEquals(1, getPopupActionCount(markers))
  }

  @Test
  fun `test code span run marker is shown for Java class with main method`() {
    addJavaClassWithMainMethod("JavaClass")
    configureMarkdown("`JavaClass`")

    val markers = findRunMarkers()
    assertEquals(1, markers.size)
    assertEquals(1, getPopupActionCount(markers))
  }

  @Test
  fun `test code span run marker is not shown for non-runnable Java class`() {
    myFixture.addClass("public class JavaClass {}")
    configureMarkdown("`JavaClass`")

    val markers = findRunMarkers()
    assertThat(markers).isNullOrEmpty()
  }

  @Test
  fun `test duplicate code spans on one line produce one Java run action`() {
    addJavaClassWithMainMethod("JavaClass")
    configureMarkdown("`JavaClass` `JavaClass` `JavaClass`")

    val markers = findRunMarkers()
    assertEquals(1, markers.size)
    assertEquals(1, getPopupActionCount(markers))
  }

  @Test
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
    val runManager = RunManager.getInstance(myFixture.project)
    runManager.addConfiguration(
      runManager.createConfiguration("Smoke Tests Dev", ApplicationConfigurationType.getInstance().configurationFactories.single())
    )
  }

  private fun findRunMarkers(icon: Icon = AllIcons.RunConfigurations.TestState.Run_run): List<LineMarkerInfo<*>> {
    return getDisplayedLineMarkers(myFixture.editor.document, myFixture.project)
      .filter { it.icon == icon }
  }

  private fun getPopupActionCount(markers: List<LineMarkerInfo<*>>): Int {
    val renderer = markers.single().createGutterRenderer() as GutterIconRenderer
    return (renderer.popupMenuActions as? DefaultActionGroup)?.childActionsOrStubs?.count { it !is Separator } ?: 0
  }
}
