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

class JavaUTryExpression(
        override val psi: PsiTryStatement,
        override val uastParent: UElement?
) : JavaAbstractUExpression(), UTryExpression {
    override val tryClause by lz { JavaConverter.convertOrEmpty(psi.tryBlock, this) }
    override val catchClauses by lz { psi.catchSections.map { JavaUCatchClause(it, this) } }
    override val finallyClause by lz { psi.finallyBlock?.let { JavaConverter.convertBlock(it, this) } }

    override val resourceVariables by lz {
        psi.resourceList
                ?.filterIsInstance<PsiResourceVariable>()
                ?.map { JavaUVariable.create(it, this) }
                ?: emptyList()
    }

    override val hasResources: Boolean
        get() = psi.resourceList != null

    override val tryIdentifier: UIdentifier
        get() = UIdentifier(psi.getChildByRole(ChildRole.TRY_KEYWORD), this)

    override val finallyIdentifier: UIdentifier?
        get() = psi.getChildByRole(ChildRole.FINALLY_KEYWORD)?.let { UIdentifier(it, this) }
}

class JavaUCatchClause(
        override val psi: PsiCatchSection,
        override val uastParent: UElement?
) : JavaAbstractUElement(), UCatchClause {
    override val body by lz { JavaConverter.convertOrEmpty(psi.catchBlock, this) }
    
    override val parameters by lz {
        (psi.parameter?.let { listOf(it) } ?: emptyList()).map { JavaUParameter(it, this) }
    }

    override val typeReferences by lz {
        val typeElement = psi.parameter?.typeElement ?: return@lz emptyList<UTypeReferenceExpression>()
        if (typeElement.type is PsiDisjunctionType) {
            typeElement.children.filterIsInstance<PsiTypeElement>().map { JavaUTypeReferenceExpression(it, this) }
        } else {
            listOf(JavaUTypeReferenceExpression(typeElement, this))
        }
    }
}