package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.local.FileTypeUsageLocalSummary
import com.intellij.internal.statistic.local.FileTypeUsageSummary
import com.intellij.internal.statistic.local.FileTypeUsageSummaryProvider
import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiInvalidElementAccessException
import com.intellij.util.Time

abstract class SearchEverywhereClassOrFileFeaturesProvider(vararg supportedTab: Class<out SearchEverywhereContributor<*>>)
  : SearchEverywhereElementFeaturesProvider(*supportedTab) {
  companion object {
    internal val IS_INVALID_DATA_KEY = EventFields.Boolean("isInvalid")

    internal val IS_SAME_MODULE_DATA_KEY = EventFields.Boolean("isSameModule")
    internal val PACKAGE_DISTANCE_DATA_KEY = EventFields.Int("packageDistance")
    internal val PACKAGE_DISTANCE_NORMALIZED_DATA_KEY = EventFields.Double("packageDistanceNorm")
    internal val IS_SAME_FILETYPE_AS_OPENED_FILE_DATA_KEY = EventFields.Boolean("isSameFileTypeAsOpenedFile")

    internal val IS_IN_SOURCE_DATA_KEY = EventFields.Boolean("isInSource")
    internal val IS_IN_TEST_SOURCES_DATA_KEY = EventFields.Boolean("isInTestSources")
    internal val IS_IN_LIBRARY_DATA_KEY = EventFields.Boolean("isFromLibrary")
    internal val IS_EXCLUDED_DATA_KEY = EventFields.Boolean("isInExcluded")

    internal val FILETYPE_USAGE_RATIO_DATA_KEY = EventFields.Double("fileTypeUsageRatio")
    internal val FILETYPE_USAGE_RATIO_TO_MAX_DATA_KEY = EventFields.Double("fileTypeUsageRatioToMax")
    internal val FILETYPE_USAGE_RATIO_TO_MIN_DATA_KEY = EventFields.Double("fileTypeUsageRatioToMin")
    internal val TIME_SINCE_LAST_FILETYPE_USAGE_DATA_KEY = EventFields.Long("timeSinceLastFileTypeUsage")
    internal val FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY = EventFields.Boolean("fileTypeUsedInLastMinute")
    internal val FILETYPE_USED_IN_LAST_HOUR_DATA_KEY = EventFields.Boolean("fileTypeUsedInLastHour")
    internal val FILETYPE_USED_IN_LAST_DAY_DATA_KEY = EventFields.Boolean("fileTypeUsedInLastDay")
    internal val FILETYPE_USED_IN_LAST_MONTH_DATA_KEY = EventFields.Boolean("fileTypeUsedInLastMonth")
  }

  override fun getDataToCache(project: Project?): Any? {
    if (project == null) {
      return null
    }

    val openedFile = FileEditorManager.getInstance(project).selectedEditor?.file
    return Cache(deepCopyFileTypeStats(project), openedFile)
  }

  override fun getFeaturesDeclarations(): List<EventField<*>> {
    return arrayListOf(
      IS_INVALID_DATA_KEY, IS_SAME_MODULE_DATA_KEY, PACKAGE_DISTANCE_DATA_KEY,
      PACKAGE_DISTANCE_NORMALIZED_DATA_KEY, IS_SAME_FILETYPE_AS_OPENED_FILE_DATA_KEY,
      IS_IN_SOURCE_DATA_KEY, IS_IN_TEST_SOURCES_DATA_KEY, IS_IN_LIBRARY_DATA_KEY,
      IS_EXCLUDED_DATA_KEY, FILETYPE_USAGE_RATIO_DATA_KEY,
      FILETYPE_USAGE_RATIO_TO_MAX_DATA_KEY, FILETYPE_USAGE_RATIO_TO_MIN_DATA_KEY,
      TIME_SINCE_LAST_FILETYPE_USAGE_DATA_KEY,
      FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY, FILETYPE_USED_IN_LAST_HOUR_DATA_KEY,
      FILETYPE_USED_IN_LAST_DAY_DATA_KEY, FILETYPE_USED_IN_LAST_MONTH_DATA_KEY
    )
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: Any?): List<EventPair<*>> {
    val item = when (element) {
      is PSIPresentationBgRendererWrapper.PsiItemWithPresentation -> element.item
      is PsiElement -> element
      else -> return emptyList()
    }

    val presentation = (element as? PSIPresentationBgRendererWrapper.PsiItemWithPresentation)?.presentation

    cache as Cache?
    val file = getContainingFile(item)
    val project = ReadAction.compute<Project?, Nothing> { item.takeIf { it.isValid }?.project } ?: return listOf(IS_INVALID_DATA_KEY.with(true))

    val data = ArrayList<EventPair<*>>()
    if (file != null && cache != null) {
      getFileFeatures(data, file, project, cache, currentTime)
    }
    data.addAll(getElementFeatures(item, presentation, currentTime, searchQuery, elementPriority, cache))
    return data
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
                              cache: Cache,
                              currentTime: Long) {
    data.addAll(getFileLocationStats(file, project))
    data.putIfValueNotNull(IS_SAME_FILETYPE_AS_OPENED_FILE_DATA_KEY, isSameFileTypeAsOpenedFile(file, cache.openedFile))
    data.putIfValueNotNull(IS_SAME_MODULE_DATA_KEY, isSameModuleAsOpenedFile(file, project, cache.openedFile))
    data.addAll(getFileTypeStats(file, currentTime, cache.fileTypeStats))

    calculatePackageDistance(file, project, cache.openedFile)?.let { (packageDistance, packageDistanceNorm) ->
      data.add(PACKAGE_DISTANCE_DATA_KEY.with(packageDistance))
      data.add(PACKAGE_DISTANCE_NORMALIZED_DATA_KEY.with(packageDistanceNorm))
    }
  }

  protected abstract fun getElementFeatures(element: PsiElement,
                                            presentation: TargetPresentation?,
                                            currentTime: Long,
                                            searchQuery: String,
                                            elementPriority: Int,
                                            cache: Cache?): List<EventPair<*>>

  /**
   * Creates a deep copy of the file type stats obtained from the [FileTypeUsageLocalSummary],
   * so they can be safely used without running into an issue whereupon search
   * result selection, the stats get updated before calculating the file features
   * resulting in a negative timeSinceLastFileTypeUsage.
   */
  private fun deepCopyFileTypeStats(project: Project): Map<String, FileTypeUsageSummary> {
    val service = project.service<FileTypeUsageSummaryProvider>()
    val statsCopy = service.getFileTypeStats().mapValues {
      FileTypeUsageSummary(it.value.usageCount, it.value.lastUsed)
    }

    return statsCopy
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

  /**
   * Calculates the package distance of the found [file] relative to the [openedFile].
   *
   * The distance can be considered the number of steps/changes to reach the other package,
   * for instance the distance to a parent or a child of a package is equal to 1,
   * and the distance from package a.b.c.d to package a.b.x.y is equal to 4.
   *
   * @return Pair of distance and normalized distance, or null if it could not be calculated.
   */
  private fun calculatePackageDistance(file: VirtualFile, project: Project, openedFile: VirtualFile?): Pair<Int, Double>? {
    if (openedFile == null) {
      return null
    }

    val (openedFilePackage, foundFilePackage) = ReadAction.compute<Pair<String?, String?>, Nothing> {
      val fileIndex = ProjectRootManager.getInstance(project).fileIndex

      // Parents of some files may still not be directories
      val openedFileDirectory = openedFile.parent?.takeIf { it.isDirectory }
      val foundFileDirectory = if (file.isDirectory) file else file.parent?.takeIf { it.isDirectory }

      val openedFilePackageName = openedFileDirectory?.let { fileIndex.getPackageNameByDirectory(it) }
      val foundFilePackageName = foundFileDirectory?.let { fileIndex.getPackageNameByDirectory(it) }

      Pair(openedFilePackageName, foundFilePackageName)
    }.run {
      fun splitPackage(s: String?) = if (s == null) {
        null
      }
      else if (s.isBlank()) {
        // In case the file is under a source root (src/testSrc/resource) and the package prefix is blank
        emptyList()
      }
      else {
        s.split('.')
      }

      Pair(splitPackage(first), splitPackage(second))
    }

    if (openedFilePackage == null || foundFilePackage == null) {
      return null
    }

    val maxDistance = openedFilePackage.size + foundFilePackage.size
    var common = 0
    for ((index, value) in openedFilePackage.withIndex()) {
      if (foundFilePackage.size == index || foundFilePackage[index] != value) {
        // Stop counting if the found file package is a parent or it no longer matches the opened file package
        break
      }

      common++
    }

    val distance = maxDistance - 2 * common
    val normalizedDistance = roundDouble(if (maxDistance != 0) (distance.toDouble() / maxDistance) else 0.0)
    return Pair(distance, normalizedDistance)
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

  protected data class Cache(val fileTypeStats: Map<String, FileTypeUsageSummary>, val openedFile: VirtualFile?)
}