// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core

import com.intellij.util.asSafely
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KtConstantAnnotationValue
import org.jetbrains.kotlin.analysis.api.annotations.annotations
import org.jetbrains.kotlin.analysis.api.base.KtConstantValue
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.idea.debugger.base.util.fqnToInternalName
import org.jetbrains.kotlin.idea.debugger.base.util.internalNameToFqn
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.jvm.JvmClassName

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

@ApiStatus.Internal
@RequiresReadLock
fun KtDeclaration.getClassName(): String? = analyze(this) {
    val symbol = getSymbol() as? KtFunctionLikeSymbol ?: return@analyze null
    symbol.getJvmInternalClassName()?.internalNameToFqn()
}

context(KtAnalysisSession)
@ApiStatus.Internal
fun KtFunctionLikeSymbol.getJvmInternalClassName(): String? {
    val classOrObject = getContainingClassOrObjectSymbol()
    return if (classOrObject == null) {
        val fileSymbol = getContainingFileSymbol() ?: return null
        val file = fileSymbol.psi as? KtFile ?: return null
        JvmFileClassUtil.getFileClassInfoNoResolve(file).fileClassFqName.asString().fqnToInternalName()
    } else {
        val classId = classOrObject.classIdIfNonLocal ?: return null
        JvmClassName.internalNameByClassId(classId)
    }
}

context(KtAnalysisSession)
@ApiStatus.Internal
fun KtFunctionLikeSymbol.getContainingClassOrObjectSymbol(): KtClassOrObjectSymbol? {
    var symbol = getContainingSymbol()
    while (symbol != null) {
        if (symbol is KtClassOrObjectSymbol) return symbol
        symbol = symbol.getContainingSymbol()
    }
    return null
}
