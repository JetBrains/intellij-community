package com.intellij.marketplaceMl

import com.intellij.ide.plugins.marketplace.ranking.MarketplaceLocalRanker

private class MarketplaceLocalRankerImpl : MarketplaceLocalRanker by MarketplaceLocalRankingService.getInstance()