// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ml.features.imports

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.application
import com.jetbrains.ml.building.blocks.model.MLModel
import com.jetbrains.ml.building.blocks.task.MLFeaturesTree
import com.jetbrains.ml.features.api.logs.EventPair
import com.jetbrains.python.codeInsight.imports.ImportCandidateHolder
import com.jetbrains.python.codeInsight.imports.mlapi.ImportCandidateContext
import com.jetbrains.python.codeInsight.imports.mlapi.ImportRankingContext
import com.jetbrains.python.codeInsight.imports.mlapi.MLUnitImportCandidate
import com.jetbrains.python.codeInsight.imports.mlapi.MLUnitImportRankingContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds


@Service(Service.Level.APP)
internal class MLApiComputations(
  val coroutineScope: CoroutineScope,
)

internal sealed class FinalCandidatesRanker(
  protected val contextFeatures: MLFeaturesTree,
  protected val contextLogs: MutableList<EventPair<*>>,
  protected val timestampStarted: Long,
) {

  abstract fun launchMLRanking(initialCandidatesOrder: MutableList<out ImportCandidateHolder>, displayResult: (RateableRankingResult) -> Unit)

  abstract val mlEnabled: Boolean
}

private class ExperimentalMLRanker(
  mlContextFeatures: MLFeaturesTree,
  mlContextLogs: MutableList<EventPair<*>>,
  timestampStarted: Long,
  private val mlModel: MLModel<Double>,
) : FinalCandidatesRanker(mlContextFeatures, mlContextLogs, timestampStarted) {

  override val mlEnabled = true

  override fun launchMLRanking(initialCandidatesOrder: MutableList<out ImportCandidateHolder>, displayResult: (RateableRankingResult) -> Unit) {
    service<MLApiComputations>().coroutineScope.launch(Dispatchers.Default) {
      contextFeatures.computeFeaturesFromProviders(
        MLUnitImportRankingContext with ImportRankingContext(initialCandidatesOrder),
      )

      val importCandidatesFeatures = initialCandidatesOrder.associateWith { candidate ->
        val candidateFeatures = contextFeatures.addChild()
        candidateFeatures.computeFeaturesFromProviders(
          MLUnitImportCandidate with ImportCandidateContext(initialCandidatesOrder, candidate)
        )
        candidateFeatures
      }

      val predictionsByCandidateTree = contextFeatures
        .predictForChildren(mlModel, emptyMap())
        .toMap()

      val scoreByCandidate = initialCandidatesOrder.associateWith { candidate ->
        val candidateTree = importCandidatesFeatures.getValue(candidate)
        val score = predictionsByCandidateTree.getValue(candidateTree)
        score
      }

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
        displayResult(RateableRankingResult(contextFeatures, contextLogs, importCandidatesFeatures, initialCandidatesOrder, relevanceCandidateOrder, timestampStarted))
      }
    }
  }
}

private class InitialOrderKeepingRanker(
  contextFeatures: MLFeaturesTree,
  contextLogs: MutableList<EventPair<*>>,
  timestampStarted: Long,
) : FinalCandidatesRanker(contextFeatures, contextLogs, timestampStarted) {
  override val mlEnabled = false

  override fun launchMLRanking(initialCandidatesOrder: MutableList<out ImportCandidateHolder>, displayResult: (RateableRankingResult) -> Unit) {
    application.runWriteAction {
      val importCandidatesFeatures = initialCandidatesOrder.associateWith { candidate ->
        contextFeatures.addChild()
      }
      displayResult(RateableRankingResult(contextFeatures, contextLogs, importCandidatesFeatures, initialCandidatesOrder, initialCandidatesOrder, timestampStarted))
    }
  }
}


internal fun launchMLRanking(initialCandidatesOrder: MutableList<out ImportCandidateHolder>, displayResult: (RateableRankingResult) -> Unit) {
  // In EDT

  val timestampStarted = System.currentTimeMillis()
  return MLTaskPyCharmImportStatementsRanking.lifetime {
    val mlContextFeatures = newMLFeaturesTree()
    val mlContextLogs = mutableListOf<EventPair<*>>()
    val rankingStatus = service<FinalImportRankingStatusService>().status
    var modelWasReady = true
    val ranker = if (rankingStatus.mlEnabled) {
      val mlModel = service<MLModelService>().getModel(MLTaskPyCharmImportStatementsRanking)
      if (mlModel != null) {
        ExperimentalMLRanker(mlContextFeatures, mlContextLogs, timestampStarted, mlModel)
      } else {
        modelWasReady = false
        mlContextLogs.add(ContextAnalysis.MODEL_UNAVAILABLE with true)
        InitialOrderKeepingRanker(mlContextFeatures, mlContextLogs, timestampStarted)
      }
    }
    else {
      InitialOrderKeepingRanker(mlContextFeatures, mlContextLogs, timestampStarted)
    }

    mlContextLogs.add(ContextAnalysis.ML_STATUS_CORRESPONDS_TO_BUCKET with (rankingStatus.mlStatusCorrespondsToBucket && modelWasReady))
    mlContextLogs.add(ContextAnalysis.ML_ENABLED with (rankingStatus.mlEnabled && modelWasReady))

    ranker.launchMLRanking(initialCandidatesOrder) { result ->
      val timestampDisplayed = System.currentTimeMillis()
      displayResult(result)
      mlContextLogs.add(ContextAnalysis.TIME_MS_TO_DISPLAY with (timestampDisplayed - timestampStarted))
    }
  }
}

internal class RateableRankingResult(
  private val mlContextFeatures: MLFeaturesTree,
  private val contextAnalysis: MutableList<EventPair<*>>,
  private val importCandidates: Map<ImportCandidateHolder, MLFeaturesTree>,
  val orderInitial: List<ImportCandidateHolder>,
  val order: List<ImportCandidateHolder>,
  private val timestampStarted: Long,
) {
  private var submitted = AtomicBoolean(false)
  private val analysis: MutableMap<MLFeaturesTree, MutableList<EventPair<*>>> = mutableMapOf(
    mlContextFeatures to contextAnalysis
  )

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
    analysis[requireNotNull(importCandidates[selected])] = mutableListOf(CandidateAnalysis.MLFieldCorrectElement with true)
    val selectedIndex = order.indexOf(selected)
    val selectedIndexOriginal = orderInitial.indexOf(selected)
    require(selectedIndex >= 0 && selectedIndexOriginal >= 0)
    contextAnalysis.add(ContextAnalysis.SELECTED_POSITION with selectedIndex)
    contextAnalysis.add(ContextAnalysis.SELECTED_POSITION_INITIAL with selectedIndexOriginal)
    service<MLApiComputations>().coroutineScope.launch(Dispatchers.IO) {
      submitLogs()
    }
  }

  private suspend fun submitLogs() {
    val logger = PyCharmImportsRankingLogs.mlLogger
    val loggingOption = getLoggingOption(mlContextFeatures)
    contextAnalysis.add(ContextAnalysis.ML_LOGGING_STATE with loggingOption)
    when (loggingOption) {
      LoggingOption.FULL -> logger.log(mlContextFeatures, analysis)
        LoggingOption.NO_TREE -> {
        val treeStem = logger.newMLLogsTree()
        treeStem.addAnalysis(contextAnalysis)
        logger.log(mlContextFeatures.sessionId, treeStem)
      }
      LoggingOption.SKIP -> Unit
    }
  }
}
