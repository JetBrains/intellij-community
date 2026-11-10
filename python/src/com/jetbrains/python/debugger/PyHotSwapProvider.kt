// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.debugger.impl.rpc.HotSwapVisibleStatus
import com.intellij.util.ui.accessibility.AccessibleAnnouncerUtil
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.hotswap.HotSwapInDebugSessionEnabler
import com.intellij.xdebugger.hotswap.HotSwapProvider
import com.intellij.xdebugger.hotswap.HotSwapSession
import com.intellij.xdebugger.hotswap.SourceFileChangesCollector
import com.intellij.xdebugger.hotswap.SourceFileChangesListener
import com.intellij.xdebugger.impl.hotswap.HotSwapDebugSessionManager
import com.intellij.xdebugger.impl.hotswap.HotSwapUiExtension
import com.intellij.xdebugger.impl.hotswap.SourceFileChangeFilter
import com.intellij.xdebugger.impl.hotswap.SourceFileChangesCollectorImpl
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.run.AbstractServerPythonRunConfiguration
import kotlinx.coroutines.CoroutineScope
import javax.swing.Icon

/**
 * Tells the user that the Python debugger does not apply code changes to a running process,
 * and offers to restart the debug session.
 *
 * Only the sessions of a Python run configuration get the provider, because a restart reruns that configuration.
 * A server that reloads itself after a change gets no provider.
 */
internal class PyHotSwapInDebugSessionEnabler : HotSwapInDebugSessionEnabler {
  override fun createProvider(process: XDebugProcess): HotSwapProvider<*>? {
    if (process !is PyDebugProcessWithConsole) return null
    val runProfile = process.session.executionEnvironment?.runProfile as? AbstractPythonRunConfiguration<*> ?: return null
    if (runProfile is AbstractServerPythonRunConfiguration<*>) return null
    return PyHotSwapProvider(process)
  }
}

private class PyHotSwapProvider(private val process: XDebugProcess) : HotSwapProvider<VirtualFile> {
  override fun createChangesCollector(
    session: HotSwapSession<VirtualFile>,
    coroutineScope: CoroutineScope,
    listener: SourceFileChangesListener,
  ): SourceFileChangesCollector<VirtualFile> {
    return SourceFileChangesCollectorImpl(
      session.project,
      coroutineScope,
      RestartOnlyChangesListener(listener),
      filters = listOf(PythonSourceInProjectFilter(session.project)),
    )
  }

  /**
   * The Python debugger cannot reload code, so a restart is the only way to apply the changes.
   */
  override fun performHotSwap(session: HotSwapSession<VirtualFile>) {
    session.startHotSwapListening(showSuccessNotification = false).onFinish()
    restart()
  }

  override fun restart() {
    HotSwapDebugSessionManager.getInstance(process.session.project).restart(process)
  }
}

/**
 * Reports each change as incompatible, so that the floating toolbar suggests a restart instead of a hot swap.
 */
private class RestartOnlyChangesListener(private val delegate: SourceFileChangesListener) : SourceFileChangesListener by delegate {
  override fun onNewChanges() {
    delegate.onIncompatibleChanges(PyBundle.message("debugger.hotswap.restart.to.apply.changes"))
  }
}

private class PythonSourceInProjectFilter(private val project: Project) : SourceFileChangeFilter<VirtualFile> {
  override suspend fun isApplicable(change: VirtualFile): Boolean {
    return FileTypeRegistry.getInstance().isFileOfType(change, PythonFileType.INSTANCE) &&
           readAction { ProjectFileIndex.getInstance(project).isInContent(change) }
  }
}

/**
 * Gives the floating toolbar the Python texts in an IDE that has no other [HotSwapUiExtension].
 * It is registered last, so the extension of the IDE wins when the IDE has one.
 */
internal class PyHotSwapUiExtension : HotSwapUiExtension {
  override val hotSwapIcon: Icon
    get() = AllIcons.Actions.RestartDebugger

  override fun configureTooltip(tooltip: HelpTooltip, status: HotSwapVisibleStatus) {
    tooltip.setPlainTextTitle(XDebuggerBundle.message("xdebugger.hotswap.tooltip.restart"))
    tooltip.setDescription(HtmlChunk.text(PyBundle.message("debugger.hotswap.restart.to.apply.changes")))
  }

  override fun announceHotSwapStatus(project: Project, status: HotSwapVisibleStatus) {
    when (status) {
      is HotSwapVisibleStatus.ChangesNotHotSwappable -> AccessibleAnnouncerUtil.announce(null, status.reason, true)
      else -> Unit
    }
  }
}
