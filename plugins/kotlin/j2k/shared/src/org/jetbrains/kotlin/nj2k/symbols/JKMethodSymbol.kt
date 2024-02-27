// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.symbols


import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.j2k.Nullability
import org.jetbrains.kotlin.nj2k.tree.JKClass
import org.jetbrains.kotlin.nj2k.tree.JKMethod
import org.jetbrains.kotlin.nj2k.types.*
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

sealed class JKMethodSymbol : JKSymbol {
    context(KtAnalysisSession)
    abstract val receiverType: JKType?

    context(KtAnalysisSession)
    abstract val parameterTypes: List<JKType>?

    context(KtAnalysisSession)
    abstract val returnType: JKType?
}

class JKUniverseMethodSymbol(override val typeFactory: JKTypeFactory) : JKMethodSymbol(), JKUniverseSymbol<JKMethod> {
    context(KtAnalysisSession)
    override val receiverType: JKType?
        get() = target.parent.safeAs<JKClass>()?.let {
            JKClassType(symbolProvider.provideUniverseSymbol(it))
        }

    context(KtAnalysisSession)
    override val parameterTypes: List<JKType>
        get() = target.parameters.map { it.type.type }

    context(KtAnalysisSession)
    override val returnType: JKType
        get() = target.returnType.type

    override lateinit var target: JKMethod
}

class JKMultiverseMethodSymbol(
    override val target: PsiMethod,
    override val typeFactory: JKTypeFactory
) : JKMethodSymbol(), JKMultiverseSymbol<PsiMethod> {
    context(KtAnalysisSession)
    override val receiverType: JKType?
        get() = target.containingClass?.let {
            JKClassType(symbolProvider.provideDirectSymbol(it) as JKClassSymbol)
        }
    override val parameterTypes: List<JKType>
        get() = target.parameterList.parameters.map { typeFactory.fromPsiType(it.type) }
    override val returnType: JKType
        get() = target.returnType?.let { typeFactory.fromPsiType(it) } // null for constructor call
            ?: symbolProvider.provideClassSymbol(target.kotlinFqName!!).asType(Nullability.NotNull)

    override val fqName: String
        get() {
            val kotlinFqName = super.fqName
            return if (target is KtLightMethod) "$kotlinFqName.$name" else kotlinFqName
        }
}

class JKMultiverseFunctionSymbol(
    override val target: KtFunction,
    override val typeFactory: JKTypeFactory
) : JKMethodSymbol(), JKMultiverseKtSymbol<KtFunction> {
    context(KtAnalysisSession)
    override val receiverType: JKType?
        get() = target.receiverTypeReference?.toJK(typeFactory)

    context(KtAnalysisSession)
    @Suppress("UNCHECKED_CAST")
    override val parameterTypes: List<JKType>?
        get() = target.valueParameters.map { parameter ->
            val type = parameter.typeReference?.toJK(typeFactory)
            type?.let {
                if (parameter.isVarArg) {
                    JKClassType(
                        symbolProvider.provideClassSymbol(StandardNames.FqNames.array.toSafe()),
                        parameters = listOf(it)
                    )
                } else it
            }
        }.takeIf { parameters -> parameters.all { it != null } } as? List<JKType>

    context(KtAnalysisSession)
    override val returnType: JKType?
        get() = target.typeReference?.toJK(typeFactory)
}

class JKUnresolvedMethod(
    override val target: String,
    override val typeFactory: JKTypeFactory,
    override val returnType: JKType = JKNoType
) : JKMethodSymbol(), JKUnresolvedSymbol {
    constructor(target: PsiReference, typeFactory: JKTypeFactory) : this(target.canonicalText, typeFactory)

    context(KtAnalysisSession)
    override val receiverType: JKType
        get() = typeFactory.types.nullableAny

    context(KtAnalysisSession)
    override val parameterTypes: List<JKType>
        get() = emptyList()
}

class KtClassImplicitConstructorSymbol(
    override val target: KtLightMethod,
    override val typeFactory: JKTypeFactory
) : JKMethodSymbol(), JKMultiverseSymbol<KtLightMethod> {

    context(KtAnalysisSession)
    override val receiverType: JKType?
        get() = null

    context(KtAnalysisSession)
    override val parameterTypes: List<JKType>
        get() = emptyList()

    context(KtAnalysisSession)
    override val returnType: JKType?
        get() = target.returnType?.let(typeFactory::fromPsiType)
}
