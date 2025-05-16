package com.intellij.searchEverywhereMl.ranking.java.features

import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.RecentFilesSEContributor
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.PackageIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiInvalidElementAccessException
import com.intellij.searchEverywhereMl.ranking.core.features.FeaturesProviderCache
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereElementFeaturesProvider
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywherePsiElementFeaturesProviderUtils
import com.intellij.searchEverywhereMl.ranking.java.features.SearchEverywhereJavaPsiElementFeatureProvider.Fields.PACKAGE_DISTANCE_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.java.features.SearchEverywhereJavaPsiElementFeatureProvider.Fields.PACKAGE_DISTANCE_NORMALIZED_DATA_KEY
import kotlin.math.round

internal class SearchEverywhereJavaPsiElementFeatureProvider : SearchEverywhereElementFeaturesProvider(
  ClassSearchEverywhereContributor::class.java,
  FileSearchEverywhereContributor::class.java,
  RecentFilesSEContributor::class.java,
) {
  override fun getFeaturesDeclarations(): List<EventField<*>> = listOf(
    PACKAGE_DISTANCE_DATA_KEY, PACKAGE_DISTANCE_NORMALIZED_DATA_KEY
  )

  override fun getElementFeatures(element: Any, currentTime: Long, searchQuery: String, elementPriority: Int, cache: FeaturesProviderCache?): List<EventPair<*>> {
    val psiElement = SearchEverywherePsiElementFeaturesProviderUtils.getPsiElement(element) ?: return emptyList()

    val file = getContainingFile(psiElement) ?: return emptyList()

    val openedFile = cache?.currentlyOpenedFile
    val (packageDistance, normalizedPackageDistance) = calculatePackageDistance(file, psiElement.project, openedFile) ?: return emptyList()

    return listOf(
      PACKAGE_DISTANCE_DATA_KEY.with(packageDistance),
      PACKAGE_DISTANCE_NORMALIZED_DATA_KEY.with(normalizedPackageDistance),
    )
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
      val packageIndex = PackageIndex.getInstance(project)

      // Parents of some files may still not be directories
      val openedFilePackageName = packageIndex.getPackageName(openedFile)
      val foundFilePackageName = packageIndex.getPackageName(file)

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

  private fun getContainingFile(item: PsiElement): VirtualFile? {
    if (item is PsiFileSystemItem) {
      return item.virtualFile
    }

    return ReadAction.compute<VirtualFile?, Nothing> {
      try {
        item.containingFile?.virtualFile
      }
      catch (ex: PsiInvalidElementAccessException) {
        null
      }
    }
  }

  private fun roundDouble(value: Double): Double {
    if (!value.isFinite()) return -1.0
    return round(value * 100000) / 100000
  }

  internal object Fields {
    val PACKAGE_DISTANCE_DATA_KEY = EventFields.Int("packageDistance")
    val PACKAGE_DISTANCE_NORMALIZED_DATA_KEY = EventFields.Double("packageDistanceNorm")
  }
}
