// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.vcs

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.Notifications
import com.intellij.notification.impl.NotificationGroupEP
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.tasks.TaskBundle
import com.intellij.tasks.context.BranchContextTracker
import com.intellij.tasks.context.WorkingContextManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.xmlb.XmlSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.writeText

private const val NOTIFICATION_GROUP_ID = "Branch Context group"

@TestApplication
class BranchContextTrackerTest {
  private val projectFixture = projectFixture(
    openProjectTask = OpenProjectTask {
      beforeInitTasks += { it.putUserData(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT, true) }
    },
    openAfterCreation = true,
  )
  private val fileEditorManagerFixture = projectFixture.fileEditorManagerFixture()
  private val tempPathFixture = tempPathFixture()

  private val project: Project get() = projectFixture.get()
  private val editorManager: FileEditorManagerImpl get() = fileEditorManagerFixture.get()

  @TestDisposable
  lateinit var disposable: Disposable

  @BeforeEach
  fun setUp() {
    val contextManager = WorkingContextManager.getInstance(project)
    contextManager.enableUntil(disposable)
    contextManager.contextFile.delete()
    if (!NotificationGroupManager.getInstance().isGroupRegistered(NOTIFICATION_GROUP_ID)) {
      val group = XmlSerializer.deserialize(
        JDOMUtil.load("""<notificationGroup id="$NOTIFICATION_GROUP_ID" displayType="BALLOON"/>"""),
        NotificationGroupEP::class.java,
      )
      ExtensionPointName<NotificationGroupEP>("com.intellij.notificationGroup").point.registerExtension(group, disposable)
    }
  }

  @Test
  fun fileOpenedDuringCheckoutStaysOpenAndRollbackReturnsToTheWorkspaceBeforeTheRestore(): Unit = runInEdtAndWait {
    val first = createFile("first.txt", "first")
    val second = createFile("second.txt", "second line")
    val third = createFile("third.txt", "third")
    val contextManager = WorkingContextManager.getInstance(project)

    // the workspace saved for the target branch holds `third.txt` only
    editorManager.openFile(third, true)
    contextManager.saveContext("__branch_context_b", null)
    assertThat(contextManager.hasContext("__branch_context_b")).isTrue()
    editorManager.closeAllFiles()

    editorManager.openFile(first, true)
    val tracker = BranchContextTracker(project)
    tracker.branchWillChange("a")

    // the file opened during the checkout, with a caret position that must survive
    editorManager.openTextEditor(OpenFileDescriptor(project, second, 4), true)

    var notification: Notification? = null
    project.messageBus.connect(disposable).subscribe(Notifications.TOPIC, object : Notifications {
      override fun notify(shown: Notification) {
        if (shown.groupId == NOTIFICATION_GROUP_ID) {
          notification = shown
        }
      }
    })

    tracker.branchHasChanged("b")
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    assertThat(openFileNames()).containsExactlyInAnyOrder("third.txt", "second.txt")
    assertThat(editorManager.currentFile).isEqualTo(second)
    assertThat(editorManager.selectedTextEditor!!.caretModel.offset).isEqualTo(4)

    val shown = notification ?: error("The restore notification was not shown")
    val rollback = shown.actions.single { it.templateText == TaskBundle.message("action.Anonymous.text.rollback") } as NotificationAction
    rollback.actionPerformed(TestActionEvent.createTestEvent(rollback), shown)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    assertThat(openFileNames()).containsExactlyInAnyOrder("first.txt", "second.txt")
    assertThat(shown.isExpired).isTrue()
  }

  private fun createFile(name: String, content: String): VirtualFile {
    val path: Path = tempPathFixture.get().resolve(name)
    path.writeText(content)
    return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: error("Cannot find $path")
  }

  private fun openFileNames(): List<String> = editorManager.openFiles.map { it.name }
}
