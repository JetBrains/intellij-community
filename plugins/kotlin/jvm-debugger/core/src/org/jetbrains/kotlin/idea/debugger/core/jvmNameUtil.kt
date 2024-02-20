// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core

import com.intellij.util.asSafely
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.annotations.KtConstantAnnotationValue
import org.jetbrains.kotlin.analysis.api.annotations.annotations
import org.jetbrains.kotlin.analysis.api.base.KtConstantValue
import org.jetbrains.kotlin.analysis.api.symbols.KtDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol

@ApiStatus.Internal
fun KtFunctionSymbol.getByteCodeMethodName(): String {
    val jvmName = annotations
        .filter { it.classId?.asFqNameString() == "kotlin.jvm.JvmName" }
        .firstNotNullOfOrNull {
            it.arguments.singleOrNull { a -> a.name.asString() == "name" }
                ?.expression?.asSafely<KtConstantAnnotationValue>()
                ?.constantValue?.asSafely<KtConstantValue.KtStringConstantValue>()?.value
        }
    if (jvmName != null) return jvmName
    return name.asString()
}

context(KtAnalysisSession)
@ApiStatus.Internal
fun KtDeclarationSymbol.isInlineClass(): Boolean = this is KtNamedClassOrObjectSymbol && this.isInline
