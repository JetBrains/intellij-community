// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.ValueArgument
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.internal.DelegatedMultiResolve

@ApiStatus.Internal
class KotlinUVarargExpression(
    private val valueArgs: List<ValueArgument>,
    uastParent: UElement?,
) : KotlinAbstractUExpression(uastParent), UCallExpression, DelegatedMultiResolve {

    private val valueArgumentsPart = UastLazyPart<List<UExpression>>()

    override val kind: UastCallKind = UastCallKind.NESTED_ARRAY_INITIALIZER

    override val valueArguments: List<UExpression>
        get() = valueArgumentsPart.getOrBuild {
            valueArgs.map {
                it.getArgumentExpression()?.let { argumentExpression ->
                    baseResolveProviderService.languagePlugin.convertOpt(argumentExpression, this)
                } ?: UastEmptyExpression(this)
            }
        }

    override fun getArgumentForParameter(i: Int): UExpression? = valueArguments.getOrNull(i)

    override val valueArgumentCount: Int
        get() = valueArgs.size

    override val psi: PsiElement?
        get() = null

    override val methodIdentifier: UIdentifier?
        get() = null

    override val classReference: UReferenceExpression?
        get() = null

    override val methodName: String?
        get() = null

    override val typeArgumentCount: Int
        get() = 0

    override val typeArguments: List<PsiType>
        get() = emptyList()

    override val returnType: PsiType?
        get() = null

    override fun resolve() = null

    override val receiver: UExpression?
        get() = null

    override val receiverType: PsiType?
        get() = null
}
