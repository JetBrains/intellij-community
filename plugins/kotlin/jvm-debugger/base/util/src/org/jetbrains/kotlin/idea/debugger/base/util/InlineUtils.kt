// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.base.util

import com.intellij.debugger.jdi.LocalVariableProxyImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.codegen.AsmUtil
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.psi.getLineCount
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.debugger.base.util.KotlinDebuggerConstants.INLINE_FUN_VAR_SUFFIX
import org.jetbrains.kotlin.load.java.JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_ARGUMENT
import org.jetbrains.kotlin.load.java.JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_FUNCTION

val INLINED_THIS_REGEX = run {
    val escapedName = Regex.escape(AsmUtil.INLINE_DECLARATION_SITE_THIS)
    val escapedSuffix = Regex.escape(INLINE_FUN_VAR_SUFFIX)
    Regex("^$escapedName(?:$escapedSuffix)*$")
}

// Compute the current inline depth given a list of visible variables.
// All usages of this function should probably use [InlineStackFrame] instead,
// since the inline depth does not suffice to determine which variables
// are visible and this function will not work on a dex VM.
fun getInlineDepth(variables: List<LocalVariableProxyImpl>): Int {
    val rawInlineFunDepth = variables.count { it.name().startsWith(LOCAL_VARIABLE_NAME_PREFIX_INLINE_FUNCTION) }

    for (variable in variables.sortedByDescending { it.variable }) {
        val name = variable.name()
        val depth = getInlineDepth(name)
        if (depth > 0) {
            return depth
        } else if (name.startsWith(LOCAL_VARIABLE_NAME_PREFIX_INLINE_ARGUMENT)) {
            return 0
        }
    }

    return rawInlineFunDepth
}

fun getInlineDepth(variableName: String): Int {
    var endIndex = variableName.length
    var depth = 0

    val suffixLen = INLINE_FUN_VAR_SUFFIX.length
    while (endIndex >= suffixLen) {
        if (variableName.substring(endIndex - suffixLen, endIndex) != INLINE_FUN_VAR_SUFFIX) {
            break
        }

        depth++
        endIndex -= suffixLen
    }

    return depth
}

fun dropInlineSuffix(name: String): String {
    val depth = getInlineDepth(name)
    if (depth == 0) {
        return name
    }

    return name.dropLast(depth * INLINE_FUN_VAR_SUFFIX.length)
}

fun isInlineFrameLineNumber(file: VirtualFile, lineNumber: Int, project: Project): Boolean {
    if (RootKindFilter.projectSources.matches(project, file)) {
        val linesInFile = file.toPsiFile(project)?.getLineCount() ?: return false
        return lineNumber > linesInFile
    }

    return true
}
