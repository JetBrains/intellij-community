package com.intellij.markdown.frontend.preview.accessor.impl

import com.intellij.ide.BrowserUtil
import com.intellij.ide.actions.OpenFileAction
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbModeBlockedFunctionality
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectForFile
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.platform.project.findProject
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.dto.MarkdownHeaderInfo
import org.intellij.plugins.markdown.service.MarkdownLinkOpenerRemoteApi
import org.intellij.plugins.markdown.settings.DocumentLinksSafeState
import org.intellij.plugins.markdown.ui.MarkdownNotifications
import org.intellij.plugins.markdown.ui.preview.accessor.MarkdownLinkOpener
import org.intellij.plugins.markdown.ui.preview.accessor.MarkdownLinkOpenerUtil
import org.intellij.plugins.markdown.ui.preview.accessor.MarkdownLinkOpenerUtil.findVirtualFile
import org.intellij.plugins.markdown.util.MarkdownDisposable
import java.net.URI
import java.net.URISyntaxException

internal class MarkdownLinkOpenerImpl(val coroutineScope: CoroutineScope) : MarkdownLinkOpener {
  @Deprecated("Use openLink(project, link, sourceFile) instead", replaceWith = ReplaceWith("openLink(project, link, sourceFile)"))
  override fun openLink(project: Project?, link: String) {
    val uri = createUri(link) ?: return
    if (tryOpenInEditorDeprecated(project, uri)) {
      return
    }
    coroutineScope.launch {
      openExternalLink(project, uri)
    }
  }

  override fun openLink(currentProject: Project?, link: String, containingFile: VirtualFile?) {
    coroutineScope.launch {
      val data = MarkdownLinkOpenerRemoteApi.Companion.getInstance().fetchLinkNavigationData(link, containingFile?.rpcId())
      val uri = createUri(data.uri) ?: return@launch
      if (uri.scheme != "file") {
        openExternalLink(currentProject, uri)
        return@launch
      }
      val project = currentProject ?: data.projectId?.findProject() ?: return@launch
      val fileToOpen = data.virtualFileId?.virtualFile() ?: return@launch
      val anchor = uri.fragment
      if (anchor == null) {
        withContext(Dispatchers.EDT) {
          runReadAction {
            OpenFileAction.Companion.openFile(fileToOpen, project)
          }
        }
        return@launch
      }
      processHeaders(anchor, project, data.headers)
    }
  }

  private suspend fun processHeaders(anchor: String, project: Project, headers: List<MarkdownHeaderInfo>?){
    if (headers == null) {
      DumbService.Companion.getInstance(project).showDumbModeNotificationForFunctionality(
        message = MarkdownBundle.message("markdown.dumb.mode.navigation.is.not.available.notification.text"),
        functionality = DumbModeBlockedFunctionality.ActionWithoutId
        )
      return
    }
    if (headers.size == 1) {
      withContext(Dispatchers.EDT) {
        runReadAction {
          MarkdownLinkOpenerUtil.navigateToHeader(project, headers.first())
        }
      }
      return
    }
    val point = obtainHeadersPopupPosition(project)
    if (point == null) {
      logger.warn("Failed to obtain screen point for showing popup")
      return
    }
    when {
      headers.isEmpty() -> showCannotNavigateNotification(project, anchor, point)
      headers.size > 1 ->  showHeadersPopup(project, headers, point)
    }
  }

  override fun isSafeLink(project: Project?, link: String): Boolean {
    val uri = createUri(link) ?: return false
    return isSafeUri(project, uri)
  }

  private fun isSafeUri(project: Project?, uri: URI): Boolean {
    val protocol = uri.scheme ?: return false
    if (project != null) {
      val safeLinksState = DocumentLinksSafeState.Companion.getInstance(project)
      return safeLinksState.isProtocolAllowed(protocol)
    }
    return DocumentLinksSafeState.Companion.isHttpScheme(protocol) && isLocalHost(uri.host)
  }

  @RequiresEdt
  private fun openExternalLink(project: Project?, uri: URI) {
    if (isSafeUri(project, uri)) {
      actuallyBrowseExternalLink(project, uri)
      return
    }
    if (showDialog(project, uri)) {
      actuallyBrowseExternalLink(project, uri)
    }
  }

  @RequiresEdt
  private fun showDialog(project: Project?, uri: URI): Boolean {
    val dialog = MessageDialogBuilder.Companion.yesNo(
      title = MarkdownBundle.message("markdown.browse.external.link.open.confirmation.dialog.title"),
      message = MarkdownBundle.message("markdown.browse.external.link.open.confirmation.dialog.text", uri)
    ).doNotAsk(createDoNotAskOption(project, uri))
    return dialog.ask(project)
  }

