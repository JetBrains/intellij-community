// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k.symbols


import com.intellij.psi.PsiVariable
import org.jetbrains.kotlin.j2k.Nullability.NotNull
import org.jetbrains.kotlin.nj2k.tree.JKVariable
import org.jetbrains.kotlin.nj2k.types.JKClassType
import org.jetbrains.kotlin.nj2k.types.JKType
import org.jetbrains.kotlin.nj2k.types.JKTypeFactory
import org.jetbrains.kotlin.nj2k.types.toJK
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.psiUtil.containingClass

internal sealed class JKFieldSymbol : JKSymbol {
    abstract val fieldType: JKType?
}

internal class JKUniverseFieldSymbol(override val typeFactory: JKTypeFactory) : JKFieldSymbol(), JKUniverseSymbol<JKVariable> {
    override val fieldType: JKType
        get() = target.type.type

    override lateinit var target: JKVariable
}

internal class JKMultiverseFieldSymbol(
    override val target: PsiVariable,
    override val typeFactory: JKTypeFactory
) : JKFieldSymbol(), JKMultiverseSymbol<PsiVariable> {
    override val fieldType: JKType
        get() = typeFactory.fromPsiType(target.type)
}

internal class JKMultiversePropertySymbol(
    override val target: KtCallableDeclaration,
    override val typeFactory: JKTypeFactory
) : JKFieldSymbol(), JKMultiverseKtSymbol<KtCallableDeclaration> {
    override val fieldType: JKType?
        get() = target.typeReference?.toJK(typeFactory)
}

internal class JKMultiverseKtEnumEntrySymbol(
    override val target: KtEnumEntry,
    override val typeFactory: JKTypeFactory
) : JKFieldSymbol(), JKMultiverseKtSymbol<KtEnumEntry> {
    override val fieldType: JKType?
        get() = target.containingClass()?.let { klass ->
            JKClassType(
                symbolProvider.provideDirectSymbol(klass) as? JKClassSymbol ?: return@let null,
                nullability = NotNull
            )
        }
}

internal class JKUnresolvedField(
    override val target: String,
    override val typeFactory: JKTypeFactory
) : JKFieldSymbol(), JKUnresolvedSymbol {
    override val fieldType: JKType
        get() = typeFactory.types.nullableAny
}

