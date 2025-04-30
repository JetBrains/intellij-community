package com.intellij.terminal.frontend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.asDisposable
import kotlinx.coroutines.*
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModelListener

/**
 * Calls [PsiDocumentManager.commitDocument] on the provided [TerminalOutputModel.document] after the changes in the output model.
 * But doing it with a debouncing, because document commit requires write action,
 * but the output model can be updated very frequently in case of large output. So we can't commit the document on the every change
 * because of performance reasons.
 */
internal fun updatePsiOnOutputModelChange(
  project: Project,
  outputModel: TerminalOutputModel,
  coroutineScope: CoroutineScope,
) {
  // Accessed only on EDT
  var updateJob: Job? = null

  outputModel.addListener(coroutineScope.asDisposable(), object : TerminalOutputModelListener {
    override fun afterContentChanged(model: TerminalOutputModel, startOffset: Int) {
      updateJob?.cancel()
      updateJob = coroutineScope.launch {
        delay(50)

        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          try {
            PsiDocumentManager.getInstance(project).commitDocument(outputModel.document)
          }
          finally {
            updateJob = null
          }
        }
      }
    }
  })
}