// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.symbols

import com.intellij.psi.PsiTypeParameter
import org.jetbrains.kotlin.nj2k.tree.JKTypeParameter
import org.jetbrains.kotlin.nj2k.tree.JKTypeParameterListOwner
import org.jetbrains.kotlin.nj2k.types.JKTypeFactory
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtTypeParameterListOwner
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal sealed class JKTypeParameterSymbol : JKSymbol {
    abstract val index: Int
}

internal class JKMultiverseTypeParameterSymbol(
    override val target: PsiTypeParameter,
    override val typeFactory: JKTypeFactory
) : JKTypeParameterSymbol(), JKMultiverseSymbol<PsiTypeParameter> {
    override val index: Int
        get() = target.index
}

internal class JKUniverseTypeParameterSymbol(
    override val typeFactory: JKTypeFactory
) : JKTypeParameterSymbol(), JKUniverseSymbol<JKTypeParameter> {
    override val index: Int
        get() = declaredIn?.safeAs<JKTypeParameterListOwner>()?.typeParameterList?.typeParameters?.indexOf(target) ?: -1
    override lateinit var target: JKTypeParameter
}

internal class JKMultiverseKtTypeParameterSymbol(
    override val target: KtTypeParameter,
    override val typeFactory: JKTypeFactory
) : JKTypeParameterSymbol(), JKMultiverseKtSymbol<KtTypeParameter> {
    override val index: Int
        get() = target.getParentOfType<KtTypeParameterListOwner>(strict = false)?.typeParameters?.indexOf(target) ?: -1
}