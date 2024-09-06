// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.imports.mlapi

import com.intellij.codeInsight.inline.completion.logs.TypingSpeedTracker
import com.intellij.codeInsight.inline.completion.ml.MLUnitTyping
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.application
import com.jetbrains.ml.MockMLModel
import com.jetbrains.ml.platform.MLApiTaskExecutor
import com.jetbrains.ml.session.MLSession
import com.jetbrains.python.codeInsight.imports.ImportCandidateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds


@Service(Service.Level.APP)
private class MLApiComputations(
  val coroutineScope: CoroutineScope,
)

internal sealed class FinalCandidatesRanker(
  protected val mlSession: MLSession<MockMLModel, Unit>,
  protected val timestampStarted: Long,
) {

  abstract fun launchMLRanking(initialCandidatesOrder: MutableList<out ImportCandidateHolder>, displayResult: (RateableRankingResult) -> Unit)

  abstract val mlEnabled: Boolean
}

private class ExperimentalMLRanker(
  private val coroutineScope: CoroutineScope,
  mlSession: MLSession<MockMLModel, Unit>, timestampStarted: Long,
) : FinalCandidatesRanker(mlSession, timestampStarted) {

  override val mlEnabled = true

  override fun launchMLRanking(initialCandidatesOrder: MutableList<out ImportCandidateHolder>, displayResult: (RateableRankingResult) -> Unit) {
    coroutineScope.launch(Dispatchers.Default) {
      val mlTreeRoot = mlSession.buildRoot(
        MLUnitImportCandidatesList with initialCandidatesOrder,
        MLUnitTyping with TypingSpeedTracker.getInstance(),
      ).computingFeatures { computeAllFeaturesNow() }

      for (candidate in initialCandidatesOrder) {
        mlTreeRoot
          .onlyLog(MLUnitImportCandidate with candidate)
          .computingFeatures { computeAllFeaturesNow() }
      }

      mlSession.finish()

      writeAction {
        displayResult(RateableRankingResult(mlSession, initialCandidatesOrder, timestampStarted))
      }
    }
  }
}

private class InitialOrderKeepingRanker(
  mlSession: MLSession<MockMLModel, Unit>, timestampStarted: Long,
) : FinalCandidatesRanker(mlSession, timestampStarted) {
  override val mlEnabled = false

  override fun launchMLRanking(initialCandidatesOrder: MutableList<out ImportCandidateHolder>, displayResult: (RateableRankingResult) -> Unit) {
    mlSession.finish()

    application.runWriteAction {
      displayResult(RateableRankingResult(mlSession, initialCandidatesOrder, timestampStarted))
    }
  }
}


internal fun launchMLRanking(initialCandidatesOrder: MutableList<out ImportCandidateHolder>, displayResult: (RateableRankingResult) -> Unit) {
  // In EDT

  val timestampStarted = System.currentTimeMillis()
  val mlCoroutineScope = service<MLApiComputations>().coroutineScope
  val mlSession = service<MLTaskPyCharmImportStatementsRanking>().task
    .startMLSession(taskExecutor = MLApiTaskExecutor.configure(mlCoroutineScope))
  val rankingStatus = service<FinalImportRankingStatusService>().status
  val ranker = if (rankingStatus.mlEnabled)
    ExperimentalMLRanker(mlCoroutineScope, mlSession, timestampStarted)
  else
    InitialOrderKeepingRanker(mlSession, timestampStarted)

  mlSession.writeRuntimeSessionAnalysis(ML_LOGGING_STATE with getLoggingOption(mlSession))
  mlSession.writeRuntimeSessionAnalysis(ML_ENABLED with rankingStatus.mlEnabled)
  mlSession.writeRuntimeSessionAnalysis(ML_STATUS_CORRESPONDS_TO_BUCKET with rankingStatus.mlStatusCorrespondsToBucket)

  ranker.launchMLRanking(initialCandidatesOrder) { result ->
    val timestampDisplayed = System.currentTimeMillis()
    displayResult(result)
    ML_FEEDBACK_TIME_MS_TO_DISPLAY.feedback(mlSession, timestampDisplayed - timestampStarted)
  }
}

internal class RateableRankingResult(
  private val mlSession: MLSession<*, *>,
  val order: List<ImportCandidateHolder>,
  private val timestampStarted: Long,
) {
  private var submitted = AtomicBoolean(false)

  fun submitPopUpClosed() {
    val timestampClosed = System.currentTimeMillis()
    ML_FEEDBACK_TIME_MS_BEFORE_CLOSED.feedback(mlSession, timestampClosed - timestampStarted)
    service<MLApiComputations>().coroutineScope.launch {
      delay(1.seconds)
      synchronized(this@RateableRankingResult) {
        if (submitted.get()) return@launch
        submitted.set(true)
        MLFeedbackCorrectElementPosition.feedbackEventPairs(mlSession, MLFeedbackCorrectElementPosition.CANCELLED with true)
        MLFieldCorrectElement.cancelFeedback(mlSession)
      }
    }
  }

  fun submitSelectedItem(selected: ImportCandidateHolder) = synchronized(this) {
    // In EDT
    require(submitted.compareAndSet(false, true)) { "Some feedback to the ml ranker was already submitted" }
    MLFieldCorrectElement.feedback(mlSession, selected, true)
    val selectedIndex = order.indexOf(selected)
    if (selectedIndex == -1)
      MLFeedbackCorrectElementPosition.feedbackEventPairs(mlSession, MLFeedbackCorrectElementPosition.INVALID_SELECTED_ITEM with true)
    else
      MLFeedbackCorrectElementPosition.feedbackEventPairs(mlSession, MLFeedbackCorrectElementPosition.SELECTED_POSITION with selectedIndex)
  }
}
