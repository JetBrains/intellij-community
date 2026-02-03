package com.intellij.lang.properties.diff

import com.intellij.diff.comparison.ComparisonManagerImpl
import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.lang.DiffLangSpecificProvider
import com.intellij.diff.tools.util.base.HighlightPolicy
import com.intellij.diff.tools.util.base.IgnorePolicy
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import com.intellij.lang.Language
import com.intellij.lang.properties.PropertiesBundle
import com.intellij.lang.properties.PropertiesLanguage
import com.intellij.lang.properties.diff.data.FileInfoHolder
import com.intellij.lang.properties.diff.data.PropertyInfo
import com.intellij.lang.properties.diff.data.SemiOpenLineRange
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls

/**
 * Extension for implementing the semantic-based algorithm for properties difference.
 *
 * Each property might be in 1 of 4 states:
 * 1. Property (value) is unchanged. Algorithm ignores such property even if it was moved
 * 2. Property (value) is changed. In this case algorithm provides the *modification* highlighting based on the property ranges in both versions on the file.
 * 3. Property is added (there was no such key in the previous version of the file). In this case algorithm provides the *addition* highlighting.
 * 4. Property is removed (there was no such key in the new version of the file). In this case algorithm provides the *deletion* highlighting.
 *
 * For cases *3* and *4* algorithm uses the other properties to find the anchor ranges in the opposite side.
 *
 * For example, text before:
 * ```properties
 * key1=value1
 *
 * # some comment
 * ```
 * text after:
 * ```properties
 * key1=value1
 *
 * # some comment
 * key2=value2
 * ```
 * In this scenario algorithm will consider that property was added directly below the `key1=value1` property ignoring comments and blank lines.
 */
class PropertiesDiffLangSpecificProvider : DiffLangSpecificProvider {
  override val shouldPrecalculateLineFragments: Boolean = false

  override val description: @Nls(capitalization = Nls.Capitalization.Sentence) String
    get() = PropertiesBundle.message("ignore.unchanged.properties.text")

  override fun isApplicable(language: Language): Boolean = language == PropertiesLanguage.INSTANCE

  override fun getLineFragments(
    project: Project?,
    textLeft: CharSequence,
    textRight: CharSequence,
    ignorePolicy: IgnorePolicy,
    highlightPolicy: HighlightPolicy,
    indicator: ProgressIndicator,
  ): List<List<LineFragment>> {
    indicator.checkCanceled()
    if (project == null) return emptyList()
    val fileInfoHolder = ReadAction.compute<FileInfoHolder?, Exception> { FileInfoHolder.create(project, textLeft, textRight) }
    if (fileInfoHolder == null) return emptyList()
    val ranges = computePropertiesRanges(fileInfoHolder)
    val lineFragmentList = ranges.sortedWith(compareBy({ it.start1 }, { it.start2 }))
      .flatMap { range -> createLineFragments(range, fileInfoHolder, ignorePolicy, highlightPolicy, indicator) }
    return if (lineFragmentList.isEmpty()) emptyList() else listOf(lineFragmentList)
  }

  private fun computePropertiesRanges(fileInfoHolder: FileInfoHolder): List<Range> {
    val leftPropertiesMap = fileInfoHolder[Side.LEFT].propertyInfoMap
    val rightPropertiesMap = fileInfoHolder[Side.RIGHT].propertyInfoMap
    val commonPropertiesKeys = leftPropertiesMap.keys.intersect(rightPropertiesMap.keys)

    val modifiedPropertiesRanges = computeModifiedPropertiesRanges(commonPropertiesKeys, leftPropertiesMap, rightPropertiesMap)
    val deletedPropertiesRanges = computeAddedOrDeletedPropertiesRanges(commonPropertiesKeys, fileInfoHolder, Side.LEFT)
    val addedPropertiesRanges = computeAddedOrDeletedPropertiesRanges(commonPropertiesKeys, fileInfoHolder, Side.RIGHT)

    return modifiedPropertiesRanges + deletedPropertiesRanges + addedPropertiesRanges
  }

  private fun computeCommonProperties(commonPropertiesKeys: Set<String>, fileInfoHolder: FileInfoHolder, side: Side): List<PropertyInfo> {
    return fileInfoHolder[side].propertyInfoMap.mapNotNull { if (it.key !in commonPropertiesKeys) null else it.value }
  }

