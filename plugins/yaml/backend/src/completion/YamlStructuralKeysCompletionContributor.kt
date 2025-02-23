// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentsOfType
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.util.asSafely
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.JsonSchemaType
import org.jetbrains.yaml.meta.impl.YamlKeyInsertHandler
import org.jetbrains.yaml.meta.model.*
import org.jetbrains.yaml.psi.*

class YamlStructuralKeysCompletionContributor : CompletionContributor() {

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val position = parameters.position
    val value = position.parentOfType<YAMLValue>(true) ?: return
    if (value.references.isNotEmpty()) return
    val jsonSchemaService = JsonSchemaService.Impl.get(position.getProject());
    val jsonObject = jsonSchemaService.getSchemaObject(parameters.originalFile);
    if (jsonObject != null) return //We have schema for autocompletion

    val alreadyDeclared = value.parent.asSafely<YAMLMapping>()?.keyValues?.mapNotNullTo(mutableSetOf()) { it.keyText }.orEmpty()

    val keysToSuggest = value.parentsOfType<YAMLSequence>().flatMap { seq ->
      ProgressManager.checkCanceled()
      findStructuralSiblings(seq, getKeysInBetween(value, seq))
        .flatMap { structuralSibling ->
          when (structuralSibling) {
            is YAMLKeyValue ->
              sequenceOf(structuralSibling)
            is YAMLMapping ->
              structuralSibling.keyValues.asSequence()
            else -> emptySequence()
          }
        }
    }.filter { !alreadyDeclared.contains(it.keyText) && position != it.key }

    result.addAllElements(
      keysToSuggest
        .mapNotNull { kv -> createLookupElementForKey(kv, parameters.originalFile) }
        .asIterable()
    )

  }

  private fun createLookupElementForKey(structuralSibling: YAMLKeyValue, physicalFile: PsiFile): LookupElementBuilder {
    val typePresentation = when (val v = structuralSibling.value) {
      is YAMLMapping -> AllIcons.Json.Object to JsonSchemaType._object
      is YAMLSequence -> AllIcons.Json.Array to JsonSchemaType._array
      is YAMLScalar -> {
        val elementForTypeEstimation = if (v.isPhysical) {
          v
        }
        else {
          // dance with a tambourine because estimatedType does not accept non-physical elements
          physicalFile.findElementAt(v.textRange.startOffset)?.parentOfType<YAMLScalar>(true)
        }

        IconManager.getInstance().getPlatformIcon(PlatformIcons.Property) to
          elementForTypeEstimation?.let { estimatedType(it)?.asJsonSchemaType() }
      }
      else -> null
    }
    return LookupElementBuilder
      .create(structuralSibling.keyText)
      .withPsiElement(structuralSibling.key)
      .withIcon(typePresentation?.first)
      .withInsertHandler(MetaModelBasedInsertHandler(structuralSibling.keyText, typePresentation?.second))
      .withTypeText(typePresentation?.second?.description)
  }

  inner class MetaModelBasedInsertHandler(
    private val keyText: String,
    private val jsonSchemaType: JsonSchemaType?
  ) : YamlKeyInsertHandler(jsonSchemaType == JsonSchemaType._array) {
    override fun computeInsertionMarkup(context: InsertionContext, forcedCompletionPath: YamlMetaType.ForcedCompletionPath): YamlMetaType.YamlInsertionMarkup {
      return YamlMetaType.YamlInsertionMarkup(context).apply {

        when (jsonSchemaType) {
          JsonSchemaType._object ->
            YamlUnstructuredClass.getInstance().buildInsertionSuffixMarkup(
              this,
              Field.Relation.OBJECT_CONTENTS,
              forcedCompletionPath.start()
            )

          JsonSchemaType._array ->
            YamlUnstructuredClass.getInstance().buildInsertionSuffixMarkup(
              this,
              Field.Relation.SEQUENCE_ITEM,
              forcedCompletionPath.start()
            )
          else ->
            YamlAnything.getInstance().buildInsertionSuffixMarkup(
              this,
              Field.Relation.SCALAR_VALUE,
              forcedCompletionPath.start()
            )
        }
      }
    }

    override fun getReplacement(): String = keyText

  }

  private fun YamlMetaType.asJsonSchemaType(): JsonSchemaType? {
    return when (this) {
      is YamlBooleanType -> JsonSchemaType._boolean
      is YamlNumberType -> JsonSchemaType._number
      is YamlStringType -> JsonSchemaType._string
      is YamlAnything -> JsonSchemaType._any
      else -> null
    }
  }
}