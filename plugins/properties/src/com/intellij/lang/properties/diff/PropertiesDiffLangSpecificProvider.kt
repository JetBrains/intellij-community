// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.lang.properties.diff.data.PropertiesDiffContext
import com.intellij.lang.properties.diff.data.SemiOpenLineRange
import com.intellij.lang.properties.diff.data.UnchangedRangeInfo
import com.intellij.lang.properties.diff.util.toLastLineRange
import com.intellij.lang.properties.diff.util.toLineRange
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls

class PropertiesDiffLangSpecificProvider : DiffLangSpecificProvider {
  override val shouldPrecalculateLineFragments: Boolean
    get() = true

  override val description: @Nls(capitalization = Nls.Capitalization.Sentence) String
    get() = PropertiesBundle.message("ignore.unchanged.properties.text")

  override fun isApplicable(language: Language): Boolean = language == PropertiesLanguage.INSTANCE

  override fun getPatchedLineFragments(project: Project?, fragmentList: List<List<LineFragment>>, textLeft: CharSequence, textRight: CharSequence, ignorePolicy: IgnorePolicy, highlightPolicy: HighlightPolicy, indicator: ProgressIndicator): List<List<LineFragment>> {
    indicator.checkCanceled()
    if (project == null) return emptyList()
    val innerFragmentList = fragmentList.flatten()
    val contextResult = ReadAction.compute<Result<PropertiesDiffContext>, Exception> {
      try {
        val fileInfoHolder = FileInfoHolder.create(project, textLeft, textRight, innerFragmentList)
        val matchProcessor = MatchProcessor(fileInfoHolder, innerFragmentList)

        Result.success(matchProcessor.collect())
      }
      catch (exception: Exception) {
        Result.failure(exception)
      }
    }
    val lineFragmentList = createLineFragmentList(contextResult.getOrNull(), ignorePolicy, highlightPolicy, indicator)

    return if (lineFragmentList != null && lineFragmentList.isNotEmpty()) listOf(lineFragmentList) else emptyList()
  }

  private fun createLineFragmentList(context: PropertiesDiffContext?, ignorePolicy: IgnorePolicy, highlightPolicy: HighlightPolicy, indicator: ProgressIndicator): List<LineFragment>? {
    if (context == null) return null
    val rangeList = createPatchedDiffRangeList(context)
    val comparisonManager = ComparisonManagerImpl.getInstanceImpl()
    val fileInfoHolder = context.fileInfoHolder
    return rangeList.flatMap { range ->
        comparisonManager.compareLinesInner(
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

  private fun createPatchedDiffRangeList(
    propertiesDiffContext: PropertiesDiffContext,
  ): List<Range> {
    val patchedLineFragmentList = propertiesDiffContext.disabledRangeMap.flatMap { (lineFragment, unchangedRangeList) ->
      if (unchangedRangeList.isEmpty()) {
        return@flatMap listOf(
          Range(lineFragment.startLine1, lineFragment.endLine1, lineFragment.startLine2, lineFragment.endLine2)
        )
      }
      val mergedDisabledRangeMap = unchangedRangeList.groupBy { it.side }.mapValues { (_, unchangedRangeList) -> mergeDisabledRanges(unchangedRangeList) }

      val sideToRangeListMap = Side.entries.associateWith { side ->
        excludeRanges(lineFragment.toLineRange(side), mergedDisabledRangeMap[side])
      }

      val zippedRangeList = sideToRangeListMap.getOrDefault(Side.LEFT, emptyList()) zip
        sideToRangeListMap.getOrDefault(Side.RIGHT, emptyList())
      val baseFragmentList = zippedRangeList.map { (leftRange, rightRange) ->
        Range(leftRange.startLine, leftRange.endLine, rightRange.startLine, rightRange.endLine)
      }
      val suffixFragmentList = createSuffixFragments(
        sideToRangeListMap,
        lineFragment,
        zippedRangeList.size
      )
      baseFragmentList + suffixFragmentList
    }

    val modifiedLineFragmentList = propertiesDiffContext.modifiedPropertyList.map { propertyRange ->
      Range(propertyRange.left.startLine, propertyRange.left.endLine, propertyRange.right.startLine, propertyRange.right.endLine)
    }

    return (patchedLineFragmentList + modifiedLineFragmentList).filter { !it.isEmpty }
  }

  private fun createSuffixFragments(
    sideToRangeListMap: Map<Side, List<SemiOpenLineRange>>,
    lineFragment: LineFragment,
    startIndex: Int,
  ): List<Range> {
    return Side.entries.flatMap { side ->
      val changedRangeList = sideToRangeListMap[side] ?: return@flatMap emptyList()
      if (startIndex >= changedRangeList.size) return@flatMap emptyList()
      val lastLineRange = lineFragment.toLastLineRange(side)
      changedRangeList.subList(startIndex, changedRangeList.size).map { changedRange ->
        when (side) {
          Side.LEFT ->
            Range(changedRange.startLine, changedRange.endLine, lastLineRange.startLine, lastLineRange.endLine)
          Side.RIGHT ->
            Range(lastLineRange.startLine, lastLineRange.endLine, changedRange.startLine, changedRange.endLine)
        }
      }
    }
  }

  private fun excludeRanges(lineRange: SemiOpenLineRange, lineRangeList: List<SemiOpenLineRange>?): List<SemiOpenLineRange> {
    fun MutableList<SemiOpenLineRange>.tryAddHighlightingRange(lineRange: SemiOpenLineRange, startLine: Int, endLine: Int) {
      if (startLine >= endLine) return
      val newRange = SemiOpenLineRange(startLine, endLine)
      if (newRange !in lineRange) return
      this.add(newRange)
    }

    if (lineRangeList == null || lineRangeList.isEmpty()) return listOf(lineRange)
    val result = mutableListOf<SemiOpenLineRange>()
    var lastEndLine = lineRange.startLine

    for (range in lineRangeList) {
      result.tryAddHighlightingRange(lineRange, lastEndLine, range.startLine)
      lastEndLine = range.endLine
    }

    result.tryAddHighlightingRange(lineRange, lastEndLine, lineRange.endLine)

    return result
  }

  private fun mergeDisabledRanges(unchangedRangeInfoList: List<UnchangedRangeInfo>): List<SemiOpenLineRange> {
    fun List<UnchangedRangeInfo>.getRange(leftIndex: Int, rightIndex: Int): SemiOpenLineRange {
      val startLine = this[leftIndex].range.startLine
      val endLine = this[rightIndex - 1].range.endLine
      return SemiOpenLineRange(startLine, endLine)
    }

    if (unchangedRangeInfoList.isEmpty()) return emptyList()
    val result = mutableListOf<SemiOpenLineRange>()
    var leftIndex = 0
    var rightIndex = 0
    while (rightIndex < unchangedRangeInfoList.size) {
      if (rightIndex > 0 && unchangedRangeInfoList[rightIndex].range.startLine != unchangedRangeInfoList[rightIndex - 1].range.endLine) {

        result.add(unchangedRangeInfoList.getRange(leftIndex, rightIndex))
        leftIndex = rightIndex
      }
      rightIndex++
    }

    result.add(unchangedRangeInfoList.getRange(leftIndex, rightIndex))
    return result
  }
}