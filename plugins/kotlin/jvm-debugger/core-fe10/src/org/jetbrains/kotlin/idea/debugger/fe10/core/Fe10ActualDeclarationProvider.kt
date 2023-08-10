// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.fe10.core

import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.debugger.core.breakpoints.ActualDeclarationProvider
import org.jetbrains.kotlin.idea.util.actualsForExpected
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.KtDeclaration

object Fe10ActualDeclarationProvider : ActualDeclarationProvider {
    override fun getActualJvmDeclaration(declaration: KtDeclaration): KtDeclaration =
        declaration.actualsForExpected().firstOrNull { it.platform.isJvm() } ?: declaration
}
