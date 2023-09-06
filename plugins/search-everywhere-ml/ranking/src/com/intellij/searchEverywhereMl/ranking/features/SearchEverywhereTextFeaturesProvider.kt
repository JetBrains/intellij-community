package com.intellij.searchEverywhereMl.ranking.features

import com.intellij.find.impl.SearchEverywhereItem
import com.intellij.find.impl.TextSearchContributor
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.application.ApplicationManager
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereTextFeaturesProvider.Fields.IS_IN_COMMENT
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.util.asSafely
import com.intellij.util.codeInsight.CommentUtilCore

internal class SearchEverywhereTextFeaturesProvider : SearchEverywhereElementFeaturesProvider(TextSearchContributor::class.java) {
  override fun getFeaturesDeclarations(): List<EventField<*>> = listOf(
    IS_IN_COMMENT
  )

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: FeaturesProviderCache?): List<EventPair<*>> {
    val item = element.asSafely<SearchEverywhereItem>() ?: return emptyList()

    return buildList {
      isComment(item.usage)?.let { isComment ->
        add(IS_IN_COMMENT.with(isComment))
      }
    }
  }

  private fun isComment(usage: UsageInfo2UsageAdapter): Boolean? {
    val element = usage.usageInfo.psiFileRange.element ?: return null
    return ApplicationManager.getApplication().runReadAction<Boolean> { CommentUtilCore.isComment(element) }
  }


  private object Fields {
    val IS_IN_COMMENT = EventFields.Boolean("isInComment")
  }
}
