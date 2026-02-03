package com.intellij.lang.properties.merge

import com.intellij.diff.util.ThreeSide
import com.intellij.lang.properties.merge.data.PropertyInfo
import com.intellij.lang.properties.parsing.PropertiesElementTypes
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil

/**
 * Tests whether conflict in properties files can be resolved using all [ThreeSide]s.
 */
internal fun interface Filter {
  /**
   * @param index position of the conflict among all conflicts in the file.
   *
   * @see PropertiesMergeConflictResolver.prepareForMerge
   */
  fun test(holder: ThreeSideConflictInfoHolder, index: Int): Boolean
}

/**
 *
 * Left part:
 * ```properties
 * left.key = left value
 * ```
 * Right part:
 * ```properties
 * right.key = right value
 * ```
 * All right file:
 * ```properties
 * left.key = left value
 * right.key = right value
 * ```
 */
internal val OUTSIDE_INTERSECTIONS = Filter { holder, index ->
  val mapList = getConflictPropertyMaps(holder, index)
  ThreeSide.entries.any { side ->
    if (ThreeSide.BASE == side) return@any false
    val oppositeSide = side.opposite()
    hasOutsideIntersection(
      side.selectNotNull(mapList),
      mapList[1],
      oppositeSide.selectNotNull(mapList),
      holder,
      oppositeSide
    )
  }
}

/**
 * Left part:
 * ```properties
 * second.key = second value
 * ```
 * Base part:
 * ```properties
 * first.key = first value
 * second.key = second value
 * ```
 * Right part:
 * ```properties
 * first.key = new first value
 * second.key = second value
 * ```
 */
internal val INNER_INCONSISTENCY = Filter { holder, index ->
  val (leftMap, baseMap, rightMap) = getConflictPropertyMaps(holder, index)
  baseMap.any { (key, _) -> key in leftMap && key !in rightMap || key !in leftMap && key in rightMap }
}

/**
 * Left part:
 * ```properties
 * # comment above
 *
 * # some comment
 * left.key = left value
 * ```
 * Right part
 * ```properties
 * right.key = right value
 * ```
 * All left file:
 * ```properties
 * above.left.key = above left value
 * # comment above
 *
 * # some comment
 * left.key = left value
 * ```
 */
internal val COMMENT_INTERSECTIONS = Filter { holder, index ->
  ThreeSide.entries.any {
    if (ThreeSide.BASE == it) return@any false
    val rangeList = holder.rangeList(it)
    val conflictToPropertiesMap = holder.conflictToPropertiesMap(it).getOrDefault(index, emptyMap())
    hasCommentIntersections(rangeList[index], conflictToPropertiesMap)
  }
}

/**
 * Left part:
 * ```properties
 * # some updated comment
 * base.key = base value
 * ```
 * Base part:
 * ```properties
 * # some comment
 * base.key = base value
 * ```
 * Right part:
 * ```properties
 * base.key = base value
 * ```
 */
internal val COMMENT_INCONSISTENCY = Filter { holder, index ->
  val mapList = getConflictPropertyMaps(holder, index)
  ThreeSide.entries.any { side ->
    if (ThreeSide.BASE == side) return@any false
    val oppositeSide = side.opposite()
    hasCommentInconsistency(side.selectNotNull(mapList), mapList[1], oppositeSide.selectNotNull(mapList))
  }
}

private fun hasCommentInconsistency(currentMap: Map<String, PropertyInfo>, baseMap: Map<String, PropertyInfo>, oppositeMap: Map<String, PropertyInfo>) : Boolean {
  return currentMap.any { (key, currentInfo) ->
    val baseInfo = baseMap[key]
    val oppositeInfo = oppositeMap[key]
    if (baseInfo == null || oppositeInfo == null) return false

    val currentComment = currentInfo.comment
    val baseComment = baseInfo.comment
    val oppositeComment = oppositeInfo.comment
    baseComment != null && (currentComment == null && oppositeComment != null || currentComment != null && oppositeComment == null)
  }
}

private fun hasCommentIntersections(conflictRange: TextRange, map : Map<*, PropertyInfo>): Boolean {
  return map.values.any { info ->
    if (info.borderElement is PsiComment && info.borderElement.textRange !in conflictRange) {
      return@any true
    }

    var hasCommentOutsideOfRange = false
    PsiTreeUtil.findSiblingBackward(info.borderElement, PropertiesElementTypes.PROPERTY_TYPE,  true) { element ->
      if (!hasCommentOutsideOfRange && element !is PsiWhiteSpace && element.textRange.intersects(conflictRange)) {
        hasCommentOutsideOfRange = true
      }
    }
    hasCommentOutsideOfRange
  }
}

private fun getConflictPropertyMaps(
  holder: ThreeSideConflictInfoHolder,
  index: Int,
): List<Map<String, PropertyInfo>> {
  val leftMap = holder.conflictToPropertiesMap(ThreeSide.LEFT).getOrDefault(index, emptyMap())
  val baseMap = holder.conflictToPropertiesMap(ThreeSide.BASE).getOrDefault(index, emptyMap())
  val rightMap = holder.conflictToPropertiesMap(ThreeSide.RIGHT).getOrDefault(index, emptyMap())
  return listOf(leftMap, baseMap, rightMap)
}

private fun ThreeSide.opposite(): ThreeSide = when (this) {
  ThreeSide.LEFT -> ThreeSide.RIGHT
  ThreeSide.BASE -> ThreeSide.BASE
  ThreeSide.RIGHT -> ThreeSide.LEFT
}

private fun hasOutsideIntersection(currentMap : Map<String, *>, baseMap : Map<String, *>, oppositeMap : Map<String, *>, holder: ThreeSideConflictInfoHolder, oppositeSide: ThreeSide) : Boolean {
  return currentMap.any { (key, _) ->
    key !in oppositeMap && key in holder.keySet(oppositeSide) ||
    key !in baseMap && key in holder.keySet(ThreeSide.BASE)
  }
}