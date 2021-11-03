// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.compiler.backwardRefs.SearchId
import com.intellij.compiler.backwardRefs.SearchIdHolder
import org.jetbrains.jps.backwardRefs.CompilerRef
import org.jetbrains.jps.backwardRefs.NameEnumerator
import org.jetbrains.kotlin.idea.util.jvmFqName
import org.jetbrains.kotlin.psi.KtClassOrObject

class JavaCompilerClassRefWithSearchId private constructor(
    override val jvmClassName: String,
    qualifierId: Int,
) : CompilerRef.JavaCompilerClassRef(qualifierId), CompilerClassHierarchyElementDefWithSearchId {
    override val searchId: SearchId get() = SearchId(jvmClassName)

    override fun createMethod(name: Int, parameterCount: Int): CompilerRef.CompilerMember = JavaCompilerMethodRefWithSearchIdOwner(
        owner = this,
        name = name,
        parameterCount = parameterCount,
    )

    override fun createField(name: Int): CompilerRef.CompilerMember = JavaCompilerFieldRefWithSearchIdOwner(owner = this, name = name)

    companion object {
        fun create(classOrObject: KtClassOrObject, names: NameEnumerator): JavaCompilerClassRefWithSearchId? {
            val qualifier = classOrObject.jvmFqName ?: return null
            return JavaCompilerClassRefWithSearchId(qualifier, names.tryEnumerate(qualifier))
        }

        fun create(jvmClassName: String, names: NameEnumerator): JavaCompilerClassRefWithSearchId =
            JavaCompilerClassRefWithSearchId(jvmClassName, names.tryEnumerate(jvmClassName))
    }
}

private class JavaCompilerFieldRefWithSearchIdOwner(
    private val owner: CompilerClassHierarchyElementDefWithSearchId,
    name: Int,
) : CompilerRef.JavaCompilerFieldRef(owner.name, name) {
    override fun getOwner(): CompilerRef.CompilerClassHierarchyElementDef = owner
}

private class JavaCompilerMethodRefWithSearchIdOwner(
    private val owner: CompilerClassHierarchyElementDefWithSearchId,
    name: Int,
    parameterCount: Int,
) : CompilerRef.JavaCompilerMethodRef(owner.name, name, parameterCount) {
    override fun getOwner(): CompilerRef.CompilerClassHierarchyElementDef = owner
}

interface CompilerClassHierarchyElementDefWithSearchId : CompilerRef.CompilerClassHierarchyElementDef, SearchIdHolder {
    fun createMethod(name: Int, parameterCount: Int): CompilerRef.CompilerMember
    fun createField(name: Int): CompilerRef.CompilerMember
    val jvmClassName: String
}