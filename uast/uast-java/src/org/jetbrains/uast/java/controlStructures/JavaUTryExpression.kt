// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java

import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.ChildRole
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

@ApiStatus.Internal
class JavaUTryExpression(
  override val sourcePsi: PsiTryStatement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UTryExpression {

  private val tryClausePart = UastLazyPart<UExpression>()
  private val catchClausesPart = UastLazyPart<List<UCatchClause>>()
  private val finallyClausePart = UastLazyPart<UBlockExpression?>()
  private val resourceVariablesPart = UastLazyPart<List<UAnnotated>>()

  override val tryClause: UExpression
    get() = tryClausePart.getOrBuild { JavaConverter.convertOrEmpty(sourcePsi.tryBlock, this) }

  override val catchClauses: List<UCatchClause>
    get() = catchClausesPart.getOrBuild { sourcePsi.catchSections.map { JavaUCatchClause(it, this) } }

  override val finallyClause: UBlockExpression?
    get() = finallyClausePart.getOrBuild { sourcePsi.finallyBlock?.let { JavaConverter.convertBlock(it, this) } }

  @Deprecated("This API doesn't support resource expression", replaceWith = ReplaceWith("resources"))
  override val resourceVariables: List<UVariable> get() = resources.filterIsInstance<UVariable>()

  override val resources: List<UAnnotated>
    get() = resourceVariablesPart.getOrBuild {
      sourcePsi.resourceList?.mapNotNull { resourceListElem ->
        when (resourceListElem) {
          is PsiResourceVariable -> JavaUVariable.create(resourceListElem, this)
          is PsiResourceExpression -> JavaConverter.convertOrEmpty(resourceListElem.expression, this)
          else -> null
        }
      } ?: emptyList()
    }

  override val hasResources: Boolean
    get() = sourcePsi.resourceList != null

  override val tryIdentifier: UIdentifier
    get() = UIdentifier(sourcePsi.getChildByRole(ChildRole.TRY_KEYWORD), this)

  override val finallyIdentifier: UIdentifier?
    get() = sourcePsi.getChildByRole(ChildRole.FINALLY_KEYWORD)?.let { UIdentifier(it, this) }
}

@ApiStatus.Internal
class JavaUCatchClause(
  override val sourcePsi: PsiCatchSection,
  givenParent: UElement?
) : JavaAbstractUElement(givenParent), UCatchClause {

  private val bodyPart = UastLazyPart<UExpression>()
  private val parametersPart = UastLazyPart<List<UParameter>>()
  private val typeReferencesPart = UastLazyPart<List<UTypeReferenceExpression>>()

  override val body: UExpression
    get() = bodyPart.getOrBuild { JavaConverter.convertOrEmpty(sourcePsi.catchBlock, this) }

  override val parameters: List<UParameter>
    get() = parametersPart.getOrBuild {
      (sourcePsi.parameter?.let { listOf(it) } ?: emptyList())
        .map { JavaUParameter(it, this) }
    }

  override val typeReferences: List<UTypeReferenceExpression>
    get() = typeReferencesPart.getOrBuild {
      val typeElement = sourcePsi.parameter?.typeElement ?: return@getOrBuild emptyList<UTypeReferenceExpression>()
      if (typeElement.type is PsiDisjunctionType) {
        typeElement.children.filterIsInstance<PsiTypeElement>().map { JavaUTypeReferenceExpression(it, this) }
      }
      else {
        listOf(JavaUTypeReferenceExpression(typeElement, this))
      }
    }
}
