package com.intellij.lang.properties.merge

import com.intellij.diff.merge.LangSpecificMergeConflictResolver
import com.intellij.diff.merge.LangSpecificMergeContext
import com.intellij.diff.util.ThreeSide
import com.intellij.lang.Language
import com.intellij.lang.properties.PropertiesLanguage
import com.intellij.lang.properties.merge.data.PropertyInfo
import com.intellij.lang.properties.psi.PropertiesElementFactory
import com.intellij.lang.properties.psi.Property
import com.intellij.lang.properties.psi.PropertyKeyValueFormat
import com.intellij.lang.properties.psi.impl.PropertyImpl.unescape
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.IncorrectOperationException
import java.util.*

private val LOG = logger<PropertiesMergeConflictResolver>()

internal typealias ConflictToPropertiesMap = Map<Int, Map<String, PropertyInfo>>

class PropertiesMergeConflictResolver : LangSpecificMergeConflictResolver {
  private val FILTERS = listOf(OUTSIDE_INTERSECTIONS, INNER_INCONSISTENCY, COMMENT_INTERSECTIONS, COMMENT_INCONSISTENCY)

  override fun isApplicable(language: Language): Boolean = language == PropertiesLanguage.INSTANCE

  override suspend fun tryResolveMergeConflicts(context: LangSpecificMergeContext): List<CharSequence?>? {
    val project = context.project
    if (project == null) return null

    try {
      val propertyList = readAction {
        val holder = ThreeSideConflictInfoHolder.create(context) ?: return@readAction null

        val propertyMergeContextList = prepareForMerge(holder, context.lineFragmentList.size)
        tryMergeProperties(propertyMergeContextList)
      }
      if (propertyList == null) return null

      return convertPropertiesToString(project, propertyList)
    }
    catch (e: IncorrectOperationException) {
      LOG.error(e)
    }
    return null
  }

  /**
   * Extracts all necessary information required for merging properties and performs additional checks on whether it is possible to resolve
   * properties with having information about all [ThreeSide]s.
   *
   * @see FILTERS
   */
  private fun prepareForMerge(holder: ThreeSideConflictInfoHolder, numberOfConflicts: Int): List<PropertyMergeContext?> {
    return (0 until numberOfConflicts).map { index ->
      val leftMap = holder.conflictToPropertiesMap(ThreeSide.LEFT).getOrDefault(index, emptyMap())
      val rightMap = holder.conflictToPropertiesMap(ThreeSide.RIGHT).getOrDefault(index, emptyMap())
      if (FILTERS.any {it.test(holder, index)})  null else PropertyMergeContext(leftMap, rightMap)
    }
  }

  /**
   * Attempts to merge properties from two sides. A property can be merged successfully if the following conditions are satisfied:
   * * The property keys are equal
   * * The property values are equal
   * * The property comments are equal, or one side doesn't have a comment
   */
  private fun tryMergeProperties(propertyMergeContextList: List<PropertyMergeContext?>): List<List<PropertyInfo>?> {
    return propertyMergeContextList.map { mergeContext ->
      if (mergeContext == null) return@map null

      val leftMap = mergeContext.leftMap
      val rightMap = mergeContext.rightMap

      val keyToPropertyMap: MutableMap<String, PropertyInfo> = TreeMap(leftMap)
      rightMap.forEach { (key, property) ->
        if (keyToPropertyMap.contains(key)) {
          val newValue = unescape(property.value)
          val existingProperty = keyToPropertyMap.getValue(key)
          val existingValue = unescape(existingProperty.value)
          if (newValue != existingValue) return@map null

          val newComment = property.comment
          val existingComment = existingProperty.comment
          if (newComment != null && existingComment != null && newComment != existingComment) return@map null
          if (newComment != null && existingComment == null) {
            keyToPropertyMap[key] = existingProperty.copy(comment = newComment)
          }
        }
        else {
          keyToPropertyMap[key] = property
        }
      }
      keyToPropertyMap.values.toList()
    }
  }

  private suspend fun convertPropertiesToString(project: Project, contextList: List<List<PropertyInfo>?>): List<CharSequence?> {
    return contextList.map { propertyList ->
      writeAction {
        propertyList?.map { info ->
          val key = info.key
          val value = info.value
          val comment = info.comment ?: ""
          // If a user explicitly wanted to have key without delimiter let's not format it.
          if (value.isEmpty()) return@map comment + key

          val formattedProperty = PropertiesElementFactory.createProperty(project, key, value, null, PropertyKeyValueFormat.FILE) as? Property
                                  ?: return@writeAction null

          val propertyText = formattedProperty.text
          comment + propertyText
        }?.joinToString("\n")
      }
    }
  }

  private data class PropertyMergeContext(val leftMap: Map<String, PropertyInfo>, val rightMap: Map<String, PropertyInfo>)
}