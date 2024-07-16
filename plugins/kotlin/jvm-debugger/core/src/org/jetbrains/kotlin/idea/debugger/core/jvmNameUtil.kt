// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core

import com.intellij.util.asSafely
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.annotations.annotations
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.idea.debugger.base.util.fqnToInternalName
import org.jetbrains.kotlin.idea.debugger.base.util.internalNameToFqn
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.jvm.JvmClassName

@ApiStatus.Internal
fun KaNamedFunctionSymbol.getByteCodeMethodName(): String {
    val jvmName = annotations
        .filter { it.classId?.asFqNameString() == "kotlin.jvm.JvmName" }
        .firstNotNullOfOrNull {
            it.arguments.singleOrNull { a -> a.name.asString() == "name" }
                ?.expression?.asSafely<KaAnnotationValue.ConstantValue>()
                ?.value?.asSafely<KaConstantValue.StringValue>()?.value
        }
    if (jvmName != null) return jvmName
    return name.asString()
}

context(KaSession)
@ApiStatus.Internal
fun KaDeclarationSymbol.isInlineClass(): Boolean = this is KaNamedClassOrObjectSymbol && this.isInline

@ApiStatus.Internal
@RequiresReadLock
fun KtDeclaration.getClassName(): String? = analyze(this) {
    val symbol = symbol as? KaFunctionSymbol ?: return@analyze null
    symbol.getJvmInternalClassName()?.internalNameToFqn()
}

context(KaSession)
@ApiStatus.Internal
fun KaFunctionSymbol.getJvmInternalClassName(): String? {
    val classOrObject = getContainingClassOrObjectSymbol()
    return if (classOrObject == null) {
        val fileSymbol = containingFile ?: return null
        val file = fileSymbol.psi as? KtFile ?: return null
        JvmFileClassUtil.getFileClassInfoNoResolve(file).fileClassFqName.asString().fqnToInternalName()
    } else {
        val classId = classOrObject.classId ?: return null
        JvmClassName.internalNameByClassId(classId)
    }
}

context(KaSession)
@ApiStatus.Internal
fun KaFunctionSymbol.getContainingClassOrObjectSymbol(): KaClassSymbol? {
    var symbol = containingDeclaration
    while (symbol != null) {
        if (symbol is KaClassSymbol) return symbol
        symbol = symbol.containingDeclaration
    }
    return null
}
