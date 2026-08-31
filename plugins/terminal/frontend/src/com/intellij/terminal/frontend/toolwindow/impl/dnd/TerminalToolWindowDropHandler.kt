package com.intellij.terminal.frontend.toolwindow.impl.dnd

import com.intellij.ide.DataManager
import com.intellij.ide.dnd.DnDDropHandler
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.ide.dnd.DnDSupport
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.terminal.frontend.dnd.TerminalDropData
import com.intellij.terminal.frontend.dnd.TerminalDroppedContentResolver.resolveFilePaths
import com.intellij.terminal.frontend.toolwindow.impl.TerminalFilePathHandler
import com.intellij.terminal.frontend.toolwindow.impl.createTerminalTab
import com.intellij.util.asDisposable
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo
import org.jetbrains.plugins.terminal.fus.TerminalTabOpeningWay
import java.nio.file.Path
import kotlin.io.path.isDirectory

/**
 * Handles drag-and-drop on the terminal tool window.
 *
 * Dropping a file or directory creates a new terminal tab whose working directory
 * is derived from the first dropped item.
 *
 * [com.intellij.terminal.frontend.view.impl.TerminalViewImpl] installs its own
 * drop handler [com.intellij.terminal.frontend.view.impl.dnd.TerminalViewDropHandler].
 * If the drop target is a [com.intellij.terminal.frontend.view.impl.TerminalViewImpl],
 * that handler processes the drop.
 *
 * Supports drops from Project View (PSI elements), native OS file managers, and plain-text drag sources
 */
internal object TerminalToolWindowDropHandler {
  fun install(window: ToolWindowEx, coroutineScope: CoroutineScope) {
    val handler = createDropHandler(window, coroutineScope)

    DnDSupport.createBuilder(window.decorator)
      .setDropHandler(handler)
      .setDisposableParent(coroutineScope.asDisposable())
      .enableAsNativeTarget()
      .disableAsSource()
      .install()
  }

  @VisibleForTesting
  fun createDropHandler(window: ToolWindowEx, coroutineScope: CoroutineScope): DnDDropHandler = DnDDropHandler { event ->
    val dataContext = event.resolveDataContextAtDropPoint() ?: return@DnDDropHandler

    val contentManager = dataContext.getData(PlatformDataKeys.TOOL_WINDOW_CONTENT_MANAGER) ?: return@DnDDropHandler
    val data = TerminalDropData(event)

    val openingWay = if (event.attachedObject is DnDNativeTarget.EventInfo) {
      TerminalTabOpeningWay.DND_FILE_TO_TOOLWINDOW_FROM_EXTERNAL_APP
    }
    else TerminalTabOpeningWay.DND_FILE_TO_TOOLWINDOW_FROM_IDE
    val fusInfo = TerminalStartupFusInfo(openingWay)

    coroutineScope.launch {
      val droppedFiles = resolveFilePaths(data, window.project.getEelDescriptor())

      val filePath = droppedFiles.firstOrNull() ?: return@launch
      if (!TerminalFilePathHandler.isSameEnvironment(filePath, window.project.getEelDescriptor())) {
        return@launch
      }

      val dir = getDirectory(filePath) ?: return@launch
      withContext(Dispatchers.EDT) {
        createTerminalTab(
          window.project,
          workingDirectory = dir.toString(),
          contentManager = contentManager,
          startupFusInfo = fusInfo
        )
      }
    }
  }

  private fun getDirectory(filePath: Path?): Path? {
    if (filePath == null) return null
    return if (filePath.isDirectory()) filePath else filePath.parent
  }

  fun DnDEvent.resolveDataContextAtDropPoint(): DataContext? {
    val handlerComponent = handlerComponent
    val point = point
    if (handlerComponent == null || point == null) return null

    val deepestComponent = UIUtil.getDeepestComponentAt(handlerComponent, point.x, point.y) ?: return null
    return DataManager.getInstance().getDataContext(deepestComponent)
  }
}
