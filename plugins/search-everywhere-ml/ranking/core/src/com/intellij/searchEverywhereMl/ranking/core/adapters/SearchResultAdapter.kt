package com.intellij.searchEverywhereMl.ranking.core.adapters

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.actions.searcheverywhere.SemanticSearchEverywhereContributor
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeItemDataKeys
import com.intellij.platform.searchEverywhere.isSemantic

@JvmInline
value class SessionWideId(val value: Int)

@JvmInline
value class StateLocalId(val value: String)

@JvmInline
value class MlProbability(val value: Double) {
  fun toWeight(): Int {
    return (value * 10_000).toInt()
  }
}

sealed interface SearchResultAdapter {
  val provider: SearchResultProviderAdapter

  val originalWeight: Int

  val providerWeight: Int?

  val isSemantic: Boolean

  /**
   * Unique identifier of the element in the current search state
   *
   * The same element that appears in different search states may have different identifiers.
   * We use this identifier to cache ML-specific information about the element.
   */
  val stateLocalId: StateLocalId

  companion object {
    fun createAdapterFor(foundElementInfo: SearchEverywhereFoundElementInfo): Raw {
      return LegacyFoundElementInfoAdapter(foundElementInfo)
    }

    fun createAdapterFor(seItemData: SeItemData): Raw {
      return SeItemDataAdapter(seItemData)
    }
  }

  interface Raw : SearchResultAdapter {
    fun fetchRawItemIfExists(): Any?
  }

  data class Processed(
    private val adapter: SearchResultAdapter,
    val rawItem: Any?,
    val sessionWideId: SessionWideId?,
    val mlFeatures: List<EventPair<*>>?,
    val mlProbability: MlProbability?,
  ) : SearchResultAdapter by adapter {
    companion object {
      private const val MAX_ELEMENT_WEIGHT = 10_000
      private const val ML_PRIORITY_MULTIPLIER = 100_000
    }

    override fun toString(): String {
      return "Processed(" +
             "stateLocalId=${stateLocalId.value}}" +
             "rawItem=${rawItem?.javaClass?.simpleName}, " +
             "sessionWideId=${sessionWideId?.value}, " +
             "mlFeatures=${mlFeatures?.size}, " +
             "mlProbability=${mlProbability?.value})"
    }

    /**
     * Represents the final priority of a search result, determined by combining machine learning
     * probability, if available, and a default weight from the associated adapter.
     *
     * If machine learning (ML) probability is present, it is converted to a weight using the
     * [toWeight] method, which scales the probability value for scoring purposes. If ML probability
     * is not available, the original default weight provided by the associated search result adapter
     * is used as a fallback.
     */
    val finalPriority: Int
      get() {
        val effectiveMlWeight = mlProbability?.value?.let {
          if (rawItem is GotoActionModel.MatchedValue && rawItem.type == GotoActionModel.MatchedValueType.ABBREVIATION) 1.0
          else it
        } ?: return adapter.originalWeight

        return (effectiveMlWeight * MAX_ELEMENT_WEIGHT).toInt() * ML_PRIORITY_MULTIPLIER + adapter.originalWeight
      }
  }
}

private class SeItemDataAdapter(private val seItemData: SeItemData) : SearchResultAdapter.Raw {
  override fun fetchRawItemIfExists(): Any? = seItemData.fetchItemIfExists()?.rawObject

  override val provider: SearchResultProviderAdapter = SearchResultProviderAdapter.createAdapterFor(seItemData.providerId.value)
  override val originalWeight: Int = seItemData.weight
  override val providerWeight: Int? = seItemData.additionalInfo[SeItemDataKeys.PROVIDER_SORT_WEIGHT]?.toIntOrNull()
  override val isSemantic: Boolean = seItemData.isSemantic
  override val stateLocalId: StateLocalId
    get() = StateLocalId(requireNotNull(seItemData.uuid) { "UUID cannot be null for ${seItemData.javaClass.simpleName}" })
}

private class LegacyFoundElementInfoAdapter(private val foundElementInfo: SearchEverywhereFoundElementInfo) : SearchResultAdapter.Raw {
  override fun fetchRawItemIfExists(): Any? = foundElementInfo.element

  override val provider: SearchResultProviderAdapter = SearchResultProviderAdapter.createAdapterFor(foundElementInfo.contributor)

  override val originalWeight: Int
    get() = foundElementInfo.priority

  override val providerWeight: Int
    get() = foundElementInfo.contributor.sortWeight

  override val isSemantic: Boolean
    get() = (foundElementInfo.contributor as? SemanticSearchEverywhereContributor)?.isElementSemantic(foundElementInfo.element) ?: false

  override val stateLocalId: StateLocalId =
    StateLocalId(requireNotNull(foundElementInfo.uuid) { "UUID cannot be null for ${foundElementInfo::class.java.simpleName}" })
}