  private fun computeModifiedPropertiesRanges(
    commonPropertiesKeys: Set<String>,
    leftProperties: Map<String, PropertyInfo>,
    rightProperties: Map<String, PropertyInfo>,
  ): List<Range> {
    val modifiedPropertiesRangeList = commonPropertiesKeys.mapNotNull { key ->
      val propertyBefore = leftProperties.getValue(key)
      val propertyAfter = rightProperties.getValue(key)
      if (propertyBefore.value == propertyAfter.value) return@mapNotNull null
      Range(propertyBefore.range.startLine, propertyBefore.range.endLine,
            propertyAfter.range.startLine, propertyAfter.range.endLine)
    }
    return squashRanges(modifiedPropertiesRangeList)
  }

  private fun computeAddedOrDeletedPropertiesRanges(commonPropertiesKeys: Set<String>, fileInfoHolder: FileInfoHolder, currentSide: Side): List<Range> {
    val commonPropertiesList = computeCommonProperties(commonPropertiesKeys, fileInfoHolder, currentSide)
    val oppositeSide = currentSide.other()
    val currentPropertiesMap = fileInfoHolder[currentSide].propertyInfoMap
    val oppositePropertiesMap = fileInfoHolder[oppositeSide].propertyInfoMap

    var anchorIndex = 0
    val addedOrDeletedPropertiesRangeList = currentPropertiesMap.mapNotNull { (_, propertyInfo) ->
      if (propertyInfo.key in commonPropertiesKeys) return@mapNotNull null
      val propertyRange = propertyInfo.range

      val anchorRange = if (commonPropertiesList.isEmpty()) {
        SemiOpenLineRange(0, 0)
      } else if (commonPropertiesList.first().range.startLine > propertyRange.startLine) {
        extractAnchorRange(commonPropertiesList.first(), oppositePropertiesMap, false)
      } else {
        while (anchorIndex < commonPropertiesList.size) {
          val nextAnchorIndex = anchorIndex + 1
          val nextAnchorInfo = commonPropertiesList.getOrNull(nextAnchorIndex)
          if (nextAnchorInfo == null || nextAnchorInfo.range.startLine > propertyRange.startLine) break
          anchorIndex = nextAnchorIndex
        }
        extractAnchorRange(commonPropertiesList[anchorIndex], oppositePropertiesMap, true)
      }
      currentSide.createRange(propertyRange, anchorRange)
    }
    return squashRanges(addedOrDeletedPropertiesRangeList)
  }

  private fun extractAnchorRange(propertyInfo: PropertyInfo, oppositePropertiesMap: Map<String, PropertyInfo>, isEndBound: Boolean): SemiOpenLineRange {
    val range = oppositePropertiesMap.getValue(propertyInfo.key).range
    val line = if (isEndBound) range.endLine else range.startLine
    return SemiOpenLineRange(line, line)
  }

  private fun squashRanges(rangeList: List<Range>): List<Range> {
    var leftIndex = 0
    val result = mutableListOf<Range>()
    while (leftIndex < rangeList.size) {
      var rightIndex = leftIndex + 1
      while (rightIndex < rangeList.size &&
             rangeList[rightIndex - 1].end1 == rangeList[rightIndex].start1 &&
             rangeList[rightIndex - 1].end2 == rangeList[rightIndex].start2) {
        rightIndex++
      }
      val newRange = if (leftIndex == rightIndex - 1) {
        rangeList[leftIndex]
      }
      else {
        Range(rangeList[leftIndex].start1, rangeList[rightIndex - 1].end1, rangeList[leftIndex].start2, rangeList[rightIndex - 1].end2)
      }
      result.add(newRange)
      leftIndex = rightIndex
    }
    return result
  }

  private fun Side.createRange(first: SemiOpenLineRange, second: SemiOpenLineRange): Range = when (this) {
    Side.LEFT -> Range(first.startLine, first.endLine, second.startLine, second.endLine)
    Side.RIGHT -> Range(second.startLine, second.endLine, first.startLine, first.endLine)
  }

  private fun createLineFragments(
    range: Range,
    fileInfoHolder: FileInfoHolder,
    ignorePolicy: IgnorePolicy,
    highlightPolicy: HighlightPolicy,
    indicator: ProgressIndicator,
  ): List<LineFragment> {
    val manager = ComparisonManagerImpl.getInstanceImpl()
    return manager.compareLinesInner(
      range,
      fileInfoHolder[Side.LEFT].fileText,
      fileInfoHolder[Side.RIGHT].fileText,
      fileInfoHolder[Side.LEFT].lineOffsets,
      fileInfoHolder[Side.RIGHT].lineOffsets,
      ignorePolicy.comparisonPolicy,
      highlightPolicy.fragmentsPolicy,
      indicator
    )
  }
}