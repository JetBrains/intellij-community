package com.intellij.searchEverywhereMl.semantics.providers

import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhereMl.semantics.utils.sendRequest
import java.util.*

class ServerSemanticActionsProvider(val model: GotoActionModel) : SemanticActionsProvider() {
  private val mapper = jacksonObjectMapper()

  private val URL_BASE = if (Registry.`is`("search.everywhere.ml.semantic.actions.use.remote.model")) {
    Registry.stringValue("search.everywhere.ml.semantic.actions.remote.host")
  }
  else {
    Registry.stringValue("search.everywhere.ml.semantic.actions.local.host")
  }
  private val SEARCH_ENDPOINT = "search"

  private val ITEMS_LIMIT = 20
  private val SIMILARITY_THRESHOLD = 0.5

  override fun search(pattern: String): List<FoundItemDescriptor<GotoActionModel.MatchedValue>> {
    val requestJson: String = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapOf(
      "pattern" to pattern,
      "items_limit" to ITEMS_LIMIT,
      "similarity_threshold" to SIMILARITY_THRESHOLD
    ))

    val responseJson: String? = sendRequest("$URL_BASE/$SEARCH_ENDPOINT/", requestJson)
    if (responseJson == null) {
      return Collections.emptyList()
    }

    val modelResponse: ModelResponse = mapper.readValue(responseJson)

    return modelResponse.nearestCandidates.mapNotNull { createItemDescriptor(it.actionId, it.similarityScore, pattern, model) }
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