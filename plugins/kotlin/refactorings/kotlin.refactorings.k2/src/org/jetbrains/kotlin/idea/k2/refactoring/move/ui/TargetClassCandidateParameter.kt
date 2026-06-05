// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.ui

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.containingModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtContextParameterList
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeReference

/**
 * Information about a value parameter, a context parameter or an extension receiver
 * that can be converted into a dispatch receiver for the purposes of Move to Class refactoring.
 */
@ApiStatus.Internal
class TargetClassCandidateParameter(
    val kind: TargetClassCandidateKind,
    val parameterName: String?,
    val targetClassFqName: FqName,
    @param:NlsSafe val displayName: String,
    @param:NlsSafe val typeText: String,
)

@ApiStatus.Internal
enum class TargetClassCandidateKind {
    VALUE_PARAMETER,
    EXTENSION_RECEIVER,
    CONTEXT_PARAMETER,
}

@RequiresReadLock
@ApiStatus.Internal
fun findTargetClassCandidates(declaration: KtCallableDeclaration): List<TargetClassCandidateParameter> {
    val result = mutableListOf<TargetClassCandidateParameter>()

    analyze(declaration) {
        declaration.receiverTypeReference?.let { receiverTypeReference ->
            result.addIfNotNull(
                createCandidate(
                    declaration = declaration,
                    parameterName = null,
                    typeReference = receiverTypeReference,
                    kind = TargetClassCandidateKind.EXTENSION_RECEIVER,
                    displayName = EXTENSION_RECEIVER_DISPLAY_NAME,
                )
            )
        }
        declaration.contextParametersList().mapNotNullTo(result) { contextParameter ->
            contextParameter.typeReference?.let { typeReference ->
                createCandidate(
                    declaration = declaration,
                    parameterName = contextParameter.name,
                    typeReference = typeReference,
                    kind = TargetClassCandidateKind.CONTEXT_PARAMETER,
                    displayName = contextParameter.name ?: ANONYMOUS_DISPLAY_NAME,
                )
            }
        }
        declaration.valueParameters.mapNotNullTo(result) { valueParameter ->
            valueParameter.typeReference?.let { typeReference ->
                createCandidate(
                    declaration = declaration,
                    parameterName = valueParameter.name,
                    typeReference = typeReference,
                    kind = TargetClassCandidateKind.VALUE_PARAMETER,
                    displayName = valueParameter.name ?: ANONYMOUS_DISPLAY_NAME,
                )
            }
        }
    }

    return result
}

/**
 * Creates a [TargetClassCandidateParameter] if the parameter can be used as a new dispatch receiver.
 *
 * The parameter (can be value/context parameter or extension receiver) must satisfy the following restrictions:
 * * The parameter type must be not nullable.
 * * The type must point to a Kotlin class-like type located in the project sources.
 * * The target class-like should be different from the current containing class of the [declaration].
 */
@OptIn(KaExperimentalApi::class)
private fun KaSession.createCandidate(
    declaration: KtCallableDeclaration,
    parameterName: String?,
    typeReference: KtTypeReference,
    kind: TargetClassCandidateKind,
    displayName: String,
): TargetClassCandidateParameter? {
    if (typeReference.type.isNullable) return null
    return typeReference.resolveSymbol()?.takeIf { classifierSymbol ->
        classifierSymbol.isSuitableTargetClass() && declaration.symbol.containingSymbol != classifierSymbol
    }?.importableFqName?.let { fqName ->
        TargetClassCandidateParameter(
            kind = kind,
            parameterName = parameterName,
            displayName = displayName,
            typeText = typeReference.text ?: "",
            targetClassFqName = fqName,
        )
    }
}

context(session: KaSession)
internal fun KaClassifierSymbol.isSuitableTargetClass(): Boolean {
    return this.containingModule is KaSourceModule && this.psi?.containingFile is KtFile
}

private fun KtCallableDeclaration.contextParametersList(): List<KtParameter> {
    val modifierList = modifierList ?: return emptyList()
    val contextParameterList = PsiTreeUtil.getChildOfType(modifierList, KtContextParameterList::class.java)
        ?: return emptyList()
    return contextParameterList.contextParameters
}

@NlsSafe
private const val EXTENSION_RECEIVER_DISPLAY_NAME: String = "<extension receiver>"

@NlsSafe
private const val ANONYMOUS_DISPLAY_NAME: String = "<anonymous>"
