/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.browsers.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.browsers.*
import com.intellij.ide.browsers.impl.WebBrowserServiceImpl
import com.intellij.internal.statistic.UsageTrigger
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vcs.vfs.ContentRevisionVirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.components.JBList
import com.intellij.util.BitUtil
import com.intellij.util.Url
import com.intellij.xml.util.HtmlUtil
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
import java.awt.event.InputEvent
import javax.swing.Icon
import javax.swing.JList

private val LOG = Logger.getInstance(BaseOpenInBrowserAction::class.java)

abstract class BaseOpenInBrowserAction : DumbAwareAction {
  @Suppress("unused")
  protected constructor(browser: WebBrowser) : super(browser.name, null, browser.icon) {
  }

  @Suppress("unused")
  protected constructor(text: String?, description: String?, icon: Icon?) : super(text, description, icon) {
  }

  companion object {
    @JvmStatic
    fun doUpdate(event: AnActionEvent): OpenInBrowserRequest? {
      val request = createRequest(event.dataContext)
      val applicable = request != null && WebBrowserServiceImpl.getProvider(request) != null
      event.presentation.isEnabledAndVisible = applicable
      return if (applicable) request else null
    }

    fun open(event: AnActionEvent, browser: WebBrowser?) {
      open(createRequest(event.dataContext), BitUtil.isSet(event.modifiers, InputEvent.SHIFT_MASK), browser)
    }

    fun open(request: OpenInBrowserRequest?, preferLocalUrl: Boolean, browser: WebBrowser?) {
      if (request == null) {
        return
      }

      try {
        val urls = WebBrowserService.getInstance().getUrlsToOpen(request, preferLocalUrl)
        if (!urls.isEmpty()) {
          chooseUrl(urls)
              .done { url ->
                ApplicationManager.getApplication().saveAll()
                BrowserLauncher.getInstance().browse(url.toExternalForm(), browser, request.project)
              }
        }
      }
      catch (e: WebBrowserUrlProvider.BrowserException) {
        Messages.showErrorDialog(e.message, IdeBundle.message("browser.error"))
      }
      catch (e: Exception) {
        LOG.error(e)
      }
    }
  }

  protected abstract fun getBrowser(event: AnActionEvent): WebBrowser?

  override fun update(e: AnActionEvent) {
    val browser = getBrowser(e)
    if (browser == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val result = doUpdate(e) ?: return

    var description = templatePresentation.text
    if (ActionPlaces.CONTEXT_TOOLBAR == e.place) {
      val builder = StringBuilder(description)
      builder.append(" (")
      val shortcuts = KeymapManager.getInstance().activeKeymap.getShortcuts("WebOpenInAction")
      val exists = shortcuts.size > 0
      if (exists) {
        builder.append(KeymapUtil.getShortcutText(shortcuts[0]))
      }

      if (HtmlUtil.isHtmlFile(result.file)) {
        builder.append(if (exists) ", " else "").append("hold Shift to open URL of local file")
      }
      builder.append(')')
      description = builder.toString()
    }
    e.presentation.text = description
  }

  override fun actionPerformed(e: AnActionEvent) {
    getBrowser(e)?.let {
      UsageTrigger.trigger("OpenInBrowser.${it.name}")
      open(e, it)
    }
  }
}

private fun createRequest(context: DataContext): OpenInBrowserRequest? {
  val editor = CommonDataKeys.EDITOR.getData(context)
  if (editor != null) {
    val project = editor.project
    if (project != null && project.isInitialized) {
      val psiFile = CommonDataKeys.PSI_FILE.getData(context) ?: PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
      if (psiFile != null && psiFile.virtualFile !is ContentRevisionVirtualFile) {
        return object : OpenInBrowserRequest() {
          override val file: PsiFile = psiFile

          private val _element by lazy { file.findElementAt(editor.caretModel.offset) }

          override val element: PsiElement
              get() = _element ?: file
        }
      }
    }
  }
  else {
    var psiFile = CommonDataKeys.PSI_FILE.getData(context)
    val virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(context)
    val project = CommonDataKeys.PROJECT.getData(context)
    if (virtualFile != null && !virtualFile.isDirectory && virtualFile.isValid && project != null && project.isInitialized) {
      psiFile = PsiManager.getInstance(project).findFile(virtualFile)
    }

    if (psiFile != null && psiFile.virtualFile !is ContentRevisionVirtualFile) {
      return createOpenInBrowserRequest(psiFile)
    }
  }
  return null
}

private fun chooseUrl(urls: Collection<Url>): Promise<Url> {
  if (urls.size == 1) {
    return resolvedPromise(urls.first())
  }

  val list = JBList<Url>(urls)
  list.setCellRenderer(object : ColoredListCellRenderer<Url>() {
    override fun customizeCellRenderer(list: JList<out Url>, value: Url?, index: Int, selected: Boolean, hasFocus: Boolean) {
      // todo icons looks good, but is it really suitable for all URLs providers?
      icon = AllIcons.Nodes.Servlet
      append((value as Url).toDecodedForm())
    }
  })

  val result = AsyncPromise<Url>()
  JBPopupFactory.getInstance()
      .createListPopupBuilder(list)
      .setTitle("Choose Url")
      .setItemChoosenCallback {
        val value = list.selectedValue
        if (value == null) {
          result.setError("selected value is null")
        }
        else {
          result.setResult(value)
        }
      }
      .createPopup().showInFocusCenter()
  return result
}