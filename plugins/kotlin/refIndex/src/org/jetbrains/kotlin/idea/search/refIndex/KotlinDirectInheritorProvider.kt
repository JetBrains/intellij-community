// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.compiler.backwardRefs.DirectInheritorProvider
import com.intellij.compiler.backwardRefs.SearchId
import com.intellij.openapi.project.Project
import org.jetbrains.jps.backwardRefs.CompilerRef
import org.jetbrains.jps.backwardRefs.NameEnumerator

class KotlinDirectInheritorProvider(private val project: Project) : DirectInheritorProvider {
    override fun findDirectInheritors(
        searchId: SearchId,
        nameEnumerator: NameEnumerator,
    ): Collection<CompilerRef.CompilerClassHierarchyElementDef> {
        val indexService = KotlinCompilerReferenceIndexService.getInstanceIfEnable(project) ?: return emptyList()
        return indexService.directKotlinSubtypesOf(searchId)?.map { it.asJavaCompilerClassRef(nameEnumerator) }.orEmpty()
    }
}