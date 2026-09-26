package com.intellij.python.ml.features.imports

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Computable
import com.intellij.util.Consumer
import com.jetbrains.python.codeInsight.imports.ImportCandidateHolder
import com.jetbrains.python.codeInsight.imports.PyImportChooser
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise

class PyMLImportChooser : PyImportChooser() {
  override fun selectImport(
    sources: MutableList<out ImportCandidateHolder>,
    useQualifiedImport: Boolean
  ): Promise<ImportCandidateHolder?> {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return resolvedPromise<ImportCandidateHolder?>(sources.get(0))
    }
    val result = AsyncPromise<ImportCandidateHolder?>()

    launchMLRanking(sources) { mlRanking: RateableRankingResult? ->
      // GUI part
      DataManager.getInstance().getDataContextFromFocus().doWhenDone(Consumer { dataContext: DataContext? ->
        val popup = JBPopupFactory.getInstance()
          .createPopupChooserBuilder<ImportCandidateHolder>(mlRanking!!.order)
          .setItemChosenCallback(Consumer { item: ImportCandidateHolder? ->
            result.setResult(item)
            mlRanking.submitSelectedItem(item!!)
          })
          .setCancelCallback(Computable {
            mlRanking.submitPopUpClosed()
            true
          })
        processPopup(popup, useQualifiedImport)
        popup.createPopup()
          .showInBestPositionFor(dataContext!!)
      })
      Unit
    }
    return result
  }
}
