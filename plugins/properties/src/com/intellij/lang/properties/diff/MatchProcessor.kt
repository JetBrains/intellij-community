// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.diff

import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.tools.util.text.LineOffsets
import com.intellij.diff.util.Side
import com.intellij.lang.properties.IProperty
import com.intellij.lang.properties.diff.data.*
import com.intellij.lang.properties.diff.util.DiffTextRangeWithData
import com.intellij.lang.properties.diff.util.findAffectedChanges
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.psi.Property
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.SyntaxTraverser

internal class MatchProcessor(private val fileInfoHolder: FileInfoHolder, private val fragmentList: List<LineFragment>) {
  private val disabledRangeMap: Map<LineFragment, MutableList<UnchangedRangeInfo>> = fragmentList.associateWith { lineFragment -> mutableListOf() }
  private val modifiedPropertyList: MutableList<ModifiedPropertyRange> = mutableListOf()

  fun collect(): PropertiesDiffContext {
    collectEmptyLines(fileInfoHolder)
    collectComments(fileInfoHolder)

    val changedPropertyInfoHolder = PropertyInfoHolder.create(fileInfoHolder)
    matchProperties(changedPropertyInfoHolder)

    disabledRangeMap.values.forEach { list -> list.sortBy { it.range.startLine } }

    return PropertiesDiffContext(fileInfoHolder, disabledRangeMap, modifiedPropertyList)
  }

  private fun matchProperties(holder: PropertyInfoHolder) {
    for ((key, rightPropertyInfo) in holder[Side.RIGHT]) {
      val leftPropertyInfo = holder[Side.LEFT][key] ?: continue
      val leftProperty = leftPropertyInfo.data as? IProperty ?: continue
      val rightProperty = rightPropertyInfo.data as? IProperty ?: continue
      if (leftProperty.unescapedValue != rightProperty.unescapedValue) {
        modifiedPropertyList.add(ModifiedPropertyRange(leftPropertyInfo.range, rightPropertyInfo.range))
      }
      addDisabledRanges(leftPropertyInfo)
      addDisabledRanges(rightPropertyInfo)
    }
  }

  private fun collectEmptyLines(fileInfoHolder: FileInfoHolder) {
    Side.entries.forEach { side ->
      val fileInfo = fileInfoHolder[side]
      val document = fileInfo.file.fileDocument
      val lineOffsets = fileInfo.lineOffsets

      val diffRangeWithDataList = (0..<document.lineCount).mapNotNull { lineIndex ->
        val startOffset = lineOffsets.getLineStart(lineIndex)
        val endOffset = lineOffsets.getLineEnd(lineIndex)
        val range = TextRange(startOffset, endOffset)

        val text = document.getText(range)
        if (!text.isBlank()) return@mapNotNull null

        DiffTextRangeWithData(range)
      }

      findAndDisableLineRanges(fileInfo, lineOffsets, side, diffRangeWithDataList)
    }
  }

  private fun collectComments(fileInfoHolder: FileInfoHolder) {
    Side.entries.forEach { side ->
      val fileInfo = fileInfoHolder[side]

      val diffRangeWithDataList = SyntaxTraverser.psiTraverser(fileInfo.file).filter(PsiComment::class.java)
        .map { DiffTextRangeWithData(it.textRange) }.toList()

      val lineOffsets = fileInfo.lineOffsets
      findAndDisableLineRanges(fileInfo, lineOffsets, side, diffRangeWithDataList)
    }
  }

  private fun findAndDisableLineRanges(
    fileInfo: FileInfo,
    lineOffsets: LineOffsets,
    side: Side,
    diffRangeWithDataList: List<DiffTextRangeWithData>,
  ) {
    val lineFragmentRangeList = fileInfo.lineFragmentRangeList
    findNonSignificantChanges(lineOffsets, lineFragmentRangeList, diffRangeWithDataList, side).forEach { changeInfo -> addDisabledRanges(changeInfo) }
  }

  private fun addDisabledRanges(changeInfo: NonSignificantChangeInfo) {
    for (index in changeInfo.fragmentIndexList) {
      val lineFragment = fragmentList[index]
      disabledRangeMap[lineFragment]?.add(UnchangedRangeInfo(changeInfo.range, changeInfo.side))
    }
  }

  class PropertyInfoHolder private constructor(leftMap: Map<String, NonSignificantChangeInfo>, rightMap: Map<String, NonSignificantChangeInfo>): DiffHolderBase<Map<String, NonSignificantChangeInfo>>(leftMap, rightMap) {
    companion object {
      @JvmStatic
      internal fun create(changedFileInfoHolder: FileInfoHolder): PropertyInfoHolder {
        return PropertyInfoHolder(
          findAffectedProperties(changedFileInfoHolder, Side.LEFT),
          findAffectedProperties(changedFileInfoHolder, Side.RIGHT)
        )
      }

      private fun findAffectedProperties(holder: FileInfoHolder, side: Side): Map<String, NonSignificantChangeInfo> {
        val changedFileInfo = holder[side]
        val file = changedFileInfo.file
        if (file !is PropertiesFile) throw IllegalStateException("Not a properties file: $file")

        val propertyList = file.properties
        if (propertyList.map { it.unescapedKey }.toSet().size != propertyList.size) {
          throw IllegalStateException("Properties file contains duplicated keys: $file")
        }

        val lineFragmentRangeList = holder[side].lineFragmentRangeList

        val diffRangeWithDataList = extractDiffRangesWithData(file)

        return findNonSignificantChanges(changedFileInfo.lineOffsets, lineFragmentRangeList, diffRangeWithDataList, side).mapNotNull { changeInfo ->
          val property = changeInfo.data as? IProperty ?: return@mapNotNull null
          val key = property.unescapedKey
          if (key != null) key to changeInfo
          else null
        }.toMap()
      }

      private fun extractDiffRangesWithData(file: PropertiesFile): List<DiffTextRangeWithData> {
        return file.properties.mapNotNull { property ->
          if (property !is Property) return@mapNotNull null
          DiffTextRangeWithData(property.textRange, property)
        }
      }
    }
  }

  /**
   * Represents a unit of change of a file that does not need to be highlighted.
   *
   * @property range - changed lines of the file.
   * @property fragmentIndexList - indices of the initial [com.intellij.diff.fragments.LineFragment]s.
   * @property side - which version of the file the change belongs to (before or after).
   * @property data - element that was changed.
   */
  data class NonSignificantChangeInfo(val range: SemiOpenLineRange, val fragmentIndexList: List<Int>, val side: Side, val data: Any? = null)

  private companion object {
    fun findNonSignificantChanges(lineOffsets: LineOffsets, lineFragmentRangeList: List<TextRange>, diffRangeWithDataList: List<DiffTextRangeWithData>, side: Side): List<NonSignificantChangeInfo> {
      return findAffectedChanges(lineFragmentRangeList, diffRangeWithDataList).map {
        val startLine = lineOffsets.getLineNumber(it.range.startOffset)
        val endLine = lineOffsets.getLineNumber(it.range.endOffset)
        NonSignificantChangeInfo(SemiOpenLineRange(startLine, endLine + 1), it.fragmentIndexList, side, it.data)
      }
    }
  }
}