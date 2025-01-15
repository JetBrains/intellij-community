// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ml.features.imports

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.application
import com.jetbrains.ml.api.feature.Feature
import com.jetbrains.ml.api.logs.EventPair
import com.jetbrains.ml.api.model.MLModel
import com.jetbrains.ml.tools.logs.MLLogsTree
import com.jetbrains.python.codeInsight.imports.ImportCandidateHolder
import com.jetbrains.python.codeInsight.imports.mlapi.ImportCandidateContext
import com.jetbrains.python.codeInsight.imports.mlapi.ImportRankingContext
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds


@Service(Service.Level.APP)
internal class MLApiComputations(
  val coroutineScope: CoroutineScope,
)

internal sealed class FinalCandidatesRanker(
  protected val contextFeatures: MutableList<Feature>,
  protected val contextAnalysis: MutableList<EventPair<*>>,
  protected val timestampStarted: Long,
) {

  abstract fun launchMLRanking(initialCandidatesOrder: MutableList<out ImportCandidateHolder>, displayResult: (RateableRankingResult) -> Unit)

  abstract val mlEnabled: Boolean
}

private class ExperimentalMLRanker(
  contextFeatures: MutableList<Feature>,
  contextAnalysis: MutableList<EventPair<*>>,
  timestampStarted: Long,
  private val mlModel: MLModel<Double>,
) : FinalCandidatesRanker(contextFeatures, contextAnalysis, timestampStarted) {

  override val mlEnabled = true

  override fun launchMLRanking(initialCandidatesOrder: MutableList<out ImportCandidateHolder>, displayResult: (RateableRankingResult) -> Unit) {
    service<MLApiComputations>().coroutineScope.launch(Dispatchers.Default) {
      contextFeatures.addAll(FeaturesRegistry.computeContextFeatures(ImportRankingContext(initialCandidatesOrder), mlModel.knownFeatures))

      val importCandidatesFeatures = initialCandidatesOrder.associateWith { candidate ->
        async { FeaturesRegistry.computeCandidateFeatures(ImportCandidateContext(initialCandidatesOrder, candidate), mlModel.knownFeatures) }
      }.mapValues { it.value.await() }

      val scoreByCandidate = mlModel.predictBatch(contextFeatures, importCandidatesFeatures)

      val relevanceCandidateOrder = scoreByCandidate.toList()
        .sortedByDescending { it.second }
        .map { it.first }

      logger<ExperimentalMLRanker>().debug {
        "Sorted import candidates:\n" +
        relevanceCandidateOrder.mapIndexed { index, candidate ->
          val score = scoreByCandidate.getValue(candidate)
          val initialIndex = initialCandidatesOrder.indexOf(candidate)
          "\t#${index + 1}: '${candidate.presentableText}' (score: ${score}, initial index: $initialIndex)"
        }.joinToString("\n")
      }

      writeAction {
        displayResult(RateableRankingResult(this@ExperimentalMLRanker.contextFeatures, contextAnalysis, importCandidatesFeatures, initialCandidatesOrder, relevanceCandidateOrder, timestampStarted))
      }
    }
  }
}

private class InitialOrderKeepingRanker(
  contextFeatures: MutableList<Feature>,
  contextLogs: MutableList<EventPair<*>>,
  timestampStarted: Long,
) : FinalCandidatesRanker(contextFeatures, contextLogs, timestampStarted) {
  override val mlEnabled = false

  override fun launchMLRanking(initialCandidatesOrder: MutableList<out ImportCandidateHolder>, displayResult: (RateableRankingResult) -> Unit) {
    application.runWriteAction {
      val importCandidatesFeatures = initialCandidatesOrder.associateWith { candidate ->
        emptyList<Feature>()
      }
      displayResult(RateableRankingResult(contextFeatures, contextAnalysis, importCandidatesFeatures, initialCandidatesOrder, initialCandidatesOrder, timestampStarted))
    }
  }
}


