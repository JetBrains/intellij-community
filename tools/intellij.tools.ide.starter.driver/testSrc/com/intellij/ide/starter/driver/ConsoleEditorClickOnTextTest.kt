package com.intellij.ide.starter.driver

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.buildToolWindow
import com.intellij.driver.sdk.ui.components.common.codeEditor
import com.intellij.driver.sdk.ui.components.common.editor
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.runToolWindow
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.ui.shouldContainText
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.junit5.newContext
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import com.intellij.ide.starter.sdk.JdkDownloaderFacade.jdk17
import com.intellij.ide.starter.utils.JarUtils
import com.intellij.tools.ide.starter.product.idea.ultimate.IdeaUltimate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ConsoleEditorClickOnTextTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun clickOnTextInRunConsole() {
    runWithProject("run-console", "src/ConsoleLines.java") {
      step("Run ConsoleLines") {
        codeEditor {
          goToPosition(2, 5)
          driver.invokeAction("RunClass", component = component)
        }
      }
      runToolWindow {
        editor {
          shouldContainText("console line 04", timeout = 2.minutes)
          clickOnEachText(listOf("console line 01", "console line 02", "console line 04"))
        }
      }
    }
  }

  @Test
  fun clickOnTextInBuildConsole() {
    runWithProject("build-console", "src/BrokenBuild.java") {
      step("Build the project") {
        codeEditor {
          driver.invokeAction("CompileDirty", component = component)
        }
      }
      buildToolWindow {
        editor {
          shouldContainText("location: class BrokenBuild", timeout = 3.minutes)
          clickOnEachText(listOf("variable undefinedValue", "cannot find symbol", "class BrokenBuild"))
        }
      }
    }
  }

  private fun runWithProject(projectName: String, fileToOpen: String, action: IdeaFrameUI.() -> Unit) {
    val projectDir = JarUtils.extractResource("console-editor-click/$projectName", tempDir)
    Starter.newContext(IdeInfo.IdeaUltimate) {
      project = LocalProjectInfo(projectDir)
    }.copyWithProjectSdk(jdk17.toSdk()).runIdeWithDriver(pauseOnIndicators = 5.minutes).useDriverAndCloseIde {
      openFile(fileToOpen)
      ideFrame(action)
    }
  }

  private fun JEditorUiComponent.clickOnEachText(targets: List<String>) {
    for (target in targets) {
      step("Click on '$target'") {
        clickOn(target)
        val expectedLine = getLineNumber(target)
        should("caret is on the line of '$target'", timeout = 5.seconds) { getCaretLine() == expectedLine }
      }
    }
  }
}
