// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.black

import com.intellij.codeInsight.actions.VcsFacade
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.FormattingService
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorLastActionTracker
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.ui.LightweightHint
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.black.configuration.BlackFormatterConfiguration
import org.jetbrains.annotations.Nls
import java.awt.Container
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlin.time.toKotlinDuration


class BlackFormattingService : AsyncDocumentFormattingService() {
  companion object {
    private val LOG = Logger.getInstance(BlackFormattingService::class.java)
    val NAME: String = PyBundle.message("black.formatting.service.name")
    val DEFAULT_CHARSET: Charset = StandardCharsets.UTF_8
    const val NOTIFICATION_GROUP_ID = "Black Formatter Integration"
    val FEATURES: Set<FormattingService.Feature> = setOf()
  }

  override fun getFeatures(): Set<FormattingService.Feature> = FEATURES

  override fun canFormat(source: PsiFile): Boolean {
    if (!Registry.`is`("black.formatter.support.enabled")) return false
    val project = source.project
    val blackConfiguration = BlackFormatterConfiguration.getBlackConfiguration(project)

    val vFile = source.virtualFile ?: return false

    val isApplicable = BlackFormatterUtil.isFileApplicable(vFile)

    if (isApplicable) {
      BlackFormatterAdvertiserService.getInstance(source.project).suggestBlack(source, blackConfiguration)
    }

    if (!blackConfiguration.enabledOnReformat) return false

    return isApplicable
  }

  override fun createFormattingTask(formattingRequest: AsyncFormattingRequest): FormattingTask? {
    val formattingContext = formattingRequest.context
    val file = formattingContext.containingFile
    val vFile = formattingContext.virtualFile ?: return null
    val project = formattingContext.project
    val blackConfig = BlackFormatterConfiguration.getBlackConfiguration(project)
    val sdk = blackConfig.getSdk()
    val editor = PsiEditorUtil.findEditor(file)

    if (sdk == null) {
      val message = PyBundle.message("black.sdk.not.configured.error", project.name)
      LOG.warn(message)
      formattingRequest.onError(PyBundle.message("black.sdk.not.configured.error.title"), message)
      return null
    }

    val document = PsiDocumentManager.getInstance(project).getDocument(file)
    if (document == null) {
      LOG.warn("Document for file ${file.name} is null")
      return null
    }

    val text = formattingRequest.documentText

    val formattingRange = if (formattingRequest.formattingRanges.size > 1) {
      TextRange(0, text.length)
    }
    else {
      formattingRequest.formattingRanges.first()
    }

    val isFormatFragmentAction = isFormatFragmentAction(text, formattingRange)

    val fragment = runCatching { text.substring(formattingRange.startOffset, formattingRange.endOffset) }.getOrNull()
    if (fragment.isNullOrBlank()) return null

    val blackFormattingRequest = if (isFormatFragmentAction)
      BlackFormattingRequest.Fragment(fragment, vFile)
    else
      BlackFormattingRequest.File(fragment, vFile)

    return object : FormattingTask {
      override fun run() {
        runCatching {
          val executor = BlackFormatterExecutor(project, sdk, blackConfig)

          when (val response = executor.getBlackFormattingResponse(blackFormattingRequest, timeout.toKotlinDuration())) {
            is BlackFormattingResponse.Success -> {
              val formattedFragment = response.formattedText
              val formattedDocumentText = when (blackFormattingRequest) {
                is BlackFormattingRequest.Fragment -> {
                  val postProcessedFragment = blackFormattingRequest.postProcessResponse(formattedFragment)
                  text.replaceRange(IntRange(formattingRange.startOffset, formattingRange.endOffset - 1), postProcessedFragment)
                }
                is BlackFormattingRequest.File -> {
                  formattedFragment
                }
              }
              val message = buildNotificationMessage(document, formattedDocumentText)
              showFormattedLinesInfo(editor, message, false)
              if (formattedDocumentText == text) { // if text is unchanged, pass null, see .onTextReady(...) doc
                formattingRequest.onTextReady(null)
              } else {
                formattingRequest.onTextReady(formattedDocumentText)
              }
            }
            is BlackFormattingResponse.Failure -> {
              LOG.debug(response.getLoggingMessage())
              when (blackFormattingRequest) {
                is BlackFormattingRequest.File -> {
                  formattingRequest.onError(response.title, response.getPopupMessage())
                }
                is BlackFormattingRequest.Fragment -> {
                  showFormattedLinesInfo(editor,
                                         PyBundle.message("black.format.fragment.inline.error",
                                                          response.getInlineNotificationMessage()),
                                         true)
                  formattingRequest.onTextReady(text)
                }
              }
            }
            is BlackFormattingResponse.Ignored -> {
              showFormattedLinesInfo(editor, PyBundle.message("black.file.ignored.notification.message", vFile.name), false)
              formattingRequest.onTextReady(text)
            }
          }
        }.onFailure { exception ->
          when (exception) {
            is ProcessCanceledException -> { /* ignore */ }
            else -> {
              LOG.warn(exception)
              formattingRequest.onError(PyBundle.message("black.exception.error.message"), exception.localizedMessage)
            }
          }
        }
      }

      override fun cancel(): Boolean {
        return true
      }

      override fun isRunUnderProgress(): Boolean {
        return true
      }
    }
  }

  private fun buildNotificationMessage(document: Document, textBefore: CharSequence): @Nls String {
    val diff = VcsFacade.getInstance().calculateChangedLinesNumber(document, textBefore)
    return if (diff == 0)
      PyBundle.message("black.no.lines.changed")
    else
      PyBundle.message("black.formatted.n.lines", diff, if (diff == 1) 1 else 0)
  }

  private fun showFormattedLinesInfo(editor: Editor?, text: @Nls String, isError: Boolean) {
    if (editor != null) {
      ApplicationManager.getApplication()
        .invokeLater({
                       val component = if (isError) HintUtil.createErrorLabel(text) else HintUtil.createInformationLabel(text)
                       val hint = LightweightHint(component)
                       HintManagerImpl.getInstanceImpl()
                         .showEditorHint(hint, editor, HintManager.ABOVE,
                                         HintManager.HIDE_BY_ANY_KEY or HintManager.HIDE_BY_SCROLLING, 0,
                                         false)
                     },
                     ModalityState.defaultModalityState()) { editor.isDisposed || !editor.component.isShowing }
    }
  }

  private fun isFormatFragmentAction(text: CharSequence, range: TextRange): Boolean = range.length != text.length

  override fun getNotificationGroupId(): String = NOTIFICATION_GROUP_ID

  override fun getName(): String = NAME
}