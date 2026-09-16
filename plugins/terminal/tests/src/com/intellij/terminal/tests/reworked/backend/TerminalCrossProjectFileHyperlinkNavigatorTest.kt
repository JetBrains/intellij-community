// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.backend

import com.intellij.execution.filters.FileHyperlinkInfo
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.mock.MockProjectEx
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.hyperlinks.TerminalCrossProjectFileHyperlinkNavigator
import org.jetbrains.plugins.terminal.hyperlinks.TerminalSourceNavigationProjectResolver
import org.junit.jupiter.api.Test

@TestApplication
class TerminalCrossProjectFileHyperlinkNavigatorTest {
  @Test
  fun fileHyperlinkAsksTheResolverAtClickTimeAndNavigatesInItsProject() {
    withTerminalProject { terminalProject ->
      val targetProject = ProjectManager.getInstance().defaultProject
      val file = LightVirtualFile("Main.kt", "fun main() {}")
      val hyperlinkInfo = TestFileHyperlinkInfo(OpenFileDescriptor(targetProject, file, 4, 2))
      var resolverCalls = 0
      var askedTerminalProject: Project? = null
      var focusCalls = 0
      var navigatedProject: Project? = null
      var capturedDescriptor: OpenFileDescriptor? = null
      val navigator = TerminalCrossProjectFileHyperlinkNavigator(
        sourceNavigationProjectResolver = {
          TerminalSourceNavigationProjectResolver { askedProject ->
            resolverCalls++
            askedTerminalProject = askedProject
            targetProject
          }
        },
        focusProjectWindow = {
          focusCalls++
        },
        navigate = { project, descriptor, _ ->
          navigatedProject = project
          capturedDescriptor = descriptor
          true
        },
      )

      val handled = navigator.navigate(terminalProject, hyperlinkInfo, null)

      assertThat(handled).isTrue()
      assertThat(resolverCalls).isEqualTo(1)
      assertThat(askedTerminalProject).isSameAs(terminalProject)
      assertThat(navigatedProject).isSameAs(targetProject)
      assertThat(focusCalls).isEqualTo(1)
      assertThat(capturedDescriptor?.file).isSameAs(file)
      assertThat(capturedDescriptor?.line).isEqualTo(4)
      assertThat(capturedDescriptor?.column).isEqualTo(2)
      assertThat(hyperlinkInfo.navigateCalls).isZero()
    }
  }

  @Test
  fun resolverThatAnswersTheTerminalProjectReturnsFalse() {
    runBlocking(Dispatchers.Default) {
      val terminalProject = ProjectManager.getInstance().defaultProject
      val file = LightVirtualFile("Main.kt", "fun main() {}")
      val hyperlinkInfo = TestFileHyperlinkInfo(OpenFileDescriptor(terminalProject, file, 4, 2))
      var focusCalls = 0
      var navigateCalls = 0
      val navigator = TerminalCrossProjectFileHyperlinkNavigator(
        sourceNavigationProjectResolver = { TerminalSourceNavigationProjectResolver { terminalProject } },
        focusProjectWindow = {
          focusCalls++
        },
        navigate = { _, _, _ ->
          navigateCalls++
          true
        },
      )

      val handled = navigator.navigate(terminalProject, hyperlinkInfo, null)

      assertThat(handled).isFalse()
      assertThat(focusCalls).isZero()
      assertThat(navigateCalls).isZero()
      assertThat(hyperlinkInfo.navigateCalls).isZero()
    }
  }

  @Test
  fun absentResolverReturnsFalse() {
    runBlocking(Dispatchers.Default) {
      val project = ProjectManager.getInstance().defaultProject
      val hyperlinkInfo = TestFileHyperlinkInfo(OpenFileDescriptor(project, LightVirtualFile("Main.kt", "fun main() {}"), 1, 0))
      val navigator = TerminalCrossProjectFileHyperlinkNavigator(
        sourceNavigationProjectResolver = { null },
      )

      val handled = navigator.navigate(project, hyperlinkInfo, null)

      assertThat(handled).isFalse()
      assertThat(hyperlinkInfo.navigateCalls).isZero()
    }
  }

