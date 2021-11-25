// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.compiler.backwardRefs.SearchId
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.ClassUtil
import org.jetbrains.jps.backwardRefs.CompilerRef
import org.jetbrains.jps.backwardRefs.NameEnumerator
import org.jetbrains.kotlin.idea.util.jvmFqName
import org.jetbrains.kotlin.idea.util.toJvmFqName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject

@IntellijInternalApi
sealed class FqNameWrapper {
    abstract val fqName: FqName
    abstract val jvmFqName: String

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FqNameWrapper) return false
        if (jvmFqName != other.jvmFqName) return false
        return true
    }

    override fun hashCode(): Int = jvmFqName.hashCode()

    companion object {
        fun createFromFqName(fqName: FqName): FqNameWrapper = FqNameBasedWrapper(fqName)
        fun createFromJvmFqName(jvmFqName: String): FqNameWrapper = JvmFqNameBasedWrapper(jvmFqName)
        fun createFromSearchId(searchId: SearchId): FqNameWrapper? = searchId.deserializedName?.let(::JvmFqNameBasedWrapper)
        fun createFromPsiElement(clazz: PsiElement): FqNameWrapper? = when (clazz) {
            is PsiClass -> ClassUtil.getJVMClassName(clazz)
            is KtClassOrObject -> clazz.jvmFqName
            else -> null
        }?.let(::createFromJvmFqName)
    }
}

private class FqNameBasedWrapper(override val fqName: FqName) : FqNameWrapper() {
    override val jvmFqName: String = fqName.toJvmFqName
}

private class JvmFqNameBasedWrapper(override val jvmFqName: String) : FqNameWrapper() {
    override val fqName: FqName = FqName(jvmFqName.replace('$', '.'))
}

fun FqNameWrapper.asJavaCompilerClassRef(nameEnumerator: NameEnumerator): CompilerRef.JavaCompilerClassRef =
    JavaCompilerClassRefWithSearchId.create(jvmFqName, nameEnumerator)
