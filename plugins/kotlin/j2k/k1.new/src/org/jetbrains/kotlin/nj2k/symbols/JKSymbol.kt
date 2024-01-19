// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.symbols

import com.intellij.psi.PsiMember
import com.intellij.psi.PsiNamedElement
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.nj2k.JKSymbolProvider
import org.jetbrains.kotlin.nj2k.tree.JKDeclaration
import org.jetbrains.kotlin.nj2k.tree.JKFile
import org.jetbrains.kotlin.nj2k.tree.parentOfType
import org.jetbrains.kotlin.nj2k.types.JKTypeFactory
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal interface JKSymbol {
    val target: Any
    val declaredIn: JKSymbol?
    val fqName: String
    val name: String

    val typeFactory: JKTypeFactory
    val symbolProvider: JKSymbolProvider
        get() = typeFactory.symbolProvider
}

internal interface JKUniverseSymbol<T : JKDeclaration> : JKSymbol {
    override var target: T
    override val fqName: String
        get() {
            val qualifier =
                declaredIn?.fqName
                    ?: target
                        .parentOfType<JKFile>()
                        ?.packageDeclaration
                        ?.name
                        ?.value
            return qualifier?.takeIf { it.isNotBlank() }?.let { "$it." }.orEmpty() + name
        }

    override val name: String
        get() = target.name.value

    override val declaredIn: JKSymbol?
        get() = target.parentOfType<JKDeclaration>()?.let { symbolProvider.provideUniverseSymbol(it) }
            ?: target.parentOfType<JKFile>()?.packageDeclaration
                ?.takeIf { it.name.value.isNotEmpty() }
                ?.let { symbolProvider.provideUniverseSymbol(it) }
}

internal interface JKMultiverseSymbol<T : PsiNamedElement> : JKSymbol {
    override val target: T
    override val declaredIn: JKSymbol?
        get() = target.getStrictParentOfType<PsiMember>()?.let { symbolProvider.provideDirectSymbol(it) }
    override val fqName: String
        get() = target.kotlinFqName?.asString() ?: name
    override val name: String
        get() = target.name ?: SpecialNames.ANONYMOUS.asString()
}

internal interface JKMultiverseKtSymbol<T : KtNamedDeclaration> : JKSymbol {
    override val target: T
    override val name: String
        get() = target.name ?: SpecialNames.ANONYMOUS.asString()
    override val declaredIn: JKSymbol?
        get() = target.getStrictParentOfType<KtDeclaration>()?.let { symbolProvider.provideDirectSymbol(it) }
    override val fqName: String
        get() = target.fqName?.asString() ?: name
}

internal interface JKUnresolvedSymbol : JKSymbol {
    override val target: String
    override val declaredIn: JKSymbol?
        get() = null
    override val fqName: String
        get() = target
    override val name: String
        get() = target.substringAfterLast(".")
}

