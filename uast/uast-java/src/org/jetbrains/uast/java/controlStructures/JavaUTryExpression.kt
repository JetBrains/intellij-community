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
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

@ApiStatus.Internal
class JavaUTryExpression(
  override val sourcePsi: PsiTryStatement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UTryExpression {
  override val tryClause: UExpression by lz { JavaConverter.convertOrEmpty(sourcePsi.tryBlock, this) }
  override val catchClauses: List<UCatchClause> by lz { sourcePsi.catchSections.map { JavaUCatchClause(it, this) } }
  override val finallyClause: UBlockExpression? by lz { sourcePsi.finallyBlock?.let { JavaConverter.convertBlock(it, this) } }

  override val resourceVariables: List<UVariable> by lz {
    sourcePsi.resourceList
      ?.filterIsInstance<PsiResourceVariable>()
      ?.map { JavaUVariable.create(it, this) }
    ?: emptyList()
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
  override val body: UExpression by lz { JavaConverter.convertOrEmpty(sourcePsi.catchBlock, this) }

  override val parameters: List<UParameter> by lz {
    (sourcePsi.parameter?.let { listOf(it) } ?: emptyList()).map { JavaUParameter(it, this) }
  }

  override val typeReferences: List<UTypeReferenceExpression> by lz {
    val typeElement = sourcePsi.parameter?.typeElement ?: return@lz emptyList<UTypeReferenceExpression>()
    if (typeElement.type is PsiDisjunctionType) {
      typeElement.children.filterIsInstance<PsiTypeElement>().map { JavaUTypeReferenceExpression(it, this) }
    }
    else {
      listOf(JavaUTypeReferenceExpression(typeElement, this))
    }
  }
}
