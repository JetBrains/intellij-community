// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.java

import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.ChildRole
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*
import org.jetbrains.uast.java.expressions.JavaUExpressionList
import org.jetbrains.uast.java.kinds.JavaSpecialExpressionKinds

@ApiStatus.Internal
class JavaUSwitchExpression(
  override val sourcePsi: PsiSwitchBlock,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), USwitchExpression {
  override val expression: UExpression by lz { JavaConverter.convertOrEmpty(sourcePsi.expression, this) }

  override val body: JavaUSwitchEntryList by lz { JavaUSwitchEntryList(sourcePsi, this) }

  override val switchIdentifier: UIdentifier
    get() = UIdentifier(sourcePsi.getChildByRole(ChildRole.SWITCH_KEYWORD), this)

}

@ApiStatus.Internal
class JavaUSwitchEntryList(
  override val sourcePsi: PsiSwitchBlock,
  override val uastParent: JavaUSwitchExpression
) : JavaAbstractUExpression(uastParent), UExpressionList {

  override val kind: UastSpecialExpressionKind
    get() = JavaSpecialExpressionKinds.SWITCH

  override fun asRenderString() = expressions.joinToString("\n") {
    it.asRenderString().withMargin
  }

  private val switchEntries: Lazy<List<JavaUSwitchEntry>> = lz {
    val statements = sourcePsi.body?.statements ?: return@lz emptyList<JavaUSwitchEntry>()
    var currentLabels = listOf<PsiSwitchLabelStatementBase>()
    var currentBody = listOf<PsiStatement>()
    val result = mutableListOf<JavaUSwitchEntry>()
    for (statement in statements) {
      if (statement is PsiSwitchLabeledRuleStatement) {
        val body = statement.body
        result += when (body) {
          is PsiBlockStatement ->
            JavaUSwitchEntry(listOf(statement), body.codeBlock.statements.toList(), this, false)
          else ->
            JavaUSwitchEntry(listOf(statement), listOfNotNull(body), this, true)
        }

      }
      if (statement is PsiSwitchLabelStatement) {
        if (currentBody.isEmpty()) {
          currentLabels += statement
        }
        else if (currentLabels.isNotEmpty()) {
          result += JavaUSwitchEntry(currentLabels, currentBody, this)
          currentLabels = listOf(statement)
          currentBody = listOf()
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

  override val expressions: List<UExpression>
      get() = switchEntries.value

  internal fun findUSwitchEntryForLabel(switchLabelStatement: PsiSwitchLabelStatementBase): JavaUSwitchEntry? {
    if (switchEntries.isInitialized()) return switchEntries.value.find { it.labels.contains(switchLabelStatement) }

    if (switchLabelStatement is PsiSwitchLabeledRuleStatement) {
      return JavaUSwitchEntry(listOf(switchLabelStatement), listOfNotNull(switchLabelStatement.body), this, true)
    }

    val bodyStart = switchLabelStatement.nextSiblings.find { it !is PsiSwitchLabelStatement } ?: return null
    val body = bodyStart.nextSiblings.takeWhile { it !is PsiSwitchLabelStatement }.filterIsInstance<PsiStatement>().toList()
    val labels = switchLabelStatement.prevSiblings.takeWhile { it is PsiSwitchLabelStatement }.filterIsInstance<PsiSwitchLabelStatement>().toList()

    return JavaUSwitchEntry(labels, body, this)
  }

  internal fun findUSwitchEntryForBodyStatementMember(psi: PsiElement): JavaUSwitchEntry? {
    if (switchEntries.isInitialized()) return switchEntries.value.find { it.body.expressions.any { it.sourcePsi == psi } }

    val statement = psi as? PsiStatement ?: // PsiBreakStatement for instance
                    psi.parent as? PsiStatement ?: // expressions inside case body
                    return null
    val psiSwitchLabelStatement = statement.prevSiblings.filterIsInstance<PsiSwitchLabelStatement>().firstOrNull() ?: return null
    return findUSwitchEntryForLabel(psiSwitchLabelStatement)
  }

}

private val PsiElement.nextSiblings: Sequence<PsiElement> get() = generateSequence(this) { it.nextSibling }
private val PsiElement.prevSiblings: Sequence<PsiElement> get() = generateSequence(this) { it.prevSibling }

@ApiStatus.Internal
class JavaUSwitchEntry(
  val labels: List<PsiSwitchLabelStatementBase>,
  val statements: List<PsiStatement>,
  givenParent: UElement?,
  private val addDummyBreak: Boolean = false
) : JavaAbstractUExpression(givenParent), USwitchClauseExpressionWithBody {
  override val sourcePsi: PsiSwitchLabelStatementBase = labels.first()

  override val caseValues: List<UExpression> by lz {
    labels.flatMap {
      if (it.isDefaultCase) {
        listOf(JavaUDefaultCaseExpression(it, this))
      }
      else {
        it.caseLabelElementList?.elements.orEmpty().map { element ->
          if (element is PsiExpression) JavaConverter.convertOrEmpty(element, this)
          else UnknownJavaExpression(element, this)
        }
      }
    }
  }

  override val body: UExpressionList by lz {
    object : JavaUExpressionList(sourcePsi, JavaSpecialExpressionKinds.SWITCH_ENTRY, this) {

      override val expressions: List<UExpression>

      init {
        val expressions = ArrayList<UExpression>(this@JavaUSwitchEntry.statements.size)
        for (statement in this@JavaUSwitchEntry.statements) {
          expressions.add(JavaConverter.convertOrEmpty(statement, this))
        }
        if (addDummyBreak) {
          val lastValueExpressionPsi = expressions.lastOrNull()?.sourcePsi as? PsiExpression
          if (lastValueExpressionPsi != null)
            expressions[expressions.size - 1] = DummyYieldExpression(lastValueExpressionPsi, this)
        }

        this.expressions = expressions
      }

      override fun asRenderString() = buildString {
        appendLine("{")
        expressions.forEach { appendLine(it.asRenderString().withMargin) }
        appendLine("}")
      }
    }
  }
}

internal class DummyYieldExpression(
  val expressionPsi: PsiExpression,
  override val uastParent: UElement?
) : UYieldExpression {
  override val javaPsi: PsiElement? = null
  override val sourcePsi: PsiElement? = null
  override val psi: PsiElement?
    get() = null
  override val label: String?
    get() = null
  override val uAnnotations: List<UAnnotation>
    get() = emptyList()

  override val expression: UExpression? by lz { JavaConverter.convertExpression(expressionPsi, this) }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as DummyYieldExpression
    return expressionPsi == other.expressionPsi
  }

  override fun hashCode(): Int = expressionPsi.hashCode()
}

@ApiStatus.Internal
class JavaUDefaultCaseExpression(
  override val sourcePsi: PsiElement?,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UElement {

  override val uAnnotations: List<UAnnotation>
    get() = emptyList()

  override fun asLogString(): String = "UDefaultCaseExpression"

  override fun asRenderString(): String = "else"
}
