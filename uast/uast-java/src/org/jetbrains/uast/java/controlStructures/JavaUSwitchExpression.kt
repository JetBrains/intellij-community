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
  override val psi: PsiSwitchBlock,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), USwitchExpression {
  override val expression: UExpression by lz { JavaConverter.convertOrEmpty(psi.expression, this) }

  override val body: JavaUSwitchEntryList by lz { JavaUSwitchEntryList(psi, this) }

  override val switchIdentifier: UIdentifier
    get() = UIdentifier(psi.getChildByRole(ChildRole.SWITCH_KEYWORD), this)

}

class JavaUSwitchEntryList(override val psi: PsiSwitchBlock, override val uastParent: JavaUSwitchExpression) :
  JavaAbstractUExpression(uastParent), UExpressionList {

  override val kind: UastSpecialExpressionKind
    get() = JavaSpecialExpressionKinds.SWITCH

  override fun asRenderString() = expressions.joinToString("\n") {
    it.asRenderString().withMargin
  }

  private val switchEntries: Lazy<List<JavaUSwitchEntry>> = lazy {
    val statements = psi.body?.statements ?: return@lazy emptyList<JavaUSwitchEntry>()
    var currentLabels = listOf<PsiSwitchLabelStatementBase>()
    var currentBody = listOf<PsiStatement>()
    val result = mutableListOf<JavaUSwitchEntry>()
    for (statement in statements) {
      if (statement is PsiSwitchLabeledRuleStatement) {
        result += JavaUSwitchEntry(listOf(statement), listOfNotNull(statement.body), this)
      }
      if (statement is PsiSwitchLabelStatement) {
        if (currentBody.isEmpty()) {
          currentLabels += statement
        }
        else if (currentLabels.isNotEmpty()) {
          result += JavaUSwitchEntry(currentLabels, currentBody, this)
          currentLabels = listOf(statement)
          currentBody = listOf<PsiStatement>()
        }
      }
      else {
        currentBody += statement
      }
    }
    if (currentLabels.isNotEmpty()) {
      result += JavaUSwitchEntry(currentLabels, currentBody, this)
    }
    result

  }

  override val expressions: List<JavaUSwitchEntry> get() = switchEntries.value

  fun findUSwitchEntryForLabel(switchLabelStatement: PsiSwitchLabelStatementBase): JavaUSwitchEntry? {
    if (switchEntries.isInitialized()) return switchEntries.value.find { it.labels.contains(switchLabelStatement) }

    if (switchLabelStatement is PsiSwitchLabeledRuleStatement) {
      return JavaUSwitchEntry(listOf(switchLabelStatement), listOfNotNull(switchLabelStatement.body), this)
    }

    val bodyStart = switchLabelStatement.nextSiblings.find { it !is PsiSwitchLabelStatement } ?: return null
    val body = bodyStart.nextSiblings.takeWhile { it !is PsiSwitchLabelStatement }.filterIsInstance<PsiStatement>().toList()
    val labels = switchLabelStatement.prevSiblings.takeWhile { it is PsiSwitchLabelStatement }.filterIsInstance<PsiSwitchLabelStatement>().toList()

    return JavaUSwitchEntry(labels, body, this)
  }

  fun findUSwitchEntryForBodyStatementMember(psi: PsiElement): JavaUSwitchEntry? {
    if (switchEntries.isInitialized()) return switchEntries.value.find { it.body.expressions.any { it.psi == psi } }

    val statement = psi as? PsiStatement ?: // PsiBreakStatement for instance
                    psi.parent as? PsiStatement ?: // expressions inside case body
                    return null
    val psiSwitchLabelStatement = statement.prevSiblings.filterIsInstance<PsiSwitchLabelStatement>().firstOrNull() ?: return null
    return findUSwitchEntryForLabel(psiSwitchLabelStatement)
  }

}

private val PsiElement.nextSiblings: Sequence<PsiElement> get() = generateSequence(this) { it.nextSibling }
private val PsiElement.prevSiblings: Sequence<PsiElement> get() = generateSequence(this) { it.prevSibling }


class JavaUSwitchEntry(
  val labels: List<PsiSwitchLabelStatementBase>,
  val statements: List<PsiStatement>,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), USwitchClauseExpressionWithBody {
  override val psi: PsiSwitchLabelStatementBase = labels.first()

  override val caseValues: List<UExpression> by lz {
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

      override val expressions: List<UExpression> =
        this@JavaUSwitchEntry.statements.map { JavaConverter.convertOrEmpty(it, this) }

      override fun asRenderString() = buildString {
        appendln("{")
        expressions.forEach { appendln(it.asRenderString().withMargin) }
        appendln("}")
      }
    }
  }
}

class JavaUDefaultCaseExpression(override val psi: PsiElement?, givenParent: UElement?)
  : JavaAbstractUExpression(givenParent), JvmDeclarationUElement {

  override val annotations: List<UAnnotation>
    get() = emptyList()

  override fun asLogString(): String = "UDefaultCaseExpression"

  override fun asRenderString(): String = "else"
}