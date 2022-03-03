package org.intellij.plugins.markdown.ui.preview.accessor.impl

import com.intellij.ide.BrowserUtil
import com.intellij.ide.actions.OpenFileAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectForFile
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.ui.showOkCancelDialog
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.PsiNavigateUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.io.isLocalHost
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.references.MarkdownAnchorReference
import org.intellij.plugins.markdown.settings.DocumentLinksSafeState
import org.intellij.plugins.markdown.ui.preview.MarkdownEditorWithPreview
import org.intellij.plugins.markdown.ui.preview.accessor.MarkdownLinkOpener
import org.intellij.plugins.markdown.util.MarkdownDisposable
import java.net.URI
import java.net.URISyntaxException

internal class MarkdownLinkOpenerImpl: MarkdownLinkOpener {
  override fun openLink(project: Project?, link: String) {
    val uri = createUri(link) ?: return
    if (tryOpenInEditor(uri)) {
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
    val dialogResult = showOkCancelDialog(
      title = MarkdownBundle.message("markdown.browse.external.link.open.confirmation.dialog.title"),
      message = MarkdownBundle.message("markdown.browse.external.link.open.confirmation.dialog.text", uri),
      okText = Messages.getOkButton(),
      doNotAskOption = createDoNotAskOption(project, uri),
      project = project
    )
    if (dialogResult == DialogWrapper.OK_EXIT_CODE) {
      actuallyBrowseExternalLink(project, uri)
    }
  }

  @RequiresEdt
  private fun actuallyBrowseExternalLink(project: Project?, uri: URI) {
    try {
      BrowserUtil.browse(uri)
    } catch (exception: Throwable) {
      logger.warn("Failed to browse external link!", exception)
      Notifications.Bus.notify(
        Notification(
          "Markdown",
          MarkdownBundle.message("markdown.browse.external.link.failed.notification.title"),
          MarkdownBundle.message("markdown.browse.external.link.failed.notification.content", uri),
          NotificationType.WARNING
        ),
        project
      )
    }
  }

  private fun createDoNotAskOption(project: Project?, uri: URI): DialogWrapper.DoNotAskOption? {
    if (project == null) {
      return null
    }
    val protocol = uri.scheme
    if (protocol == null) {
      logger.error("Failed to obtain protocol for link: $uri")
      return null
    }
    return object: DialogWrapper.DoNotAskOption.Adapter() {
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

    private fun tryOpenInEditor(uri: URI): Boolean {
      if (uri.scheme != "file") {
        return false
      }
      return runReadAction {
        actuallyOpenInEditor(uri)
      }
    }

    private fun actuallyOpenInEditor(uri: URI): Boolean {
      val anchor = uri.fragment
      val path = uri.path
      val targetFile = LocalFileSystem.getInstance().findFileByPath(path) ?: return false
      val project = guessProjectForFile(targetFile) ?: return false
      if (anchor == null) {
        invokeLater {
          OpenFileAction.openFile(targetFile, project)
        }
        return true
      }
      val point = obtainHeadersPopupPosition(project) ?: return false
      val headers = runReadAction {
        MarkdownAnchorReference.getPsiHeaders(project, anchor, PsiManager.getInstance(project).findFile(targetFile))
      }
      invokeLater {
        when {
          headers.isEmpty() -> showCannotNavigateNotification(project, anchor, point)
          headers.size == 1 -> navigateToHeader(targetFile, headers.first())
          else -> showHeadersPopup(headers, point)
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

    private fun showHeadersPopup(headers: Collection<PsiElement>, point: RelativePoint) {
      JBPopupFactory.getInstance().createListPopup(HeadersPopup(headers.toList())).show(point)
    }

    private class HeadersPopup(
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
          navigateToHeader(selectedValue.containingFile.virtualFile, selectedValue)
        }
      }
    }

    private fun navigateToHeader(targetFile: VirtualFile, item: PsiElement) {
      val editorManager = FileEditorManager.getInstance(item.project)
      val editor = editorManager.getSelectedEditor(targetFile) as? MarkdownEditorWithPreview ?: return
      val oldAutoScrollPreview = editor.isAutoScrollPreview
      if (!oldAutoScrollPreview) {
        editor.isAutoScrollPreview = true
      }
      PsiNavigateUtil.navigate(item)
      if (!oldAutoScrollPreview) {
        editor.isAutoScrollPreview = false
      }
    }
  }
}