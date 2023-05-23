package org.intellij.plugins.markdown.ui.preview.accessor.impl

import com.intellij.ide.BrowserUtil
import com.intellij.ide.actions.OpenFileAction
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
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
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.PsiNavigateUtil
import com.intellij.util.UriUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.io.isLocalHost
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.index.HeaderAnchorIndex
import org.intellij.plugins.markdown.settings.DocumentLinksSafeState
import org.intellij.plugins.markdown.ui.MarkdownNotifications
import org.intellij.plugins.markdown.ui.preview.MarkdownEditorWithPreview
import org.intellij.plugins.markdown.ui.preview.accessor.MarkdownLinkOpener
import org.intellij.plugins.markdown.util.MarkdownDisposable
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Path

internal class MarkdownLinkOpenerImpl: MarkdownLinkOpener {
  override fun openLink(project: Project?, link: String) {
    val uri = createUri(link) ?: return
    if (tryOpenInEditor(project, uri)) {
      return
    }
    invokeLater {
      openExternalLink(project, uri)
    }
  }

  override fun isSafeLink(project: Project?, link: String): Boolean {
    val uri = createUri(link)?: return false
    return isSafeUri(project, uri)
  }

  private fun isSafeUri(project: Project?, uri: URI): Boolean {
    val protocol = uri.scheme ?: return false
    if (project != null) {
      val safeLinksState = DocumentLinksSafeState.getInstance(project)
      return safeLinksState.isProtocolAllowed(protocol)
    }
    return DocumentLinksSafeState.isHttpScheme(protocol) && isLocalHost(uri.host)
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
          DocumentLinksSafeState.getInstance(project).allowProtocol(protocol)
        }
      }

      override fun getDoNotShowMessage(): String {
        return MarkdownBundle.message("markdown.browse.external.link.open.confirmation.dialog.do.not.ask.again.text", protocol)
      }
    }
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
             isLocalHost(hostName, false, false)
    }

    private fun tryOpenInEditor(project: Project?, uri: URI): Boolean {
      if (uri.scheme != "file") {
        return false
      }
      return runReadAction {
        actuallyOpenInEditor(project, uri)
      }
    }

    private fun URI.findVirtualFile(): VirtualFile? {
      val actualPath = when {
        SystemInfo.isWindows -> UriUtil.trimLeadingSlashes(path)
        else -> path
      }
      val path = Path.of(actualPath)
      return VfsUtil.findFile(path, true)
    }

    private fun actuallyOpenInEditor(project: Project?, uri: URI): Boolean {
      val anchor = uri.fragment
      val targetFile = uri.findVirtualFile() ?: return false
      @Suppress("NAME_SHADOWING")
      val project = project ?: guessProjectForFile(targetFile) ?: return false
      if (anchor == null) {
        invokeLater {
          OpenFileAction.openFile(targetFile, project)
        }
        return true
      }
      val point = obtainHeadersPopupPosition(project)
      if (point == null) {
        logger.warn("Failed to obtain screen point for showing popup")
        return false
      }
      val headers = runReadAction {
        val file = PsiManager.getInstance(project).findFile(targetFile)
        val scope = when (file) {
          null -> GlobalSearchScope.EMPTY_SCOPE
          else -> GlobalSearchScope.fileScope(file)
        }
        return@runReadAction HeaderAnchorIndex.collectHeaders(project, scope, anchor)
      }
      invokeLater {
        when {
          headers.isEmpty() -> showCannotNavigateNotification(project, anchor, point)
          headers.size == 1 -> navigateToHeader(project, targetFile, headers.first())
          else -> showHeadersPopup(project, headers, point)
        }
      }
      return true
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
      Disposer.register(MarkdownDisposable.getInstance(project), balloon)
      balloon.show(point, Balloon.Position.below)
    }

    private fun showHeadersPopup(project: Project, headers: Collection<PsiElement>, point: RelativePoint) {
      JBPopupFactory.getInstance().createListPopup(HeadersPopup(project, headers.toList())).show(point)
    }

    private class HeadersPopup(
      private val project: Project,
      headers: List<PsiElement>
    ): BaseListPopupStep<PsiElement>(MarkdownBundle.message("markdown.navigate.to.header"), headers) {
      override fun getTextFor(value: PsiElement): String {
        val document = FileDocumentManager.getInstance().getDocument(value.containingFile.virtualFile)
        requireNotNull(document)
        val name = value.containingFile.virtualFile.name
        val line = document.getLineNumber(value.textOffset) + 1
        return "${value.text} ($name:$line)"
      }

      override fun onChosen(selectedValue: PsiElement, finalChoice: Boolean): PopupStep<*> {
        return doFinalStep {
          navigateToHeader(project, selectedValue.containingFile.virtualFile, selectedValue)
        }
      }
    }

    private fun navigateToHeader(project: Project, file: VirtualFile, element: PsiElement) {
      val manager = FileEditorManager.getInstance(project)
      val openedEditors = manager.getEditors(file).filterIsInstance<MarkdownEditorWithPreview>()
      if (openedEditors.isNotEmpty()) {
        for (editor in openedEditors) {
          PsiNavigateUtil.navigate(element, true)
        }
        return
      }
      val descriptor = OpenFileDescriptor(project, file, element.textOffset)
      manager.openEditor(descriptor, true)
    }
  }
}
