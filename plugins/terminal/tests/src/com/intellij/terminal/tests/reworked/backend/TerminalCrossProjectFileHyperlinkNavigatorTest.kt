// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.backend

import com.intellij.execution.filters.FileHyperlinkInfo
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.hyperlinks.SourceNavigationProjectRouter
import org.jetbrains.plugins.terminal.hyperlinks.TerminalCrossProjectFileHyperlinkNavigator
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

@TestApplication
class TerminalCrossProjectFileHyperlinkNavigatorTest {
  @Test
  fun fileHyperlinkUsesSourceNavigationProjectPathAtClickTime() {
    runBlocking(Dispatchers.Default) {
      val currentProject = ProjectManager.getInstance().defaultProject
      val targetProject = ProjectManager.getInstance().defaultProject
      val file = LightVirtualFile("Main.kt", "fun main() {}")
      val hyperlinkInfo = TestFileHyperlinkInfo(OpenFileDescriptor(currentProject, file, 4, 2))
      var sourceProjectPath = "/tmp/source-project-a"

      var openedPath: String? = null
      var focusCalls = 0
      var capturedDescriptor: OpenFileDescriptor? = null
      val navigator = TerminalCrossProjectFileHyperlinkNavigator(
        sourceNavigationProjectPath = { sourceProjectPath },
        openProject = { path ->
          openedPath = path
          targetProject
        },
        focusProjectWindow = {
          focusCalls++
        },
        navigate = { _, descriptor, _ ->
          capturedDescriptor = descriptor
          true
        },
      )

      sourceProjectPath = "/tmp/source-project-b"
      val handled = navigator.navigate(currentProject, hyperlinkInfo, null)

      assertThat(handled).isTrue()
      assertThat(openedPath).isEqualTo("/tmp/source-project-b")
      assertThat(focusCalls).isEqualTo(1)
      assertThat(capturedDescriptor).isNotNull()
      assertThat(capturedDescriptor?.file).isSameAs(file)
      assertThat(capturedDescriptor?.line).isEqualTo(4)
      assertThat(capturedDescriptor?.column).isEqualTo(2)
      assertThat(hyperlinkInfo.navigateCalls).isZero()
    }
  }

  @Test
  fun blankSourceNavigationProjectPathReturnsFalse() {
    runBlocking(Dispatchers.Default) {
      val project = ProjectManager.getInstance().defaultProject
      val hyperlinkInfo = TestFileHyperlinkInfo(OpenFileDescriptor(project, LightVirtualFile("Main.kt", "fun main() {}"), 1, 0))
      var openCalls = 0
      val navigator = TerminalCrossProjectFileHyperlinkNavigator(
        sourceNavigationProjectPath = { "" },
        openProject = {
          openCalls++
          project
        },
      )

      val handled = navigator.navigate(project, hyperlinkInfo, null)

      assertThat(handled).isFalse()
      assertThat(openCalls).isZero()
      assertThat(hyperlinkInfo.navigateCalls).isZero()
    }
  }

  @Test
  fun missingDescriptorReturnsFalse() {
    runBlocking(Dispatchers.Default) {
      val project = ProjectManager.getInstance().defaultProject
      val hyperlinkInfo = TestFileHyperlinkInfo(null)
      val navigator = TerminalCrossProjectFileHyperlinkNavigator(
        sourceNavigationProjectPath = { "/tmp/source-project" },
      )

      val handled = navigator.navigate(project, hyperlinkInfo, null)

      assertThat(handled).isFalse()
      assertThat(hyperlinkInfo.navigateCalls).isZero()
    }
  }

