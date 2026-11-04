// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.backend

import com.intellij.execution.filters.FileHyperlinkInfo
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.mock.MockProjectEx
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.hyperlinks.TerminalFileHyperlinkDescriptors
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinkId
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksSessionId
import org.junit.jupiter.api.Test

private val SESSION_ID = TerminalHyperlinksSessionId(3)
private val HYPERLINK_ID = TerminalHyperlinkId(7)

/** The descriptor of a file hyperlink, read in the owner project of the hyperlinks session. */
@TestApplication
class TerminalFileHyperlinkDescriptorsTest {
  @Test
  fun aFileHyperlinkAnswersItsDescriptorFromTheOwnerProject(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val ownerProject = ProjectManager.getInstance().defaultProject
    val file = LightVirtualFile("Main.kt", "fun main() {}")
    val requests = mutableListOf<Triple<Project, TerminalHyperlinksSessionId, TerminalHyperlinkId>>()
    val descriptors = TerminalFileHyperlinkDescriptors { project, sessionId, hyperlinkId ->
      requests += Triple(project, sessionId, hyperlinkId)
      TestFileHyperlinkInfo(OpenFileDescriptor(ownerProject, file, 4, 2))
    }

    val descriptor = descriptors.descriptorOf(ownerProject, SESSION_ID, HYPERLINK_ID)

    assertThat(requests).containsExactly(Triple(ownerProject, SESSION_ID, HYPERLINK_ID))
    assertThat(descriptor?.file).isSameAs(file)
    assertThat(descriptor?.line).isEqualTo(4)
    assertThat(descriptor?.column).isEqualTo(2)
  }

  @Test
  fun aHyperlinkThatNamesNoFileAnswersNothing(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val descriptors = TerminalFileHyperlinkDescriptors { _, _, _ -> TestPlainHyperlinkInfo() }

    assertThat(descriptors.descriptorOf(ProjectManager.getInstance().defaultProject, SESSION_ID, HYPERLINK_ID)).isNull()
  }

  @Test
  fun aGoneHyperlinkAnswersNothing(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val descriptors = TerminalFileHyperlinkDescriptors { _, _, _ -> null }

    assertThat(descriptors.descriptorOf(ProjectManager.getInstance().defaultProject, SESSION_ID, HYPERLINK_ID)).isNull()
  }

  @Test
  fun anInvalidFileAnswersNothing(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val ownerProject = ProjectManager.getInstance().defaultProject
    val file = TestInvalidVirtualFile()
    val descriptors = TerminalFileHyperlinkDescriptors { _, _, _ -> TestFileHyperlinkInfo(OpenFileDescriptor(ownerProject, file, 1, 0)) }

    assertThat(descriptors.descriptorOf(ownerProject, SESSION_ID, HYPERLINK_ID)).isNull()
  }

  @Test
  fun aDisposedOwnerProjectReadsNoHyperlink(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val disposable = Disposer.newDisposable("TerminalFileHyperlinkDescriptorsTest")
    val ownerProject = MockProjectEx(disposable)
    Disposer.dispose(disposable)
    val descriptors = TerminalFileHyperlinkDescriptors { _, _, _ -> error("a disposed project reads no hyperlink") }

    assertThat(descriptors.descriptorOf(ownerProject, SESSION_ID, HYPERLINK_ID)).isNull()
  }
}

private class TestFileHyperlinkInfo(private val openFileDescriptor: OpenFileDescriptor?) : FileHyperlinkInfo {
  override fun getDescriptor(): OpenFileDescriptor? = openFileDescriptor

  override fun navigate(project: Project) = error("the descriptor read navigates nothing")
}

private class TestPlainHyperlinkInfo : HyperlinkInfo {
  override fun navigate(project: Project) = error("the descriptor read navigates nothing")
}

private class TestInvalidVirtualFile : LightVirtualFile("Gone.kt", "") {
  override fun isValid(): Boolean = false
}
