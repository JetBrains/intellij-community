// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.htmlInspections

import com.intellij.codeInsight.daemon.impl.analysis.XmlChangeAttributeValueIntentionFix
import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.Pair
import com.intellij.psi.impl.source.html.HtmlEnumeratedValueReference
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.util.text.EditDistance

class HtmlWrongAttributeValueInspection : HtmlLocalInspectionTool() {

  override fun checkAttributeValue(attributeValue: XmlAttributeValue,
                                   holder: ProblemsHolder,
                                   isOnTheFly: Boolean) {
    for (ref in attributeValue.references) {
      if (ref is HtmlEnumeratedValueReference && XmlHighlightVisitor.hasBadResolve(ref, true)) {
        val fixes = getQuickFixes(ref)
        holder.registerProblemForReference(
          ref, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
          ref.unresolvedMessagePattern, *fixes)
      }
    }
  }

  private fun getQuickFixes(ref: HtmlEnumeratedValueReference): Array<LocalQuickFix> {
    val refValue = ref.value
    return ref.variants.asSequence()
      .mapNotNull {
        if (it is String)
          Pair(EditDistance.levenshtein(it, refValue, false), it)
        else null
      }
      // Choose 3 values with the closest match to current value
      .sortedWith(Pair.comparingByFirst())
      .take(3)
      .mapIndexed { index, v ->
        XmlChangeAttributeValueIntentionFix(v.second)
          .also {
            if (index == 0)
              it.priority = PriorityAction.Priority.HIGH
          }
      }
      .toList()
      .toTypedArray()
  }
}