  @RequiresEdt
  private fun actuallyBrowseExternalLink(project: Project?, uri: URI) {
    try {
      BrowserUtil.browse(uri)
    } catch (exception: Throwable) {
      logger.warn("Failed to browse external link!", exception)
      MarkdownNotifications.showWarning(
        project,
        id = "markdown.links.external.open.failed",
        title = MarkdownBundle.message("markdown.browse.external.link.failed.notification.title"),
        message = MarkdownBundle.message("markdown.browse.external.link.failed.notification.content", uri),
      )
    }
  }

  private fun createDoNotAskOption(project: Project?, uri: URI): DoNotAskOption? {
    if (project == null) {
      return null
    }
    val protocol = uri.scheme
    if (protocol == null) {
      logger.error("Failed to obtain protocol for link: $uri")
      return null
    }
    return object: DoNotAskOption.Adapter() {
      override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
        if (isSelected) {
          DocumentLinksSafeState.Companion.getInstance(project).allowProtocol(protocol)
        }
      }

      override fun getDoNotShowMessage(): String {
        return MarkdownBundle.message("markdown.browse.external.link.open.confirmation.dialog.do.not.ask.again.text", protocol)
      }
    }
  }

  private fun tryOpenInEditorDeprecated(project: Project?, uri: URI): Boolean {
    if (uri.scheme != "file") {
      return false
    }
    return runReadAction {
      actuallyOpenInEditorDeprecated(project, uri)
    }
  }

  private fun actuallyOpenInEditorDeprecated(project: Project?, uri: URI): Boolean {
    val targetFile = uri.findVirtualFile() ?: return false
    @Suppress("NAME_SHADOWING")
    val project = project ?: guessProjectForFile(targetFile) ?: return false
    val anchor = uri.fragment
    if (anchor == null){
      coroutineScope.launch(Dispatchers.EDT) {
        OpenFileAction.Companion.openFile(targetFile, project)
      }
      return true
    }
    val headers = MarkdownLinkOpenerUtil.collectHeaders(project, anchor, targetFile)
    if (headers == null) {
      coroutineScope.launch {
        DumbService.Companion.getInstance(project).showDumbModeNotificationForFunctionality(
          message = MarkdownBundle.message("markdown.dumb.mode.navigation.is.not.available.notification.text"),
          functionality = DumbModeBlockedFunctionality.ActionWithoutId
        )
      }
      // Return true to prevent external navigation from happening
      return true
    }
    if (headers.size == 1) {
      coroutineScope.launch(Dispatchers.EDT) {
        MarkdownLinkOpenerUtil.navigateToHeader(project, headers.first())
      }
      return true
    }
    val point = obtainHeadersPopupPosition(project)
    if (point == null) {
      logger.warn("Failed to obtain screen point for showing popup")
      return false
    }
    coroutineScope.launch {
      when {
        headers.isEmpty() -> showCannotNavigateNotification(project, anchor, point)
        headers.size > 1 ->  showHeadersPopup(project, headers, point)
      }
    }
    return true
  }

  companion object {
    private val logger = logger<MarkdownLinkOpenerImpl>()

    fun createUri(link: String): URI? {
      return try {
        when {
          BrowserUtil.isAbsoluteURL(link) -> URI(link)
          else -> URI("http://$link")
        }
      } catch (exception: URISyntaxException) {
        logger.warn(exception)
        null
      }
    }

    private fun isLocalHost(hostName: String?): Boolean {
      return hostName == null ||
             hostName.startsWith("127.") ||
             hostName.endsWith(":1") ||
             com.intellij.util.io.isLocalHost(hostName, false, false)
    }

    private fun obtainHeadersPopupPosition(project: Project?): RelativePoint? {
      val frame = WindowManager.getInstance().getFrame(project)
      val mousePosition = frame?.mousePosition ?: return null
      return RelativePoint(frame, mousePosition)
    }

    private fun showCannotNavigateNotification(project: Project, anchor: String, point: RelativePoint) {
      val balloonBuilder = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(
        MarkdownBundle.message("markdown.navigate.to.header.no.headers", anchor),
        MessageType.WARNING,
        null
      )
      val balloon = balloonBuilder.createBalloon()
      Disposer.register(MarkdownDisposable.Companion.getInstance(project), balloon)
      balloon.show(point, Balloon.Position.below)
    }

    private fun showHeadersPopup(project: Project, headers: Collection<MarkdownHeaderInfo>, point: RelativePoint) {
      JBPopupFactory.getInstance().createListPopup(HeadersPopup(project, headers.toList())).show(point)
    }

    private class HeadersPopup(
      private val project: Project,
      headers: List<MarkdownHeaderInfo>
    ): BaseListPopupStep<MarkdownHeaderInfo>(MarkdownBundle.message("markdown.navigate.to.header"), headers) {
      override fun getTextFor(value: MarkdownHeaderInfo): String {
        return "${value.headerText} (${value.fileName}:${value.lineNumber})"
      }

      override fun onChosen(selectedValue: MarkdownHeaderInfo, finalChoice: Boolean): PopupStep<*> {
        return doFinalStep {
          MarkdownLinkOpenerUtil.navigateToHeader(project, selectedValue)
        }
      }
    }
  }
}