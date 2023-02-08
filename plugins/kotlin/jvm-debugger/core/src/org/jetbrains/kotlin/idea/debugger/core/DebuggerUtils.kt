// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.debugger.impl.DebuggerUtilsImpl.getLocalVariableBorders
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.sun.jdi.LocalVariable
import com.sun.jdi.Location
import com.sun.jdi.ReferenceType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.successfulFunctionCallOrNull
import org.jetbrains.kotlin.asJava.finder.JavaElementFinder
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.indices.KotlinPackageIndexUtils.findFilesWithExactPackage
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.base.psi.getLineStartOffset
import org.jetbrains.kotlin.idea.base.util.KOTLIN_FILE_EXTENSIONS
import org.jetbrains.kotlin.idea.debugger.base.util.FileApplicabilityChecker
import org.jetbrains.kotlin.idea.debugger.base.util.KotlinSourceMapCache
import org.jetbrains.kotlin.idea.stubindex.KotlinFileFacadeFqNameIndex
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.kotlin.PackagePartClassUtils
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.jvm.JvmClassName
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import java.util.*

object DebuggerUtils {
    @set:TestOnly
    var forceRanking = false

    private val IR_BACKEND_LAMBDA_REGEX = ".+\\\$lambda[$-]\\d+".toRegex()

    fun findSourceFileForClassIncludeLibrarySources(
        project: Project,
        scope: GlobalSearchScope,
        className: JvmClassName,
        fileName: String,
        location: Location? = null
    ): KtFile? {
        return runReadAction {
            findSourceFileForClass(
              project,
              listOf(scope, KotlinSourceFilterScope.librarySources(GlobalSearchScope.allScope(project), project)),
              className,
              fileName,
              location
            )
        }
    }

    fun findSourceFileForClass(
        project: Project,
        scopes: List<GlobalSearchScope>,
        className: JvmClassName,
        fileName: String,
        location: Location?
    ): KtFile? {
        if (!isKotlinSourceFile(fileName)) return null
        if (DumbService.getInstance(project).isDumb) return null

        val partFqName = className.fqNameForClassNameWithoutDollars

        for (scope in scopes) {
            val files = findFilesByNameInPackage(className, fileName, project, scope)
                .filter { it.platform.isJvm() || it.platform.isCommon() }

            if (files.isEmpty()) {
                continue
            }

            if (files.size == 1 && !forceRanking || location == null) {
                return files.first()
            }

            val singleFile = runReadAction {
                val matchingFiles = KotlinFileFacadeFqNameIndex.get(partFqName.asString(), project, scope)
                PackagePartClassUtils.getFilesWithCallables(matchingFiles).singleOrNull { it.name == fileName }
            }

            if (singleFile != null) {
                return singleFile
            }

            return chooseApplicableFile(files, location)
        }

        return null
    }

    private fun chooseApplicableFile(files: List<KtFile>, location: Location): KtFile {
        return if (Registry.`is`("kotlin.debugger.analysis.api.file.applicability.checker")) {
            FileApplicabilityChecker.chooseMostApplicableFile(files, location)
        } else {
            KotlinDebuggerLegacyFacade.getInstance()?.fileSelector?.chooseMostApplicableFile(files, location)
                ?: FileApplicabilityChecker.chooseMostApplicableFile(files, location)
        }
    }

    private fun findFilesByNameInPackage(
        className: JvmClassName,
        fileName: String,
        project: Project,
        searchScope: GlobalSearchScope
    ): List<KtFile> {
        val files = findFilesWithExactPackage(className.packageFqName, searchScope, project).filter { it.name == fileName }
        return files.sortedWith(JavaElementFinder.byClasspathComparator(searchScope))
    }

    fun isKotlinSourceFile(fileName: String): Boolean {
        val extension = FileUtilRt.getExtension(fileName).lowercase(Locale.getDefault())
        return extension in KOTLIN_FILE_EXTENSIONS
    }

    fun String.trimIfMangledInBytecode(isMangledInBytecode: Boolean): String =
        if (isMangledInBytecode)
            getMethodNameWithoutMangling()
        else
            this

    private fun String.getMethodNameWithoutMangling() =
        substringBefore('-')

    fun String.isGeneratedIrBackendLambdaMethodName() =
        matches(IR_BACKEND_LAMBDA_REGEX)

    fun LocalVariable.getBorders(): ClosedRange<Location>? {
        val range = getLocalVariableBorders(this) ?: return null
        return range.from..range.to
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

        val file = findSourceFileForClassIncludeLibrarySources(project, sourceSearchScope, jvmClassName, destinationFileName)
            ?: return listOf()

        val virtualFile = file.virtualFile ?: return listOf()

        val sourceMap = KotlinSourceMapCache.getInstance(project).getSourceMap(virtualFile, jvmClassName) ?: return listOf()

        val mappingsToInlinedFile = sourceMap.fileMappings.filter { it.name == inlineFileName }
        val mappingIntervals = mappingsToInlinedFile.flatMap { it.lineMappings }

        return mappingIntervals.asSequence().filter { rangeMapping -> rangeMapping.hasMappingForSource(inlineLineNumber) }
            .map { rangeMapping -> rangeMapping.mapSourceToDest(inlineLineNumber) }.filter { line -> line != -1 }.toList()
    }
}
