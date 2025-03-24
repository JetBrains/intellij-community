package org.intellij.plugins.markdown.frontend.preview.accessor.impl

import com.intellij.ide.BrowserUtil
import com.intellij.ide.actions.OpenFileAction
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbModeBlockedFunctionality
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.platform.project.findProject
import com.intellij.platform.project.projectId
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.PlatformUtils
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.io.isLocalHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.dto.MarkdownHeaderInfo
import org.intellij.plugins.markdown.service.MarkdownFrontendService
import org.intellij.plugins.markdown.service.MarkdownLinkOpenerRemoteApi
import org.intellij.plugins.markdown.settings.DocumentLinksSafeState
import org.intellij.plugins.markdown.ui.MarkdownNotifications
import org.intellij.plugins.markdown.ui.preview.accessor.MarkdownLinkOpener
import org.intellij.plugins.markdown.util.MarkdownDisposable
import java.net.URI
import java.net.URISyntaxException

internal class MarkdownLinkOpenerImpl(val coroutineScope: CoroutineScope) : MarkdownLinkOpener {
  override fun openLink(project: Project?, link: String, containingFile: VirtualFile?) {
    val uri = createUri(link, containingFile) ?: return
    if (tryOpenInEditor(project, uri)) {
      return
    }
    coroutineScope.launch {
      openExternalLink(project, uri)
    }
  }
  override fun isSafeLink(project: Project?, link: String): Boolean {
    val uri = createUri(link, null) ?: return false
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
    val dialog = MessageDialogBuilder.yesNo(
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

  private fun tryOpenInEditor(project: Project?, uri: URI): Boolean {
    if (uri.scheme != "file") {
      return false
    }
    return runReadAction {
      actuallyOpenInEditor(project, uri)
    }
  }

  private fun actuallyOpenInEditor(project: Project?, uri: URI): Boolean {
    val service = MarkdownFrontendService.getInstance()
    @Suppress("NAME_SHADOWING")
    val project = project ?: service.guessProjectForUri(uri) ?: return false
    val anchor = uri.fragment
    if (anchor == null){
      coroutineScope.launch(Dispatchers.EDT) {
        service.openFile(project.projectId(), uri)
      }
      return true
    }
    val headers = service.collectHeaders(project.projectId(), uri)
    if (headers == null) {
      coroutineScope.launch {
        DumbService.getInstance(project).showDumbModeNotificationForFunctionality(
          message = MarkdownBundle.message("markdown.dumb.mode.navigation.is.not.available.notification.text"),
          functionality = DumbModeBlockedFunctionality.ActionWithoutId
        )
      }
      // Return true to prevent external navigation from happening
      return true
    }
    if (headers.size == 1) {
      coroutineScope.launch(Dispatchers.EDT) {
        service.navigateToHeader(project.projectId(), headers.first())
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

  private fun actuallyOpenInEditor(project: Project?, uri: URI): Boolean {
    @Suppress("NAME_SHADOWING")
    val project = project ?:
        runBlockingCancellable {
          withContext(Dispatchers.IO) {
            MarkdownLinkOpenerRemoteApi.getInstance().guessProjectForUri(uri.toString())?.findProject()
          }
        }
    if (project == null) return false
    val anchor = uri.fragment
    if (anchor == null){
      coroutineScope.launch(Dispatchers.EDT) {
        OpenFileAction.openFile(uri.path, project)
      }
      return true
    }
    var headers = runBlockingCancellable {
      withContext(Dispatchers.IO) {
        MarkdownLinkOpenerRemoteApi.getInstance().collectHeaders(project.projectId(), uri.toString())
      }
    }
    if (headers == null) {
      coroutineScope.launch {
        DumbService.getInstance(project).showDumbModeNotificationForFunctionality(
          message = MarkdownBundle.message("markdown.dumb.mode.navigation.is.not.available.notification.text"),
          functionality = DumbModeBlockedFunctionality.ActionWithoutId
        )
      }
      // Return true to prevent external navigation from happening
      return true
    }
    if (headers.size == 1) {
      coroutineScope.launch(Dispatchers.EDT) {
        MarkdownFrontendService.getInstance().navigateToHeader(project.projectId(), headers.first())
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

  private fun createUri(link: String, containingFile: VirtualFile?): URI? {
    return try {
      if (BrowserUtil.isAbsoluteURL(link)) return URI(link)
      else {
      if (!Registry.`is`("markdown.open.link.fallback") && PlatformUtils.isJetBrainsClient()){
          val scheme = runBlockingCancellable {
            withContext(Dispatchers.IO) {
              MarkdownLinkOpenerRemoteApi.getInstance().resolveLinkAsFilePath(link, containingFile?.rpcId())
            }
          }
          if (scheme != null && scheme.startsWith("file://")) {
              return URI(scheme)
          }
        }
        return URI("http://$link")
      }
    } catch (exception: URISyntaxException) {
      logger.warn(exception)
      null
    }
  }

  companion object {
    private val logger = logger<MarkdownLinkOpenerImpl>()

    private fun isLocalHost(hostName: String?): Boolean {
      return hostName == null ||
             hostName.startsWith("127.") ||
             hostName.endsWith(":1") ||
             isLocalHost(hostName, false, false)
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
          MarkdownFrontendService.getInstance().navigateToHeader(project.projectId(), selectedValue);
        }
      }
    }
  }
}