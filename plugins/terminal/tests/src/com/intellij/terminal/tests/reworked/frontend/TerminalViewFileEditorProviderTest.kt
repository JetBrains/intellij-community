package com.intellij.terminal.tests.reworked.frontend

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.terminal.TerminalTitle
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.session.TerminalStartupOptions
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellIntegration
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import javax.swing.JPanel

@TestApplication
internal class TerminalViewFileEditorProviderTest {
  private companion object {
    val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val project: Project
    get() = projectFixture.get()

  @Test
  fun `provider reuses terminal view after closing flag is cleared before delayed reopen`(@TestDisposable disposable: Disposable) {
    val terminalView = createTerminalView(disposable)
    val file = createTerminalViewVirtualFile(terminalView)
    val provider = createTerminalViewFileEditorProvider()

    file.putUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN, true)
    try {
      val originalEditor = provider.createEditor(project, file)
      try {
        assertTerminalViewFileEditor(originalEditor)
      }
      finally {
        Disposer.dispose(originalEditor)
      }

      file.putUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN, null)

      val reopenedEditor = provider.createEditor(project, file)
      try {
        assertTerminalViewFileEditor(reopenedEditor)
        assertThat(reopenedEditor.component).isSameAs(terminalView.component)
        assertThat(terminalView.coroutineScope.isActive).isTrue()
      }
      finally {
        file.putUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN, true)
        Disposer.dispose(reopenedEditor)
      }
    }
    finally {
      file.putUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN, null)
    }
  }

  @Test
  fun `provider reuses terminal view across repeated editor reopens`(@TestDisposable disposable: Disposable) {
    val terminalView = createTerminalView(disposable)
    val file = createTerminalViewVirtualFile(terminalView)
    val provider = createTerminalViewFileEditorProvider()

    try {
      repeat(10) {
        file.putUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN, true)
        val reopenedEditor = provider.createEditor(project, file)
        try {
          assertTerminalViewFileEditor(reopenedEditor)
          assertThat(reopenedEditor.component).isSameAs(terminalView.component)
        }
        finally {
          Disposer.dispose(reopenedEditor)
        }

        assertThat(terminalView.coroutineScope.isActive).isTrue()

        file.putUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN, null)
      }
    }
    finally {
      file.putUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN, null)
    }
  }

  private fun assertTerminalViewFileEditor(editor: FileEditor) {
    assertThat(editor.javaClass.name).isEqualTo("com.intellij.terminal.frontend.editor.TerminalViewFileEditor")
  }

  private fun createTerminalViewVirtualFile(terminalView: TerminalView): VirtualFile {
    val fileClass = Class.forName("com.intellij.terminal.frontend.editor.TerminalViewVirtualFile")
    val constructor = fileClass.getDeclaredConstructor(TerminalView::class.java, Boolean::class.javaPrimitiveType)
    constructor.isAccessible = true
    return constructor.newInstance(terminalView, false) as VirtualFile
  }

  private fun createTerminalViewFileEditorProvider(): FileEditorProvider {
    val providerClass = Class.forName("com.intellij.terminal.frontend.editor.TerminalViewFileEditorProvider")
    val constructor = providerClass.getDeclaredConstructor()
    constructor.isAccessible = true
    return constructor.newInstance() as FileEditorProvider
  }

  private fun createTerminalView(disposable: Disposable): TerminalView {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    Disposer.register(disposable) { scope.cancel() }
    val component = JPanel()

    val title = TerminalTitle()
    title.change { defaultTitle = "Terminal" }

    return mock<TerminalView>().also { view ->
      whenever(view.coroutineScope).thenReturn(scope)
      whenever(view.component).thenReturn(component)
      whenever(view.preferredFocusableComponent).thenReturn(component)
      whenever(view.title).thenReturn(title)
      whenever(view.shellIntegrationDeferred).thenReturn(CompletableDeferred<TerminalShellIntegration>())
      whenever(view.startupOptionsDeferred).thenReturn(CompletableDeferred<TerminalStartupOptions>())
      whenever(view.sessionDeferred).thenReturn(CompletableDeferred<TerminalSession>())
    }
  }
}