internal fun launchMLRanking(initialCandidatesOrder: MutableList<out ImportCandidateHolder>, displayResult: (RateableRankingResult) -> Unit) {
  // In EDT

  val timestampStarted = System.currentTimeMillis()
  val mlContextFeatures = mutableListOf<Feature>()
  val mlContextLogs = mutableListOf<EventPair<*>>()
  val rankingStatus = service<FinalImportRankingStatusService>().status
  val ranker = when (rankingStatus) {
    is FinalImportRankingStatus.Disabled -> {
      InitialOrderKeepingRanker(mlContextFeatures, mlContextLogs, timestampStarted)
    }
    is FinalImportRankingStatus.Enabled -> {
      ExperimentalMLRanker(mlContextFeatures, mlContextLogs, timestampStarted, rankingStatus.mlModel)
    }
  }

  mlContextLogs.add(ContextAnalysis.MODEL_UNAVAILABLE with (!rankingStatus.mlModelUnavailable))
  mlContextLogs.add(ContextAnalysis.ML_ENABLED with rankingStatus.mlEnabled)
  mlContextLogs.add(ContextAnalysis.REGISTRY_OPTION with rankingStatus.registryOption)

  ranker.launchMLRanking(initialCandidatesOrder) { result ->
    val timestampDisplayed = System.currentTimeMillis()
    displayResult(result)
    mlContextLogs.add(ContextAnalysis.TIME_MS_TO_DISPLAY with (timestampDisplayed - timestampStarted))
  }
}

internal class RateableRankingResult(
  private val contextFeatures: List<Feature>,
  private val contextAnalysis: MutableList<EventPair<*>>,
  private val importCandidates: Map<ImportCandidateHolder, List<Feature>>,
  val orderInitial: List<ImportCandidateHolder>,
  val order: List<ImportCandidateHolder>,
  private val timestampStarted: Long,
) {
  private var submitted = AtomicBoolean(false)
  private val candidateAnalysis: MutableMap<ImportCandidateHolder, EventPair<*>> = mutableMapOf()

  fun submitPopUpClosed() {
    val timestampClosed = System.currentTimeMillis()
    contextAnalysis.add(ContextAnalysis.TIME_MS_BEFORE_CLOSED with timestampClosed - timestampStarted)
    service<MLApiComputations>().coroutineScope.launch(Dispatchers.IO) {
      delay(1.seconds)
      synchronized(this@RateableRankingResult) {
        if (submitted.get()) return@launch
        submitted.set(true)
        contextAnalysis.add(ContextAnalysis.CANCELLED with true)
      }
      submitLogs()
    }
  }

  fun submitSelectedItem(selected: ImportCandidateHolder) = synchronized(this) {
    // In EDT
    require(submitted.compareAndSet(false, true)) { "Some feedback to the ml ranker was already submitted" }
    candidateAnalysis[selected] = CandidateAnalysis.MLFieldCorrectElement with true
    val selectedIndex = order.indexOf(selected)
    val selectedIndexOriginal = orderInitial.indexOf(selected)
    require(selectedIndex >= 0 && selectedIndexOriginal >= 0)
    contextAnalysis.add(ContextAnalysis.SELECTED_POSITION with selectedIndex)
    contextAnalysis.add(ContextAnalysis.SELECTED_POSITION_INITIAL with selectedIndexOriginal)
    submitLogs()
  }

  private fun submitLogs() {
    val logger = PyCharmImportsRankingLogs.mlLogger
    val loggingOption = getLoggingOption()
    contextAnalysis.add(ContextAnalysis.ML_LOGGING_STATE with loggingOption)
    when (loggingOption) {
      LoggingOption.FULL -> {
        val logsTree = MLLogsTree(contextFeatures, contextAnalysis, importCandidates.map { (candidate, features) ->
          MLLogsTree(features, candidateAnalysis[candidate]?.let { listOf(it) } ?: emptyList())
        })
        logger.log(logsTree)
      }
      LoggingOption.NO_TREE -> {
        val logsTree = MLLogsTree(contextFeatures, contextAnalysis)
        logger.log(logsTree)
      }
      LoggingOption.SKIP -> Unit
    }
  }
}
