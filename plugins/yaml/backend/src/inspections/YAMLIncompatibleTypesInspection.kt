// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.parentOfType
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

    val problem = holder.problem(scalar,
                                 YAMLBundle.message("inspections.incompatible.types.message", estimatedType.displayName, mostPopularType.displayName))
    if (mostPopularType == YamlStringType.getInstance()) {
      val siblings = findStructuralSiblings(scalar)
      val singleQuote = siblings.filterIsInstance<YAMLQuotedText>().firstOrNull()?.isSingleQuote ?: false

      problem.fix(
        YAMLAddQuoteQuickFix(scalar, YAMLBundle.message("inspections.incompatible.types.quickfix.wrap.quotes.message"), singleQuote))
      if (siblings.any { it != scalar && it !is YAMLQuotedText }) {
        problem.fix(YAMLAddQuotesToSiblingsQuickFix(scalar, singleQuote))
      }
    }

    problem.register()
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
  PsiUpdateModCommandAction<YAMLValue>(baseElement) {
  override fun getFamilyName(): @Nls String = YAMLBundle.message("inspections.incompatible.types.quickfix.wrap.all.quotes.message")

  override fun invoke(context: ActionContext, baseElement: YAMLValue, updater: ModPsiUpdater) {
    for (yamlValue in findStructuralSiblings(baseElement).filterNot { it is YAMLQuotedText }) {
      wrapWithQuotes(yamlValue, singleQuote)
    }
  }
}