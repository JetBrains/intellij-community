package com.intellij.searchEverywhereMl.ranking.core.features

import com.intellij.internal.statistic.local.FileTypeUsageSummary
import com.intellij.internal.statistic.local.FileTypeUsageSummaryProvider
import com.intellij.internal.statistic.local.LanguageUsageStatistics
import com.intellij.internal.statistic.local.LanguageUsageStatisticsProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.Contract


/**
 * The [FeaturesProviderCacheDataProvider] class provides data that should be cached in search session.
 * It's mostly used by feature providers that work with usage-related data or those that need to get the currently opened file.
 *
 * The rationale behind caching data in search session relates to how the Search Everywhere code works, and the order of certain events.
 * Without caching, if a user was to open a file from Search Everywhere, the IDE would first open the file and only then collect its
 * features that are later going to be reported. This means that any features that rely on usage statistics or currently opened file will be
 * incorrect, as that data will be too fresh for our purposes. Thus, we cache the data to remember the state before a file has been opened.
 */
class FeaturesProviderCacheDataProvider {
  @Contract("null -> null; !null -> new")
  fun getDataToCache(project: Project?): FeaturesProviderCache? {
    if (project == null) return null

    return FeaturesProviderCache(
      getCurrentlyOpenedFile(project),
      getUsageSortedLanguageStatsCopy(project),
      getFileTypeUsageStatsCopy(project),
    )
  }

  private fun getCurrentlyOpenedFile(project: Project) = FileEditorManager.getInstance(project).selectedEditor?.file

  /**
   * Creates a deep copy of the language usage data returned by the [LanguageUsageStatisticsProvider].
   * The returned map type is sorted based on the usage of languages
   */
  private fun getUsageSortedLanguageStatsCopy(project: Project): LinkedHashMap<String, LanguageUsageStatistics> {
    val service = project.service<LanguageUsageStatisticsProvider>()
    val languageStatistics = service.getStatistics()

    return languageStatistics
      .mapValues { it.value.copy() }
      .entries
      .sortedByDescending { it.value.useCount }
      .associateTo(LinkedHashMap()) { it.toPair() }
  }

  /**
   * Creates a deep copy of the file type usage data returned by the [FileTypeUsageSummaryProvider].
   */
  private fun getFileTypeUsageStatsCopy(project: Project): Map<String, FileTypeUsageSummary> {
    val service = project.service<FileTypeUsageSummaryProvider>()
    return service.getFileTypeStats()
      .mapValues {
        FileTypeUsageSummary(it.value.usageCount, it.value.lastUsed)
      }
  }
}

data class FeaturesProviderCache(val currentlyOpenedFile: VirtualFile?,
                                 val usageSortedLanguageStatistics: LinkedHashMap<String, LanguageUsageStatistics>,
                                 val fileTypeUsageStatistics: Map<String, FileTypeUsageSummary>)
