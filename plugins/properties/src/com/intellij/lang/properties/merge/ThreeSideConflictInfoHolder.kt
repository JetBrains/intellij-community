package com.intellij.lang.properties.merge

import com.intellij.diff.merge.LangSpecificMergeContext
import com.intellij.diff.util.ThreeSide
import com.intellij.lang.properties.IProperty
import com.intellij.lang.properties.diff.util.DiffTextRangeWithData
import com.intellij.lang.properties.diff.util.findAffectedChanges
import com.intellij.lang.properties.merge.data.PropertyInfo
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.psi.Property
import com.intellij.lang.properties.psi.impl.PropertyImpl
import com.intellij.lang.properties.psi.impl.PropertyImpl.unescape
import com.intellij.openapi.util.TextRange

/**
 * Stores information about all conflicts for certain [ThreeSide].
 */
internal class ThreeSideConflictInfoHolder(private val sideContentList : List<ThreeSideConflictInfo>) {
  fun conflictToPropertiesMap(side: ThreeSide): ConflictToPropertiesMap = sideContentList[side.index].conflictToPropertiesMap

  fun rangeList(side: ThreeSide): List<TextRange> = sideContentList[side.index].rangeList

  fun keySet(side: ThreeSide): Set<String> = sideContentList[side.index].keySet

  companion object {
    fun create(context: LangSpecificMergeContext): ThreeSideConflictInfoHolder? {
      return ThreeSideConflictInfoHolder(ThreeSide.entries.map { side ->
        val file = context.file(side)
        val rangeList = context.lineRanges(side)

        if (file !is PropertiesFile) return null
        val propertyList = file.properties

        val propertyMap = findModifiedProperties(propertyList, rangeList) ?: return null

        val propertyKeySet = findPropertyKeys(propertyList) ?: return null

        ThreeSideConflictInfo(rangeList, propertyKeySet, propertyMap)
      })
    }

    private fun findPropertyKeys(propertyList : List<IProperty>): Set<String>? {
      val keyList = propertyList.map { property ->
        if (property !is Property) return null
        val key = property.unescapedKey ?: return null
        key
      }

      val keySet = keyList.toSet()

      if (keySet.size != keyList.size) return null

      return keySet
    }

    private fun findModifiedProperties(propertyList : List<IProperty>, rangeList: List<TextRange>): ConflictToPropertiesMap? {
      val propertyRangeList = propertyList.map { property ->
        if (property !is Property) return null
        DiffTextRangeWithData(property.textRange, property)
      }

      return findAffectedChanges(rangeList, propertyRangeList)
        .flatMap { changeInfo ->
          changeInfo.fragmentIndexList.map { index ->
            val property = changeInfo.data
            if (property !is Property) return null
            val key = property.key ?: return null
            val value = property.value ?: return null
            val comment = property.docCommentText
            val borderElement = PropertyImpl.getEdgeOfProperty(property)
            index to PropertyInfo(borderElement, key, value, comment)
          }
        }.groupBy { it.first }.mapValues { (_, list) ->
          list.associate { (_, info) ->
            unescape(info.key) to info
          }
        }
    }
  }

  /**
   * @param rangeList list of conflicting ranges.
   * @param keySet set of all keys in the file.
   * @param conflictToPropertiesMap map of index of conflicting chunk to all properties that are intersected with this chunk.
   */
  data class ThreeSideConflictInfo(val rangeList: List<TextRange>, val keySet: Set<String>, val conflictToPropertiesMap: ConflictToPropertiesMap)
}