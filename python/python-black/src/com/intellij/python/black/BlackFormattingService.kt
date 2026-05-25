// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.black

import com.intellij.codeInsight.actions.VcsFacade
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.FormattingService
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.python.pytools.isEnabledOn
import com.intellij.ui.LightweightHint
import com.intellij.util.application
import com.jetbrains.python.PythonFileType
import com.intellij.python.black.PyBlackBundle.message
import com.intellij.python.black.statistics.BlackFormatterIntegrationIdsHolder.Companion.BLACK_FORMATTER_EXCEPTION
import com.intellij.python.black.statistics.BlackFormatterIntegrationIdsHolder.Companion.BLACK_FORMATTER_TIMEOUT
import com.jetbrains.python.pyi.PyiFileType
import org.jetbrains.annotations.Nls
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.toKotlinDuration


private val NAME: String = message("black.formatting.service.name")
private val FEATURES: Set<FormattingService.Feature> = setOf(FormattingService.Feature.FORMAT_FRAGMENTS)

internal fun VirtualFile.isBlackFormattingSupported(): Boolean {
  return fileType == PythonFileType.INSTANCE || fileType == PyiFileType.INSTANCE
}

class BlackFormattingService : AsyncDocumentFormattingService() {

  companion object {
    private val LOG = Logger.getInstance(BlackFormattingService::class.java)
    const val NOTIFICATION_GROUP_ID: String = "Black Formatter Integration"
  }

  override fun getFeatures(): Set<FormattingService.Feature> = FEATURES

  override fun canFormat(source: PsiFile): Boolean {
    return source.virtualFile?.isBlackFormattingSupported() == true
           && BlackPyTool.getInstance().isEnabledOn(source.project)
  }

  override fun createFormattingTask(formattingRequest: AsyncFormattingRequest): FormattingTask? {
    val formattingContext = formattingRequest.context
    val file = formattingContext.containingFile
    val vFile = formattingContext.virtualFile ?: return null
    val project = formattingContext.project
    val editor = PsiEditorUtil.findEditor(file)

    val document = PsiDocumentManager.getInstance(project).getDocument(file)
    if (document == null) {
      LOG.warn("Document for file ${file.name} is null")
      return null
    }

    val documentText = formattingRequest.documentText
    val isFormatFragmentAction = isFormatFragmentAction(documentText, formattingRequest)

    val blackFormattingRequest = if (isFormatFragmentAction) {
      val lineRanges = mutableListOf<IntRange>()

      // Line numbers must be computed against the formatter snapshot (`documentText`),
      // not the live PSI document: the latter may have been edited (e.g. shortened)
      // between snapshot capture and this call, making snapshot offsets invalid for it.
      val newlineOffsets = buildList {
        var i = documentText.indexOf('\n')
        while (i >= 0) {
          add(i)
          i = documentText.indexOf('\n', i + 1)
        }
      }

      fun lineOf(offset: Int): Int {
        val idx = newlineOffsets.binarySearch(offset)
        return (if (idx >= 0) idx else -idx - 1) + 1
      }
      formattingRequest.formattingRanges.forEach { range ->
        lineRanges.add(IntRange(lineOf(range.startOffset), lineOf(range.endOffset)))
      }

      BlackFormattingRequest.Fragment(vFile, documentText, lineRanges)
    }
    else {
      BlackFormattingRequest.File(vFile, documentText)
    }

    return object : FormattingTask {

      override fun run() {
        runCatching {
          val response = runBlockingCancellable {
            BlackPyTool.getInstance().execute(project, blackFormattingRequest, timeout.toKotlinDuration())
          }

          when (response) {
            is BlackFormattingResponse.Success -> {
              val formattedDocumentText = response.formattedText
              if (formattedDocumentText == documentText) {
                formattingRequest.onTextReady(null)
              }
              else {
                formattingRequest.onTextReady(formattedDocumentText)
              }
            }
            is BlackFormattingResponse.Failure -> {
              editor.showFormattedLinesInfo(
                text = message("black.format.fragment.inline.error", response.getInlineNotificationMessage()),
                isError = true
              )
              formattingRequest.onTextReady(null)
            }
            is BlackFormattingResponse.Ignored -> {
              editor.showFormattedLinesInfo(
                text = message("black.file.ignored.notification.message", vFile.name),
                isError = false
              )
              formattingRequest.onTextReady(null)
            }
          }
        }.onFailure { exception ->
          when (exception) {
            is CancellationException -> throw exception
            else -> {
              LOG.warn(exception)
              formattingRequest.onError(
                message("black.exception.error.message"),
                exception.localizedMessage,
                BLACK_FORMATTER_EXCEPTION
              )
            }
          }
        }
      }

      override fun cancel(): Boolean = true
      override fun isRunUnderProgress(): Boolean = true
    }
  }

  private fun Editor?.showFormattedLinesInfo(text: @Nls String, isError: Boolean) {
    if (this == null) return

    application.invokeLater(
      {
        val component = if (isError) HintUtil.createErrorLabel(text) else HintUtil.createInformationLabel(text)
        val hint = LightweightHint(component)
        HintManagerImpl.getInstanceImpl()
          .showEditorHint(hint, this, HintManager.ABOVE,
                          HintManager.HIDE_BY_ANY_KEY or HintManager.HIDE_BY_SCROLLING, 0,
                          false)
      },
      ModalityState.defaultModalityState()) { isDisposed || !component.isShowing }
  }

  private fun isFormatFragmentAction(documentText: CharSequence, formattingRequest: AsyncFormattingRequest): Boolean {
    return formattingRequest.formattingRanges.size != 1 || documentText.length != formattingRequest.formattingRanges.first().length
  }

  override fun getNotificationGroupId(): String = NOTIFICATION_GROUP_ID

  override fun getTimeoutNotificationDisplayId(): String = BLACK_FORMATTER_TIMEOUT

  override fun getName(): String = NAME
}