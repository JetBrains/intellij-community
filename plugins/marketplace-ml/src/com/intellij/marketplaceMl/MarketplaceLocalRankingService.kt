// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.marketplaceMl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.ide.plugins.marketplace.ranking.MarketplaceLocalRanker
import com.intellij.ide.plugins.marketplace.statistics.features.PluginManagerMarketplaceSearchFeatureProvider
import com.intellij.ide.plugins.marketplace.statistics.features.PluginManagerSearchResultFeatureProvider
import com.intellij.ide.plugins.marketplace.statistics.features.PluginManagerSearchResultsFeatureProvider
import com.intellij.ide.plugins.marketplace.statistics.features.PluginManagerUserQueryFeatureProvider
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.SearchQueryParser
import com.intellij.marketplaceMl.MarketplaceMLExperiment.ExperimentOption
import com.intellij.marketplaceMl.model.MarketplaceRankingModel
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@Service(Service.Level.APP)
class MarketplaceLocalRankingService : MarketplaceLocalRanker {
  private val modelCache: Cache<Unit, MarketplaceRankingModel> =
    Caffeine.newBuilder().expireAfterAccess(60.seconds.toJavaDuration()).maximumSize(1).build()
  val model: MarketplaceRankingModel
    get() = modelCache.get(Unit) { MarketplaceRankingModel() }

  override fun isEnabled(): Boolean = MarketplaceMLExperiment.getExperiment() == ExperimentOption.USE_ML

  override fun rankPlugins(
    queryParser: SearchQueryParser.Marketplace,
    plugins: MutableList<PluginUiModel>
  ): Map<PluginUiModel, Double> {
    val pluginToScore = mutableMapOf<PluginUiModel, Double>()
    val searchQuery = queryParser.searchQuery

    val queryFeatures = PluginManagerUserQueryFeatureProvider.getSearchStateFeatures(searchQuery)
    val marketplaceFeatures = PluginManagerMarketplaceSearchFeatureProvider.getSearchStateFeatures(queryParser)
    val commonResultFeatures = PluginManagerSearchResultsFeatureProvider.getCommonFeatures(searchQuery, plugins)
    val allItemFeatures = plugins.map { PluginManagerSearchResultFeatureProvider.getSearchStateFeatures(searchQuery, it) }

    for ((index, pluginWithFeatures) in (plugins zip allItemFeatures).withIndex()) {
      val (plugin, itemFeatures) = pluginWithFeatures
      val allFeatures = queryFeatures + marketplaceFeatures + commonResultFeatures + itemFeatures
      val featuresMap = allFeatures.associate { it.field.name to it.data }
      // TODO: replace with model prediction once we have a trained model
      pluginToScore[plugin] = (plugins.size - index).toDouble() / plugins.size
      // pluginToScore[plugin] = model.predictScore(featuresMap)
    }

    plugins.sortByDescending { pluginToScore[it] }
    return pluginToScore
  }

  override val experimentGroup: Int
    get() = MarketplaceMLExperiment.experimentGroup

  override val experimentVersion: Int
    get() = MarketplaceMLExperiment.VERSION

  companion object {
    fun getInstance(): MarketplaceLocalRankingService = service()
  }
}