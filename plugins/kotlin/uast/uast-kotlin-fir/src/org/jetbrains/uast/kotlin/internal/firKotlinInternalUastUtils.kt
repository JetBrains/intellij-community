// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin.internal

import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.asJava.getRepresentativeLightMethod
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.frontend.api.calls.KtCall
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.kotlin.FirKotlinUastLanguagePlugin
import org.jetbrains.uast.kotlin.lz
import java.lang.IllegalStateException

val firKotlinUastPlugin: FirKotlinUastLanguagePlugin by lz {
    UastLanguagePlugin.getInstances().single { it.language == KotlinLanguage.INSTANCE } as FirKotlinUastLanguagePlugin?
        ?: FirKotlinUastLanguagePlugin()
}

internal fun KtCall.toPsiMethod(): PsiMethod? {
    if (isErrorCall) return null
    val psi = targetFunction.candidates.singleOrNull()?.psi ?: return null
    try {
        return psi.getRepresentativeLightMethod()
    } catch (e: IllegalStateException) {
        // TODO: Creating FirModuleResolveState is not yet supported for LibrarySourceInfo(libraryName=myLibrary)
        //  this happens while destructuring a variable via Pair casting (testDestructuringDeclaration).
        return null
    }
}