  @Test
  fun offsetDescriptorIsPreservedWhenRerouted() {
    runBlocking(Dispatchers.Default) {
      val currentProject = ProjectManager.getInstance().defaultProject
      val targetProject = ProjectManager.getInstance().defaultProject
      val file = LightVirtualFile("Main.kt", "fun main() {\n  println()\n}")
      val hyperlinkInfo = TestFileHyperlinkInfo(OpenFileDescriptor(currentProject, file, 12))
      var capturedDescriptor: OpenFileDescriptor? = null
      val navigator = TerminalCrossProjectFileHyperlinkNavigator(
        sourceNavigationProjectPath = { "/tmp/source-project" },
        openProject = { targetProject },
        focusProjectWindow = {},
        navigate = { _, descriptor, _ ->
          capturedDescriptor = descriptor
          true
        },
      )

      val handled = navigator.navigate(currentProject, hyperlinkInfo, null)

      assertThat(handled).isTrue()
      assertThat(capturedDescriptor?.offset).isEqualTo(12)
      assertThat(capturedDescriptor?.line).isEqualTo(-1)
      assertThat(capturedDescriptor?.column).isEqualTo(-1)
    }
  }

  @Test
  fun lineAndColumnDescriptorIsPreservedWhenOffsetIsAbsent() {
    runBlocking(Dispatchers.Default) {
      val currentProject = ProjectManager.getInstance().defaultProject
      val targetProject = ProjectManager.getInstance().defaultProject
      val file = LightVirtualFile("Main.kt", "fun main() {\n  println()\n}")
      val hyperlinkInfo = TestFileHyperlinkInfo(OpenFileDescriptor(currentProject, file, 1, 2))
      var capturedDescriptor: OpenFileDescriptor? = null
      val navigator = TerminalCrossProjectFileHyperlinkNavigator(
        sourceNavigationProjectPath = { "/tmp/source-project" },
        openProject = { targetProject },
        focusProjectWindow = {},
        navigate = { _, descriptor, _ ->
          capturedDescriptor = descriptor
          true
        },
      )

      val handled = navigator.navigate(currentProject, hyperlinkInfo, null)

      assertThat(handled).isTrue()
      assertThat(capturedDescriptor?.line).isEqualTo(1)
      assertThat(capturedDescriptor?.column).isEqualTo(2)
      assertThat(capturedDescriptor?.offset).isNotNegative()
    }
  }

  @Test
  fun navigationRequestFocusesTargetWindowBeforeOpeningDescriptor() {
    runBlocking(Dispatchers.Default) {
      val currentProject = ProjectManager.getInstance().defaultProject
      val targetProject = ProjectManager.getInstance().defaultProject
      val hyperlinkInfo = TestFileHyperlinkInfo(OpenFileDescriptor(currentProject, LightVirtualFile("Main.kt", "fun main() {}"), 1, 0))
      val steps = mutableListOf<String>()
      val navigator = TerminalCrossProjectFileHyperlinkNavigator(
        sourceNavigationProjectPath = { "/tmp/source-project" },
        openProject = {
          steps += "open"
          targetProject
        },
        focusProjectWindow = {
          steps += "focus"
        },
        navigate = { _, _, _ ->
          steps += "navigate"
          true
        },
      )

      val handled = navigator.navigate(currentProject, hyperlinkInfo, null)

      assertThat(handled).isTrue()
      assertThat(steps).containsExactly("open", "focus", "navigate")
    }
  }

  @Test
  fun navigationReturnsFalseWhenFileBecomesInvalidBeforeTargetDescriptorIsBuilt() {
    runBlocking(Dispatchers.Default) {
      val currentProject = ProjectManager.getInstance().defaultProject
      val file = TestInvalidatableVirtualFile("Main.kt", "fun main() {}")
      val hyperlinkInfo = TestFileHyperlinkInfo(OpenFileDescriptor(currentProject, file, 4))
      var navigateCalls = 0
      val navigator = TerminalCrossProjectFileHyperlinkNavigator(
        sourceNavigationProjectPath = { "/tmp/source-project" },
        openProject = {
          yield()
          file.isStillValid = false
          currentProject
        },
        focusProjectWindow = {},
        navigate = { _, _, _ ->
          navigateCalls++
          true
        },
      )

      val handled = navigator.navigate(currentProject, hyperlinkInfo, null)

      assertThat(handled).isFalse()
      assertThat(navigateCalls).isZero()
    }
  }

