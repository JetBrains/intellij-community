package com.intellij.searchEverywhereMl.ranking.vcs

import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.psi.PsiFileSystemItem
import com.intellij.searchEverywhereMl.ranking.core.features.FeaturesProviderCache
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereElementFeaturesProvider
import com.intellij.searchEverywhereMl.ranking.vcs.SearchEverywhereVcsFileFeaturesProvider.Fields.FILE_STATUS_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.vcs.SearchEverywhereVcsFileFeaturesProvider.Fields.IS_CHANGED_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.vcs.SearchEverywhereVcsFileFeaturesProvider.Fields.IS_IGNORED_DATA_KEY

class SearchEverywhereVcsFileFeaturesProvider : SearchEverywhereElementFeaturesProvider(FileSearchEverywhereContributor::class.java) {
  object Fields {
    val IS_IGNORED_DATA_KEY = EventFields.Boolean("isIgnored")
    val IS_CHANGED_DATA_KEY = EventFields.Boolean("isChanged")
    val FILE_STATUS_DATA_KEY = EventFields.String(
      "fileStatus",
      listOf(
        "NOT_CHANGED", "NOT_CHANGED_IMMEDIATE", "NOT_CHANGED_RECURSIVE",
        "DELETED", "MODIFIED", "ADDED", "MERGED", "UNKNOWN",
        "IDEA_FILESTATUS_IGNORED", "HIJACKED",
        "IDEA_FILESTATUS_MERGED_WITH_CONFLICTS", "IDEA_FILESTATUS_MERGED_WITH_BOTH_CONFLICTS",
        "IDEA_FILESTATUS_MERGED_WITH_PROPERTY_CONFLICTS", "IDEA_FILESTATUS_DELETED_FROM_FILE_SYSTEM",
        "SWITCHED", "OBSOLETE", "SUPPRESSED"
      )
    )
  }

  override fun getFeaturesDeclarations(): List<EventField<*>> {
    return listOf(IS_IGNORED_DATA_KEY, IS_CHANGED_DATA_KEY, FILE_STATUS_DATA_KEY)
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: FeaturesProviderCache?,
                                  correction: SearchEverywhereSpellCheckResult): List<EventPair<*>> {
    val item = when (element) {
      is PSIPresentationBgRendererWrapper.PsiItemWithPresentation -> (element.item as? PsiFileSystemItem) ?: return emptyList()
      is PsiFileSystemItem -> element
      else -> return emptyList()
    }

    return getFileFeatures(item)
  }

  private fun getFileFeatures(item: PsiFileSystemItem): List<EventPair<*>> {
    if (item.isDirectory) {
      return emptyList()
    }

    val changeListManager = ChangeListManager.getInstance(item.project)

    return arrayListOf(
      IS_CHANGED_DATA_KEY.with(changeListManager.isFileAffected(item.virtualFile)),
      IS_IGNORED_DATA_KEY.with(changeListManager.isIgnoredFile(item.virtualFile)),
      FILE_STATUS_DATA_KEY.with(changeListManager.getStatus(item.virtualFile).id)
    )
  }
}