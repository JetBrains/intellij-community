// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import com.intellij.util.SmartList
import com.intellij.util.asSafely
import com.intellij.util.containers.FactoryMap
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import org.jetbrains.annotations.Nls
import org.jetbrains.yaml.YAMLBundle
import org.jetbrains.yaml.meta.model.YamlMetaType
import org.jetbrains.yaml.meta.model.YamlStringType
import org.jetbrains.yaml.psi.*

internal class YAMLIncompatibleTypesInspection : LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    val schemaObject = JsonSchemaService.Impl.get(holder.project).getSchemaObject(holder.file)
    if (schemaObject != null) return PsiElementVisitor.EMPTY_VISITOR
    return YamlIncompatibleTypesVisitor(holder, session)
  }
}

private class YamlIncompatibleTypesVisitor(private val holder: ProblemsHolder,
                                           private val session: LocalInspectionToolSession) : YamlPsiElementVisitor() {

  override fun visitScalar(scalar: YAMLScalar) {
    val estimatedType = estimatedType(scalar) ?: return
    val mostPopularType = getMostPopularTypeForSiblings(scalar)
    if (mostPopularType == null || mostPopularType == estimatedType) return

    val quickFixes = SmartList<LocalQuickFix>()
    if (mostPopularType == YamlStringType.getInstance()) {
      val siblings = findStructuralSiblings(scalar)
      val singleQuote = siblings.filterIsInstance<YAMLQuotedText>().firstOrNull()?.isSingleQuote ?: false

      quickFixes.add(
        YAMLAddQuoteQuickFix(scalar, YAMLBundle.message("inspections.incompatible.types.quickfix.wrap.quotes.message"), singleQuote))
      if (siblings.any { it != scalar && it !is YAMLQuotedText }) {
        quickFixes.add(YAMLAddQuotesToSiblingsQuickFix(scalar, singleQuote))
      }
    }

    holder.registerProblem(
      scalar,
      YAMLBundle.message("inspections.incompatible.types.message", estimatedType.displayName, mostPopularType.displayName),
      ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
      *quickFixes.toTypedArray()
    )
    super.visitScalar(scalar)
  }

  private fun getMostPopularTypeForSiblings(value: YAMLValue): YamlMetaType? {
    val topSeq = value.parentOfType<YAMLSequence>() ?: return null
    return session.memoizeWith2Keys(STRUCTURAL_SIBLINGS_TYPE_KEY, topSeq, getKeysInBetween(value, topSeq)) { seq, ks ->
      findStructuralSiblings(seq, ks).filterIsInstance<YAMLScalar>()
        .mapNotNull { estimatedType(it) }.groupingBy { it }
        .eachCount().maxByOrNull { it.value }?.key
    }
  }

}

private val STRUCTURAL_SIBLINGS_TYPE_KEY = Key.create<Map<YAMLSequence, Map<List<String>, YamlMetaType?>>>("STRUCTURAL_SIBLINGS")

private fun <T1, T2, R> UserDataHolder.memoizeWith2Keys(storageKey: Key<Map<T1, Map<T2, R>>>, val1: T1, val2: T2, eval: (T1, T2) -> R): R? {
  val cache = getUserData(storageKey)
              ?: FactoryMap.create<T1, Map<T2, R>> { seq ->
                FactoryMap.create { keys -> eval(seq, keys) }
              }.also { putUserData(storageKey, it) }
  return cache[val1]?.get(val2)
}


private class YAMLAddQuotesToSiblingsQuickFix(baseElement: YAMLValue, private val singleQuote: Boolean = false) :
  LocalQuickFixAndIntentionActionOnPsiElement(baseElement) {
  override fun getText(): @Nls String = YAMLBundle.message("inspections.incompatible.types.quickfix.wrap.all.quotes.message")

  override fun getFamilyName(): @Nls String = text

  override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
    val baseElement = startElement.asSafely<YAMLValue>() ?: return
    for (yamlValue in findStructuralSiblings(baseElement).filterNot { it is YAMLQuotedText }) {
      wrapWithQuotes(yamlValue, singleQuote)
    }
  }
}