  @Test
  fun browserFallbackPreferenceIsPassedToTargetNavigation() {
    runBlocking(Dispatchers.Default) {
      val currentProject = ProjectManager.getInstance().defaultProject
      val targetProject = ProjectManager.getInstance().defaultProject
      val file = LightVirtualFile("Main.kt", "fun main() {}")
      val hyperlinkInfo = OpenFileHyperlinkInfo(currentProject, file, 1, 0, false)
      var capturedUseBrowser: Boolean? = null
      val navigator = TerminalCrossProjectFileHyperlinkNavigator(
        sourceNavigationProjectPath = { "/tmp/source-project" },
        openProject = { targetProject },
        focusProjectWindow = {},
        navigate = { _, _, useBrowser ->
          capturedUseBrowser = useBrowser
          true
        },
      )

      val handled = navigator.navigate(currentProject, hyperlinkInfo, null)

      assertThat(handled).isTrue()
      assertThat(capturedUseBrowser).isFalse()
    }
  }

  @Test
  fun canonicalManagedPathIsUsedForDirectOpenProjectReuse() {
    runBlocking(Dispatchers.Default) {
      val router = testRouter(
        managedProjectPath = { path ->
          if (path == Path.of("/repo")) "/repo/sample.ipr" else path.toString()
        },
        openProjects = listOf("open-project"),
        projectIdentityPath = { "/repo/sample.ipr" },
        isPathEquivalent = { _, _ -> false },
        openProjectByPath = { _, _ -> error("should reuse the already open project") },
      )

      val project = router.openOrReuseProject("/repo")

      assertThat(project).isEqualTo("open-project")
    }
  }

  @Test
  fun pathEquivalenceFallbackReusesOpenProjectWhenManagedPathDiffers() {
    runBlocking(Dispatchers.Default) {
      val router = testRouter(
        managedProjectPath = { _ -> "/repo/from-manager.ipr" },
        openProjects = listOf("open-project"),
        projectIdentityPath = { "/repo/other.ipr" },
        isPathEquivalent = { _, path -> path == Path.of("/repo") },
        openProjectByPath = { _, _ -> error("should reuse the already open project") },
      )

      val project = router.openOrReuseProject("/repo")

      assertThat(project).isEqualTo("open-project")
    }
  }

  @Test
  fun canonicalManagedPathIsUsedWhenOpeningClosedProject() {
    runBlocking(Dispatchers.Default) {
      val openedPath = AtomicReference<Path?>(null)
      val router = testRouter(
        managedProjectPath = { path ->
          if (path == Path.of("/repo")) "/repo/sample.ipr" else path.toString()
        },
        openProjects = emptyList(),
        projectIdentityPath = { error("no open projects expected") },
        isPathEquivalent = { _, _ -> false },
        openProjectByPath = { path, _ ->
          openedPath.set(path)
          "opened-project"
        },
      )

      val project = router.openOrReuseProject("/repo")

      assertThat(project).isEqualTo("opened-project")
      assertThat(openedPath.get()).isEqualTo(Path.of("/repo/sample.ipr"))
    }
  }
}

private fun testRouter(
  managedProjectPath: (Path) -> String?,
  openProjects: List<String>,
  projectIdentityPath: (String) -> String?,
  isPathEquivalent: (String, Path) -> Boolean,
  openProjectByPath: suspend (Path, OpenProjectTask) -> String?,
): SourceNavigationProjectRouter<String> {
  return SourceNavigationProjectRouter(
    parsePath = { normalizedPath -> Path.of(normalizedPath) },
    normalizePath = { it },
    resolveManagedPath = managedProjectPath,
    openProjectsProvider = { openProjects },
    projectIdentityPath = projectIdentityPath,
    isPathEquivalent = isPathEquivalent,
    openProjectByPath = openProjectByPath,
  )
}

private open class TestFileHyperlinkInfo(private val openFileDescriptor: OpenFileDescriptor?) : FileHyperlinkInfo {
  var navigateCalls: Int = 0
    private set

  override fun getDescriptor(): OpenFileDescriptor? = openFileDescriptor

  override fun navigate(project: Project) {
    navigateCalls++
  }
}

private class TestInvalidatableVirtualFile(name: String, content: String) : LightVirtualFile(name, content) {
  var isStillValid: Boolean = true

  override fun isValid(): Boolean = isStillValid
}