  @Test
  fun resolverThatAnswersNoProjectReturnsFalse() {
    withTerminalProject { terminalProject ->
      val descriptorProject = ProjectManager.getInstance().defaultProject
      val hyperlinkInfo = TestFileHyperlinkInfo(
        OpenFileDescriptor(descriptorProject, LightVirtualFile("Main.kt", "fun main() {}"), 1, 0)
      )
      var navigateCalls = 0
      val navigator = TerminalCrossProjectFileHyperlinkNavigator(
        sourceNavigationProjectResolver = { TerminalSourceNavigationProjectResolver { null } },
        focusProjectWindow = {},
        navigate = { _, _, _ ->
          navigateCalls++
          true
        },
      )

      val handled = navigator.navigate(terminalProject, hyperlinkInfo, null)

      assertThat(handled).isFalse()
      assertThat(navigateCalls).isZero()
      assertThat(hyperlinkInfo.navigateCalls).isZero()
    }
  }

  @Test
  fun nonFileHyperlinkReturnsFalse() {
    withTerminalProject { terminalProject ->
      val targetProject = ProjectManager.getInstance().defaultProject
      var navigateCalls = 0
      val navigator = TerminalCrossProjectFileHyperlinkNavigator(
        sourceNavigationProjectResolver = { TerminalSourceNavigationProjectResolver { targetProject } },
        focusProjectWindow = {},
        navigate = { _, _, _ ->
          navigateCalls++
          true
        },
      )

      val handled = navigator.navigate(terminalProject, TestPlainHyperlinkInfo(), null)

      assertThat(handled).isFalse()
      assertThat(navigateCalls).isZero()
    }
  }

  @Test
  fun missingDescriptorReturnsFalse() {
    withTerminalProject { terminalProject ->
      val targetProject = ProjectManager.getInstance().defaultProject
      val hyperlinkInfo = TestFileHyperlinkInfo(null)
      val navigator = TerminalCrossProjectFileHyperlinkNavigator(
        sourceNavigationProjectResolver = { TerminalSourceNavigationProjectResolver { targetProject } },
      )

      val handled = navigator.navigate(terminalProject, hyperlinkInfo, null)

      assertThat(handled).isFalse()
      assertThat(hyperlinkInfo.navigateCalls).isZero()
    }
  }

  @Test
  fun offsetDescriptorIsPreservedWhenRerouted() {
    withTerminalProject { terminalProject ->
      val targetProject = ProjectManager.getInstance().defaultProject
      val file = LightVirtualFile("Main.kt", "fun main() {\n  println()\n}")
      val hyperlinkInfo = TestFileHyperlinkInfo(OpenFileDescriptor(targetProject, file, 12))
      var capturedDescriptor: OpenFileDescriptor? = null
      val navigator = TerminalCrossProjectFileHyperlinkNavigator(
        sourceNavigationProjectResolver = { TerminalSourceNavigationProjectResolver { targetProject } },
        focusProjectWindow = {},
        navigate = { _, descriptor, _ ->
          capturedDescriptor = descriptor
          true
        },
      )

      val handled = navigator.navigate(terminalProject, hyperlinkInfo, null)

      assertThat(handled).isTrue()
      assertThat(capturedDescriptor?.offset).isEqualTo(12)
      assertThat(capturedDescriptor?.line).isEqualTo(-1)
      assertThat(capturedDescriptor?.column).isEqualTo(-1)
    }
  }

  @Test
  fun lineAndColumnDescriptorIsPreservedWhenOffsetIsAbsent() {
    withTerminalProject { terminalProject ->
      val targetProject = ProjectManager.getInstance().defaultProject
      val file = LightVirtualFile("Main.kt", "fun main() {\n  println()\n}")
      val hyperlinkInfo = TestFileHyperlinkInfo(OpenFileDescriptor(targetProject, file, 1, 2))
      var capturedDescriptor: OpenFileDescriptor? = null
      val navigator = TerminalCrossProjectFileHyperlinkNavigator(
        sourceNavigationProjectResolver = { TerminalSourceNavigationProjectResolver { targetProject } },
        focusProjectWindow = {},
        navigate = { _, descriptor, _ ->
          capturedDescriptor = descriptor
          true
        },
      )

      val handled = navigator.navigate(terminalProject, hyperlinkInfo, null)

      assertThat(handled).isTrue()
      assertThat(capturedDescriptor?.line).isEqualTo(1)
      assertThat(capturedDescriptor?.column).isEqualTo(2)
      assertThat(capturedDescriptor?.offset).isNotNegative()
    }
  }

