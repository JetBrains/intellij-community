package com.intellij.lang.properties.diff.util

import com.intellij.openapi.util.TextRange

/**
 * Represents a change that belongs to multiple line fragments.
 *
 * @property range - range of the element in the file.
 * @property fragmentIndexList - indexes of fragments in which the change is located.
 * @property data - element, on which the range was calculated.
 */
internal data class AffectedChangeInfo(val range: TextRange, val fragmentIndexList: List<Int>, val data: Any? = null)

/**
 * Represents the range of some element in the file that could be a part of some change.
 * It is not necessary that element inheritor of [com.intellij.psi.PsiElement].
 *
 * @property range - range of the element in the file.
 * @property data - element, on which the range was calculated.
 */
internal data class DiffTextRangeWithData(val range: TextRange, val data: Any? = null)

internal fun findAffectedChanges(lineFragmentRangeList: List<TextRange>, diffRangeWithDataList: List<DiffTextRangeWithData>): List<AffectedChangeInfo> {
  var diffRangeIndex = 0
  return diffRangeWithDataList.mapNotNull { diffTextRangeWithData ->
    if (diffRangeIndex == lineFragmentRangeList.size) return@mapNotNull null

    val range = diffTextRangeWithData.range
    val fragmentIndexList = mutableListOf<Int>()

    while (diffRangeIndex < lineFragmentRangeList.size) {
      val currentLineFragmentMetaInfo = lineFragmentRangeList[diffRangeIndex]
      if (range.intersects(currentLineFragmentMetaInfo)) {
        fragmentIndexList.add(diffRangeIndex)
      }
      val nextDiffRangeIndex = diffRangeIndex.inc()

      if (nextDiffRangeIndex < lineFragmentRangeList.size && lineFragmentRangeList[nextDiffRangeIndex].startOffset <= range.endOffset) {
        diffRangeIndex = nextDiffRangeIndex
      }
      else if (nextDiffRangeIndex == lineFragmentRangeList.size && currentLineFragmentMetaInfo.endOffset < range.startOffset) {
        diffRangeIndex = nextDiffRangeIndex
        break
      }
      else {
        break
      }
    }

    if (fragmentIndexList.isEmpty()) return@mapNotNull null

    AffectedChangeInfo(range, fragmentIndexList, diffTextRangeWithData.data)
  }
}