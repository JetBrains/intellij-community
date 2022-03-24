// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.symbols

import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.nj2k.tree.JKClass
import org.jetbrains.kotlin.nj2k.types.JKTypeFactory
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtTypeAlias


sealed class JKClassSymbol : JKSymbol

class JKUniverseClassSymbol(override val typeFactory: JKTypeFactory) : JKClassSymbol(), JKUniverseSymbol<JKClass> {
    override lateinit var target: JKClass
    override val name: String
        get() = if (target.classKind == JKClass.ClassKind.COMPANION) {
            "Companion"
        } else {
            target.name.value
        }
}

class JKMultiverseClassSymbol(
    override val target: PsiClass,
    override val typeFactory: JKTypeFactory
) : JKClassSymbol(), JKMultiverseSymbol<PsiClass>

class JKMultiverseKtClassSymbol(
    override val target: KtClassOrObject,
    override val typeFactory: JKTypeFactory
) : JKClassSymbol(), JKMultiverseKtSymbol<KtClassOrObject>

class JKTypeAliasKtClassSymbol(
    override val target: KtTypeAlias,
    override val typeFactory: JKTypeFactory
) : JKClassSymbol(), JKMultiverseKtSymbol<KtTypeAlias>

class JKUnresolvedClassSymbol(
    override val target: String,
    override val typeFactory: JKTypeFactory
) : JKClassSymbol(), JKUnresolvedSymbol