package com.intellij.searchEverywhereMl.semantics.providers

import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ml.embeddings.search.settings.SemanticSearchSettings
import com.intellij.searchEverywhereMl.semantics.utils.RequestResult
import com.intellij.searchEverywhereMl.semantics.utils.sendRequest

private val LOG = logger<ServerSemanticActionsProvider>()

class ServerSemanticActionsProvider(model: GotoActionModel, presentationProvider: suspend (AnAction) -> Presentation) :
  SemanticActionsProvider(model, presentationProvider) {

  private val mapper = jacksonObjectMapper()

  private val URL_BASE = Registry.stringValue("search.everywhere.ml.semantic.actions.server.host")

  override suspend fun search(pattern: String, similarityThreshold: Double?): List<FoundItemDescriptor<GotoActionModel.MatchedValue>> {
    if (!SemanticSearchSettings.getInstance().enabledInActionsTab || pattern.isBlank()) return emptyList()

    val requestJson: String = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapOf(
      "pattern" to pattern,
      "items_limit" to ITEMS_LIMIT,
      "similarity_threshold" to similarityThreshold,
      "token" to SemanticSearchSettings.getInstance().getActionsAPIToken()
    ))

    val modelResponse: ModelResponse = when (
      val requestResult = sendRequest("$URL_BASE/$SEARCH_ENDPOINT/", requestJson)
    ) {
      is RequestResult.Success -> mapper.readValue(requestResult.data)
      is RequestResult.Error -> {
        LOG.warn(requestResult.message)
        return emptyList()
      }
    }

    return modelResponse.nearestCandidates.mapNotNull { createItemDescriptor(it.actionId, it.similarityScore, pattern) }
  }

  override suspend fun streamSearch(pattern: String, similarityThreshold: Double?): Sequence<FoundItemDescriptor<GotoActionModel.MatchedValue>> {
    return searchIfEnabled(pattern, similarityThreshold).asSequence()
  }

  override fun isEnabled(): Boolean {
    return SemanticSearchSettings.getInstance().enabledInActionsTab
  }

  companion object {
    private const val SEARCH_ENDPOINT = "search"

    private const val ITEMS_LIMIT = 10
  }
}

data class ModelActionInfo(
  @JsonSetter("action_id")
  val actionId: String,

  @JsonSetter("embedded_text")
  val embeddedText: String,

  @JsonSetter("similarity_score")
  val similarityScore: Double
)

data class ModelResponse(
  @JsonSetter("nearest_candidates")
  val nearestCandidates: List<ModelActionInfo>
)