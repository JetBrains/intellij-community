/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.uast.java

import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.ChildRole
import org.jetbrains.uast.*
import org.jetbrains.uast.java.expressions.JavaUExpressionList
import org.jetbrains.uast.java.kinds.JavaSpecialExpressionKinds

class JavaUSwitchExpression(
  override val psi: PsiSwitchStatement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), USwitchExpression {
  override val expression by lz { JavaConverter.convertOrEmpty(psi.expression, this) }

  override val body: UExpressionList by lz {
    object : JavaUExpressionList(psi, JavaSpecialExpressionKinds.SWITCH, this) {
      override fun asRenderString() = expressions.joinToString("\n") {
        it.asRenderString().withMargin
      }
    }.apply {
      expressions = this@JavaUSwitchExpression.psi.body?.convertToSwitchEntryList(this) ?: emptyList()
    }
  }


  override val switchIdentifier: UIdentifier
    get() = UIdentifier(psi.getChildByRole(ChildRole.SWITCH_KEYWORD), this)
}

private fun PsiCodeBlock.convertToSwitchEntryList(containingElement: UExpression): List<JavaUSwitchEntry> {
  var currentLabels = listOf<PsiSwitchLabelStatement>()
  var currentBody = listOf<PsiStatement>()
  val result = mutableListOf<JavaUSwitchEntry>()
  for (statement in statements) {
    if (statement is PsiSwitchLabelStatement) {
      if (currentBody.isEmpty()) {
        currentLabels += statement
      }
      else if (currentLabels.isNotEmpty()) {
        result += JavaUSwitchEntry(currentLabels, currentBody, containingElement)
        currentLabels = listOf(statement)
        currentBody = listOf<PsiStatement>()
      }
    }
    else {
      currentBody += statement
    }
  }
  if (currentLabels.isNotEmpty()) {
    result += JavaUSwitchEntry(currentLabels, currentBody, containingElement)
  }
  return result
}

internal fun findUSwitchEntry(body: UExpressionList, el: PsiSwitchLabelStatement): JavaUSwitchEntry? =
  body.also { require(it.kind == JavaSpecialExpressionKinds.SWITCH) }
    .expressions.find { (it as? JavaUSwitchEntry)?.labels?.contains(el) ?: false } as? JavaUSwitchEntry

internal fun findUSwitchClauseBody(switch: JavaUSwitchExpression, psi: PsiElement): UExpressionList {
  val bodyExpressions = switch.body.expressions
  val uExpression = bodyExpressions.find {
    (it as JavaUSwitchEntry).body.expressions.any { it.psi == psi }
  } ?: throw IllegalStateException("${psi.javaClass} not found in ${bodyExpressions.map { it.asLogString() }}")
  return (uExpression as JavaUSwitchEntry).body
}


class JavaUSwitchEntry(
  val labels: List<PsiSwitchLabelStatement>,
  val statements: List<PsiStatement>,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), USwitchClauseExpressionWithBody {
  override val psi: PsiSwitchLabelStatement = labels.first()

  override val caseValues by lz {
    labels.mapNotNull {
      if (it.isDefaultCase) {
        JavaUDefaultCaseExpression(it, this)
      }
      else {
        val value = it.caseValue
        value?.let { JavaConverter.convertExpression(it, this) }
      }
    }
  }

  override val body: UExpressionList by lz {
    object : JavaUExpressionList(psi, JavaSpecialExpressionKinds.SWITCH_ENTRY, this) {
      override fun asRenderString() = buildString {
        appendln("{")
        expressions.forEach { appendln(it.asRenderString().withMargin) }
        appendln("}")
      }
    }.apply {
      val statements = this@JavaUSwitchEntry.statements
      expressions = statements.map { JavaConverter.convertOrEmpty(it, this) }
    }
  }
}

class JavaUDefaultCaseExpression(override val psi: PsiElement?, givenParent: UElement?)
  : JavaAbstractUExpression(givenParent), JvmDeclarationUElement {

  override val annotations: List<UAnnotation>
    get() = emptyList()

  override fun asLogString() = "UDefaultCaseExpression"

  override fun asRenderString() = "else"
}