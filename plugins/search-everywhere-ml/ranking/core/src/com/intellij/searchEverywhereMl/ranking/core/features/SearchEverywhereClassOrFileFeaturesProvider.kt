package com.intellij.searchEverywhereMl.ranking.core.features

import com.intellij.filePrediction.features.history.FileHistoryManagerWrapper
import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.RecentFilesSEContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.local.FileTypeUsageSummary
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiInvalidElementAccessException
import com.intellij.psi.PsiNamedElement
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.ALL_INITIAL_LETTERS_MATCH_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.DIRECTORY_DEPTH_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.FILETYPE_USAGE_RATIO_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.FILETYPE_USAGE_RATIO_TO_MAX_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.FILETYPE_USAGE_RATIO_TO_MIN_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.FILETYPE_USED_IN_LAST_DAY_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.FILETYPE_USED_IN_LAST_HOUR_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.FILETYPE_USED_IN_LAST_MONTH_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.IS_ACCESSIBLE_FROM_MODULE
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.IS_EXCLUDED_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.IS_IN_LIBRARY_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.IS_IN_SOURCE_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.IS_IN_TEST_SOURCES_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.IS_OPENED_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.IS_SAME_FILETYPE_AS_OPENED_FILE_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.IS_SAME_MODULE_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.PREDICTION_SCORE_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.RECENT_INDEX_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.TIME_SINCE_LAST_FILETYPE_USAGE_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.TIME_SINCE_LAST_MODIFICATION_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.WAS_MODIFIED_IN_LAST_DAY_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereClassOrFileFeaturesProvider.Fields.WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywherePsiElementFeaturesProvider.Fields.IS_INVALID_DATA_KEY
import com.intellij.util.Time
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SearchEverywhereClassOrFileFeaturesProvider : SearchEverywhereElementFeaturesProvider(
  ClassSearchEverywhereContributor::class.java,
  FileSearchEverywhereContributor::class.java,
  RecentFilesSEContributor::class.java
) {
  object Fields {
    val IS_ACCESSIBLE_FROM_MODULE = EventFields.Boolean("isAccessibleFromModule")

    val IS_SAME_MODULE_DATA_KEY = EventFields.Boolean("isSameModule")

    val DIRECTORY_DEPTH_DATA_KEY = EventFields.Int("directoryDepth")
    val IS_SAME_FILETYPE_AS_OPENED_FILE_DATA_KEY = EventFields.Boolean("isSameFileTypeAsOpenedFile")

    val IS_IN_SOURCE_DATA_KEY = EventFields.Boolean("isInSource")
    val IS_IN_TEST_SOURCES_DATA_KEY = EventFields.Boolean("isInTestSources")
    val IS_IN_LIBRARY_DATA_KEY = EventFields.Boolean("isFromLibrary")
    val IS_EXCLUDED_DATA_KEY = EventFields.Boolean("isInExcluded")

    val FILETYPE_USAGE_RATIO_DATA_KEY = EventFields.Double("fileTypeUsageRatio")
    val FILETYPE_USAGE_RATIO_TO_MAX_DATA_KEY = EventFields.Double("fileTypeUsageRatioToMax")
    val FILETYPE_USAGE_RATIO_TO_MIN_DATA_KEY = EventFields.Double("fileTypeUsageRatioToMin")
    val TIME_SINCE_LAST_FILETYPE_USAGE_DATA_KEY = EventFields.Long("timeSinceLastFileTypeUsage")
    val FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY = EventFields.Boolean("fileTypeUsedInLastMinute")
    val FILETYPE_USED_IN_LAST_HOUR_DATA_KEY = EventFields.Boolean("fileTypeUsedInLastHour")
    val FILETYPE_USED_IN_LAST_DAY_DATA_KEY = EventFields.Boolean("fileTypeUsedInLastDay")
    val FILETYPE_USED_IN_LAST_MONTH_DATA_KEY = EventFields.Boolean("fileTypeUsedInLastMonth")

    val RECENT_INDEX_DATA_KEY = EventFields.Int("recentFilesIndex")
    val PREDICTION_SCORE_DATA_KEY = EventFields.Double("predictionScore")

    val IS_OPENED_DATA_KEY = EventFields.Boolean("isOpened")

    val TIME_SINCE_LAST_MODIFICATION_DATA_KEY = EventFields.Long("timeSinceLastModification")
    val WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY = EventFields.Boolean("wasModifiedInLastMinute")
    val WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY = EventFields.Boolean("wasModifiedInLastHour")
    val WAS_MODIFIED_IN_LAST_DAY_DATA_KEY = EventFields.Boolean("wasModifiedInLastDay")
    val WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY = EventFields.Boolean("wasModifiedInLastMonth")

    val ALL_INITIAL_LETTERS_MATCH_DATA_KEY = EventFields.Boolean("allInitialLettersMatch")
  }

  override fun getFeaturesDeclarations(): List<EventField<*>> {
    return arrayListOf(
      IS_ACCESSIBLE_FROM_MODULE,
      IS_SAME_MODULE_DATA_KEY, DIRECTORY_DEPTH_DATA_KEY,
      IS_SAME_FILETYPE_AS_OPENED_FILE_DATA_KEY,
      IS_IN_SOURCE_DATA_KEY, IS_IN_TEST_SOURCES_DATA_KEY, IS_IN_LIBRARY_DATA_KEY,
      IS_EXCLUDED_DATA_KEY, FILETYPE_USAGE_RATIO_DATA_KEY,
      FILETYPE_USAGE_RATIO_TO_MAX_DATA_KEY, FILETYPE_USAGE_RATIO_TO_MIN_DATA_KEY,
      TIME_SINCE_LAST_FILETYPE_USAGE_DATA_KEY,
      FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY, FILETYPE_USED_IN_LAST_HOUR_DATA_KEY,
      FILETYPE_USED_IN_LAST_DAY_DATA_KEY, FILETYPE_USED_IN_LAST_MONTH_DATA_KEY,
      RECENT_INDEX_DATA_KEY, PREDICTION_SCORE_DATA_KEY,
      TIME_SINCE_LAST_MODIFICATION_DATA_KEY, WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY,
      WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY, WAS_MODIFIED_IN_LAST_DAY_DATA_KEY,
      WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY, IS_OPENED_DATA_KEY,
      ALL_INITIAL_LETTERS_MATCH_DATA_KEY,
    )
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: FeaturesProviderCache?,
                                  correction: SearchEverywhereSpellCheckResult): List<EventPair<*>> {
    val item = SearchEverywherePsiElementFeaturesProviderUtils.getPsiElement(element)
    val file = getContainingFile(item)

    val project = ReadAction.compute<Project?, Nothing> {
      item.takeIf { it.isValid }?.project
    } ?: return listOf(IS_INVALID_DATA_KEY.with(true))

    val data = ArrayList<EventPair<*>>()
    if (file != null && cache != null) {
      getFileFeatures(data, file, project, cache, currentTime)
    }

    if (item !is PsiFileSystemItem) {
      data.addAll(isAccessibleFromModule(item, cache?.currentlyOpenedFile))
    }

    data.add(ALL_INITIAL_LETTERS_MATCH_DATA_KEY.with(allInitialLettersMatch(item, searchQuery)))

    return data
  }

  private fun isAccessibleFromModule(element: PsiElement, openedFile: VirtualFile?): List<EventPair<*>> {
    return openedFile?.let {
      ReadAction.compute<List<EventPair<*>>, Nothing> {
        if (!element.isValid) return@compute arrayListOf(IS_INVALID_DATA_KEY.with(true))

        val elementFile = element.containingFile?.virtualFile ?: return@compute emptyList()
        val fileIndex = ProjectRootManager.getInstance(element.project).fileIndex

        val openedFileModule = fileIndex.getModuleForFile(it)
        val elementModule = fileIndex.getModuleForFile(elementFile)

        if (openedFileModule == null || elementModule == null) return@compute emptyList()

        return@compute arrayListOf(
          IS_ACCESSIBLE_FROM_MODULE.with(elementModule.name in ModuleRootManager.getInstance(openedFileModule).dependencyModuleNames)
        )
      }
    } ?: emptyList()
  }

  private fun getContainingFile(item: PsiElement) = if (item is PsiFileSystemItem) {
    item.virtualFile
  }
  else {
    ReadAction.compute<VirtualFile?, Nothing> {
      try {
        item.containingFile?.virtualFile
      }
      catch (ex: PsiInvalidElementAccessException) {
        null
      }
    }
  }

  private fun getFileFeatures(data: MutableList<EventPair<*>>,
                              file: VirtualFile,
                              project: Project,
                              cache: FeaturesProviderCache,
                              currentTime: Long) {
    data.addAll(getFileLocationStats(file, project))
    data.putIfValueNotNull(IS_SAME_FILETYPE_AS_OPENED_FILE_DATA_KEY, isSameFileTypeAsOpenedFile(file, cache.currentlyOpenedFile))
    data.putIfValueNotNull(IS_SAME_MODULE_DATA_KEY, isSameModuleAsOpenedFile(file, project, cache.currentlyOpenedFile))
    data.addAll(getFileTypeStats(file, currentTime, cache.fileTypeUsageStatistics))

    data.add(RECENT_INDEX_DATA_KEY.with(getRecentFilesIndex(file, project)))
    data.add(PREDICTION_SCORE_DATA_KEY.with(getPredictionScore(file, project)))

    data.addAll(getModificationTimeStats(file, currentTime))
    data.add(IS_OPENED_DATA_KEY.with(isOpened(file, project)))

    calculateRootDistance(file, project)?.let {
      data.add(DIRECTORY_DEPTH_DATA_KEY.with(it))
    }
  }

  private fun getFileTypeStats(file: VirtualFile,
                               currentTime: Long,
                               fileTypeStats: Map<String, FileTypeUsageSummary>): List<EventPair<*>> {
    val totalUsage = fileTypeStats.values.sumOf { it.usageCount }
    val stats = fileTypeStats[file.fileType.name]

    if (stats == null) {
      return emptyList()
    }

    val timeSinceLastUsage = currentTime - stats.lastUsed
    val usageRatio = roundDouble(stats.usageCount.toDouble() / totalUsage)
    val min = fileTypeStats.minOf { it.value.usageCount }
    val max = fileTypeStats.maxOf { it.value.usageCount }

    return arrayListOf(
      FILETYPE_USAGE_RATIO_DATA_KEY.with(usageRatio),
      FILETYPE_USAGE_RATIO_TO_MAX_DATA_KEY.with(roundDouble(stats.usageCount.toDouble() / max)),
      FILETYPE_USAGE_RATIO_TO_MIN_DATA_KEY.with(roundDouble(stats.usageCount.toDouble() / min)),

      TIME_SINCE_LAST_FILETYPE_USAGE_DATA_KEY.with(timeSinceLastUsage),
      FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY.with(timeSinceLastUsage <= Time.MINUTE),
      FILETYPE_USED_IN_LAST_HOUR_DATA_KEY.with(timeSinceLastUsage <= Time.HOUR),
      FILETYPE_USED_IN_LAST_DAY_DATA_KEY.with(timeSinceLastUsage <= Time.DAY),
      FILETYPE_USED_IN_LAST_MONTH_DATA_KEY.with(timeSinceLastUsage <= (4 * Time.WEEK.toLong()))
    )
  }

  private fun isSameModuleAsOpenedFile(file: VirtualFile, project: Project, openedFile: VirtualFile?): Boolean? {
    if (openedFile == null) {
      return null
    }

    val (openedFileModule, itemModule) = ReadAction.compute<Pair<Module?, Module?>, Nothing> {
      val fileIndex = ProjectRootManager.getInstance(project).fileIndex
      Pair(fileIndex.getModuleForFile(openedFile), fileIndex.getModuleForFile(file))
    }

    if (openedFileModule == null || itemModule == null) {
      return null
    }

    return openedFileModule == itemModule
  }


  private fun calculateRootDistance(file: VirtualFile, project: Project): Int? {
    val contentRoot = project.guessProjectDir() ?: return null

    val fileDirectoryPath = file.parent?.toNioPathOrNull() ?: return null
    val contentRootPath = contentRoot.toNioPathOrNull() ?: return null

    if (!fileDirectoryPath.startsWith(contentRootPath)) return null

    val relativePath = contentRootPath.relativize(fileDirectoryPath)

    // Empty path still has nameCount 1
    if (relativePath.toString().isEmpty()) return 0

    return relativePath.nameCount
  }

  private fun isSameFileTypeAsOpenedFile(file: VirtualFile, openedFile: VirtualFile?): Boolean? {
    val openedFileType = openedFile?.fileType ?: return null
    return FileTypeRegistry.getInstance().isFileOfType(file, openedFileType)
  }

  private fun getFileLocationStats(file: VirtualFile, project: Project): List<EventPair<*>> {
    return ReadAction.compute<List<EventPair<*>>, Nothing> {
      val fileIndex = ProjectFileIndex.getInstance(project)

      return@compute arrayListOf(
        IS_IN_SOURCE_DATA_KEY.with(fileIndex.isInSource(file)),
        IS_IN_TEST_SOURCES_DATA_KEY.with(fileIndex.isInTestSourceContent(file)),
        IS_IN_LIBRARY_DATA_KEY.with(fileIndex.isInLibrary(file)),
        IS_EXCLUDED_DATA_KEY.with(fileIndex.isExcluded(file)),
      )
    }
  }

  private fun getRecentFilesIndex(virtualFile: VirtualFile, project: Project): Int {
    val historyManager = EditorHistoryManager.getInstance(project)
    val recentFilesList = historyManager.fileList

    val fileIndex = recentFilesList.indexOf(virtualFile)
    if (fileIndex == -1) {
      return fileIndex
    }

    // Give the most recent files the lowest index value
    return recentFilesList.size - fileIndex
  }

  private fun getModificationTimeStats(virtualFile: VirtualFile, currentTime: Long): List<EventPair<*>> {
    val timeSinceLastMod = currentTime - virtualFile.timeStamp

    return arrayListOf<EventPair<*>>(
      TIME_SINCE_LAST_MODIFICATION_DATA_KEY.with(timeSinceLastMod),
      WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY.with((timeSinceLastMod <= Time.MINUTE)),
      WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY.with((timeSinceLastMod <= Time.HOUR)),
      WAS_MODIFIED_IN_LAST_DAY_DATA_KEY.with((timeSinceLastMod <= Time.DAY)),
      WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY.with((timeSinceLastMod <= (4 * Time.WEEK.toLong())))
    )
  }

  private fun getPredictionScore(virtualFile: VirtualFile, project: Project): Double {
    val historyManagerWrapper = FileHistoryManagerWrapper.getInstance(project)
    val probability = historyManagerWrapper.calcNextFileProbability(virtualFile)
    return roundDouble(probability)
  }

  private fun isOpened(virtualFile: VirtualFile, project: Project): Boolean {
    val openedFiles = FileEditorManager.getInstance(project).openFiles
    return virtualFile in openedFiles
  }

  private fun allInitialLettersMatch(element: PsiElement, query: String): Boolean {
    val elementName = when (element) {
      is PsiFileSystemItem -> element.virtualFile.nameWithoutExtension
      is PsiNamedElement -> element.name ?: return false
      else -> return false
    }

    // Transform the element name, so that the match yields true for the following three cases
    // - PascalCaseNames
    // - camelCaseNames
    // - snake_case_names
    val transformedElementName = elementName.split("_")
      .joinToString { substring -> substring.replaceFirstChar { it.uppercase() } }
      .filter { it.isUpperCase() }

    return query.filter { it.isUpperCase() } == transformedElementName
  }
}