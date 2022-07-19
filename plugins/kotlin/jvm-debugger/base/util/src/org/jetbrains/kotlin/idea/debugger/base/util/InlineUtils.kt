// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.base.util

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.debugger.jdi.LocalVariableProxyImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.sun.jdi.Location
import com.sun.jdi.ReferenceType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.successfulFunctionCallOrNull
import org.jetbrains.kotlin.codegen.AsmUtil
import org.jetbrains.kotlin.codegen.inline.INLINE_FUN_VAR_SUFFIX
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.psi.getLineCount
import org.jetbrains.kotlin.idea.base.psi.getLineStartOffset
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_ARGUMENT
import org.jetbrains.kotlin.load.java.JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_FUNCTION
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.jvm.JvmClassName
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

val INLINED_THIS_REGEX = run {
    val escapedName = Regex.escape(INLINE_DECLARATION_SITE_THIS)
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

@RequiresReadLock
@ApiStatus.Internal
fun getLocationsOfInlinedLine(type: ReferenceType, position: SourcePosition, sourceSearchScope: GlobalSearchScope): List<Location> {
    val line = position.line
    val file = position.file
    val project = position.file.project

    val lineStartOffset = file.getLineStartOffset(line) ?: return listOf()
    val element = file.findElementAt(lineStartOffset) ?: return listOf()
    val ktElement = element.parents.firstIsInstanceOrNull<KtElement>() ?: return listOf()

    val isInInline = element.parents.any { it is KtFunction && it.hasModifier(KtTokens.INLINE_KEYWORD) }

    if (!isInInline) {
        // Lambdas passed to cross-inline arguments are inlined when they are used in non-inlined lambdas
        val isInCrossInlineArgument = isInCrossInlineArgument(ktElement)
        if (!isInCrossInlineArgument) {
            return listOf()
        }
    }

    val lines = inlinedLinesNumbers(line + 1, position.file.name, FqName(type.name()), type.sourceName(), project, sourceSearchScope)

    return lines.flatMap { DebuggerUtilsAsync.locationsOfLineSync(type, it) }
}

private fun isInCrossInlineArgument(ktElement: KtElement): Boolean {
    for (function in ktElement.parents.filterIsInstance<KtFunction>()) {
        when (function) {
            is KtFunctionLiteral -> {
                val lambdaExpression = function.parent as? KtLambdaExpression ?: continue
                val argumentExpression = lambdaExpression.parent
                if (argumentExpression is KtValueArgument && isCrossInlineArgument(lambdaExpression)) {
                    return true
                }
            }
            is KtNamedFunction -> {
                if (function.parent is KtValueArgument && isCrossInlineArgument(function)) {
                    return true
                }
            }
        }
    }

    return false
}

private fun isCrossInlineArgument(argumentExpression: KtExpression): Boolean {
    val callExpression = KtPsiUtil.getParentCallIfPresent(argumentExpression) ?: return false

    return analyze(callExpression) f@ {
        val call = callExpression.resolveCall()?.successfulFunctionCallOrNull() ?: return@f false
        val parameter = call.argumentMapping[argumentExpression]?.symbol ?: return@f false
        return@f parameter.isCrossinline
    }
}

private fun inlinedLinesNumbers(
    inlineLineNumber: Int, inlineFileName: String,
    destinationTypeFqName: FqName, destinationFileName: String,
    project: Project, sourceSearchScope: GlobalSearchScope
): List<Int> {
    val internalName = destinationTypeFqName.asString().replace('.', '/')
    val jvmClassName = JvmClassName.byInternalName(internalName)

    val file = DebuggerUtils.findSourceFileForClassIncludeLibrarySources(project, sourceSearchScope, jvmClassName, destinationFileName)
        ?: return listOf()

    val virtualFile = file.virtualFile ?: return listOf()

    val sourceMap = KotlinSourceMapCache.getInstance(project).getSourceMap(virtualFile, jvmClassName) ?: return listOf()

    val mappingsToInlinedFile = sourceMap.fileMappings.filter { it.name == inlineFileName }
    val mappingIntervals = mappingsToInlinedFile.flatMap { it.lineMappings }

    return mappingIntervals.asSequence().filter { rangeMapping -> rangeMapping.hasMappingForSource(inlineLineNumber) }
        .map { rangeMapping -> rangeMapping.mapSourceToDest(inlineLineNumber) }.filter { line -> line != -1 }.toList()
}