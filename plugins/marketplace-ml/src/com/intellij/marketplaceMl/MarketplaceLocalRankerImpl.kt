package com.intellij.marketplaceMl

import com.intellij.ide.plugins.marketplace.ranking.MarketplaceLocalRanker

internal class MarketplaceLocalRankerImpl : MarketplaceLocalRanker by MarketplaceLocalRankingService.getInstance()