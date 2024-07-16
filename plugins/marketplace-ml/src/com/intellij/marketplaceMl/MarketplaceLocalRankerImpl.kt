package com.intellij.marketplaceMl

import com.intellij.ide.plugins.marketplace.ranking.MarketplaceLocalRanker

val rankingService
  get() = MarketplaceLocalRankingService.getInstance()

class MarketplaceLocalRankerImpl : MarketplaceLocalRanker by rankingService