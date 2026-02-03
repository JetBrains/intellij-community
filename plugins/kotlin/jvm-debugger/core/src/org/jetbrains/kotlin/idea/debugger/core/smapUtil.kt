// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.core

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.codegen.inline.SMAP
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.jvm.JvmClassName

enum class SourceLineKind {
    CALL_LINE,
    EXECUTED_LINE
}

fun mapStacktraceLineToSource(
    smapData: SMAP,
    line: Int,
    project: Project,
    lineKind: SourceLineKind,
    searchScope: GlobalSearchScope
): Pair<KtFile, Int>? {
    val interval = smapData.findRange(line) ?: return null
    val location = when (lineKind) {
        SourceLineKind.CALL_LINE -> interval.callSite
        SourceLineKind.EXECUTED_LINE -> interval.mapDestToSource(line)
    } ?: return null

    val jvmName = JvmClassName.byInternalName(location.path)
    val sourceFile = DebuggerUtils.findSourceFileForClassIncludeLibrarySources(
        project, searchScope, jvmName, location.file
    ) ?: return null

    return sourceFile to location.line - 1
}
