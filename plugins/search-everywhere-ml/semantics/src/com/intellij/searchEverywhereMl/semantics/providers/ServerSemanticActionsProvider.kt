package com.intellij.searchEverywhereMl.semantics.providers

import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhereMl.semantics.utils.RequestResult
import com.intellij.searchEverywhereMl.semantics.utils.sendRequest
import com.intellij.openapi.diagnostic.logger
import com.intellij.searchEverywhereMl.semantics.settings.SemanticSearchSettings

private val LOG = logger<ServerSemanticActionsProvider>()

class ServerSemanticActionsProvider(val model: GotoActionModel) : SemanticActionsProvider() {
  private val mapper = jacksonObjectMapper()

  private val URL_BASE = Registry.stringValue("search.everywhere.ml.semantic.actions.server.host")

  override fun search(pattern: String): List<FoundItemDescriptor<GotoActionModel.MatchedValue>> {
    if (!SemanticSearchSettings.getInstance().enabledInActionsTab || pattern.isBlank()) return emptyList()

    val requestJson: String = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapOf(
      "pattern" to pattern,
      "items_limit" to ITEMS_LIMIT,
      "similarity_threshold" to SIMILARITY_THRESHOLD,
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

    return modelResponse.nearestCandidates.mapNotNull { createItemDescriptor(it.actionId, it.similarityScore, pattern, model) }
  }

  companion object {
    private const val SEARCH_ENDPOINT = "search"

    private const val ITEMS_LIMIT = 10
    private const val SIMILARITY_THRESHOLD = 0.5
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