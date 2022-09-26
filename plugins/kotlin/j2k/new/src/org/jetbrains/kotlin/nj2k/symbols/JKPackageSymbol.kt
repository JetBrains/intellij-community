// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k.symbols

import com.intellij.psi.PsiPackage
import org.jetbrains.kotlin.nj2k.tree.JKPackageDeclaration
import org.jetbrains.kotlin.nj2k.types.JKTypeFactory


sealed class JKPackageSymbol : JKSymbol

class JKMultiversePackageSymbol(
    override val target: PsiPackage,
    override val typeFactory: JKTypeFactory
) : JKPackageSymbol(), JKMultiverseSymbol<PsiPackage> {
    override val declaredIn: JKSymbol?
        get() = null
}

class JKUniversePackageSymbol(
    override val typeFactory: JKTypeFactory
) : JKPackageSymbol(), JKUniverseSymbol<JKPackageDeclaration> {
    override lateinit var target: JKPackageDeclaration

    override val declaredIn: JKSymbol?
        get() = null

    override val fqName: String
        get() = name
}