  @Test
  fun navigationRequestFocusesTargetWindowBeforeOpeningDescriptor() {
    withTerminalProject { terminalProject ->
      val targetProject = ProjectManager.getInstance().defaultProject
      val hyperlinkInfo = TestFileHyperlinkInfo(
        OpenFileDescriptor(targetProject, LightVirtualFile("Main.kt", "fun main() {}"), 1, 0)
      )
      val steps = mutableListOf<String>()
      val navigator = TerminalCrossProjectFileHyperlinkNavigator(
        sourceNavigationProjectResolver = {
          TerminalSourceNavigationProjectResolver {
            steps += "resolve"
            targetProject
          }
        },
        focusProjectWindow = {
          steps += "focus"
        },
        navigate = { _, _, _ ->
          steps += "navigate"
          true
        },
      )

      val handled = navigator.navigate(terminalProject, hyperlinkInfo, null)

      assertThat(handled).isTrue()
      assertThat(steps).containsExactly("resolve", "focus", "navigate")
    }
  }

  @Test
  fun navigationReturnsFalseWhenFileBecomesInvalidBeforeTargetDescriptorIsBuilt() {
    withTerminalProject { terminalProject ->
      val targetProject = ProjectManager.getInstance().defaultProject
      val file = TestInvalidatableVirtualFile("Main.kt", "fun main() {}")
      val hyperlinkInfo = TestFileHyperlinkInfo(OpenFileDescriptor(targetProject, file, 4))
      var navigateCalls = 0
      val navigator = TerminalCrossProjectFileHyperlinkNavigator(
        sourceNavigationProjectResolver = {
          TerminalSourceNavigationProjectResolver {
            yield()
            file.isStillValid = false
            targetProject
          }
        },
        focusProjectWindow = {},
        navigate = { _, _, _ ->
          navigateCalls++
          true
        },
      )

      val handled = navigator.navigate(terminalProject, hyperlinkInfo, null)

      assertThat(handled).isFalse()
      assertThat(navigateCalls).isZero()
    }
  }

  @Test
  fun browserFallbackPreferenceIsPassedToTargetNavigation() {
    withTerminalProject { terminalProject ->
      val targetProject = ProjectManager.getInstance().defaultProject
      val file = LightVirtualFile("Main.kt", "fun main() {}")
      val hyperlinkInfo = OpenFileHyperlinkInfo(targetProject, file, 1, 0, false)
      var capturedUseBrowser: Boolean? = null
      val navigator = TerminalCrossProjectFileHyperlinkNavigator(
        sourceNavigationProjectResolver = { TerminalSourceNavigationProjectResolver { targetProject } },
        focusProjectWindow = {},
        navigate = { _, _, useBrowser ->
          capturedUseBrowser = useBrowser
          true
        },
      )

      val handled = navigator.navigate(terminalProject, hyperlinkInfo, null)

      assertThat(handled).isTrue()
      assertThat(capturedUseBrowser).isFalse()
    }
  }
}

/**
 * Runs [action] with a stand-in for the terminal's own project.
 *
 * The navigator reads only the disposal state and the identity of that project, and the reroute needs a target
 * project that differs from it. The target of each test is the default project, which builds a real descriptor.
 */
private fun withTerminalProject(action: suspend (Project) -> Unit) {
  val disposable = Disposer.newDisposable("TerminalCrossProjectFileHyperlinkNavigatorTest")
  try {
    val terminalProject = MockProjectEx(disposable)
    runBlocking(Dispatchers.Default) {
      action(terminalProject)
    }
  }
  finally {
    Disposer.dispose(disposable)
  }
}

private open class TestFileHyperlinkInfo(private val openFileDescriptor: OpenFileDescriptor?) : FileHyperlinkInfo {
  var navigateCalls: Int = 0
    private set

  override fun getDescriptor(): OpenFileDescriptor? = openFileDescriptor

  override fun navigate(project: Project) {
    navigateCalls++
  }
}

private class TestPlainHyperlinkInfo : HyperlinkInfo {
  override fun navigate(project: Project) = Unit
}

private class TestInvalidatableVirtualFile(name: String, content: String) : LightVirtualFile(name, content) {
  var isStillValid: Boolean = true

  override fun isValid(): Boolean = isStillValid
}
