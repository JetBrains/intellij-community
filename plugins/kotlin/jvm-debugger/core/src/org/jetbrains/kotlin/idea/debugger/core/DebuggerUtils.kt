// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.PositionManagerImpl
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.indexing.DumbModeAccessType
import com.intellij.util.indexing.FileBasedIndex
import com.jetbrains.jdi.LocalVariableImpl
import com.sun.jdi.LocalVariable
import com.sun.jdi.Location
import com.sun.jdi.ReferenceType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.asJava.finder.JavaElementFinder
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.indices.KotlinPackageIndexUtils.findFilesWithExactPackage
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.base.psi.getLineStartOffset
import org.jetbrains.kotlin.idea.base.util.KOTLIN_FILE_EXTENSIONS
import org.jetbrains.kotlin.idea.debugger.base.util.ClassNameCalculator
import org.jetbrains.kotlin.idea.debugger.base.util.FileApplicabilityChecker
import org.jetbrains.kotlin.idea.debugger.base.util.KotlinSourceMapCache
import org.jetbrains.kotlin.idea.debugger.base.util.runDumbAnalyze
import org.jetbrains.kotlin.idea.debugger.base.util.fqnToInternalName
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
    ): KtFile? = runBlockingMaybeCancellable {
        findSourceFileForClass(
            project,
            listOf(scope, KotlinSourceFilterScope.librarySources(GlobalSearchScope.allScope(project), project)),
            className,
            fileName,
            location = null
        )
    }

    internal suspend fun findSourceFileForClass(
        project: Project,
        scopes: List<GlobalSearchScope>,
        className: JvmClassName,
        fileName: String,
        location: Location?
    ): KtFile? {
        val files = readAction {
            findSourceFilesForClass(
                project, scopes, className, fileName,
                hasLocation = location != null, classNameResolvesInline = false
            )
        }
        return chooseApplicableFile(files, location)
    }

    internal fun findSourceFilesForClass(
        project: Project,
        scopes: List<GlobalSearchScope>,
        className: JvmClassName,
        fileName: String,
        hasLocation: Boolean = true,
        classNameResolvesInline: Boolean = true,
    ): List<KtFile> {
        if (!isKotlinSourceFile(fileName)) return emptyList()
        if (DumbService.isDumb(project)
            && FileBasedIndex.getInstance().currentDumbModeAccessType != DumbModeAccessType.RELIABLE_DATA_ONLY) return emptyList()

        val partFqName = className.fqNameForClassNameWithoutDollars

        for (scope in scopes) {
            val files = findFilesByNameInPackage(className, fileName, project, scope)
                .filter { it.platform.isJvm() || it.platform.isCommon() }
                .run { if (classNameResolvesInline) filter { isApplicable(it, className) } else this }

            if (files.isEmpty()) {
                continue
            }

            if (!hasLocation || files.size == 1 && !forceRanking) {
                return listOf(files.first())
            }

            val singleFile = runReadAction {
                val matchingFiles = KotlinFileFacadeFqNameIndex[partFqName.asString(), project, scope]
                PackagePartClassUtils.getFilesWithCallables(matchingFiles).singleOrNull { it.name == fileName }
            }

            if (singleFile != null) {
                return listOf(singleFile)
            }

            return files
        }

        return emptyList()
    }

    internal suspend fun chooseApplicableFile(files: List<KtFile>, location: Location?): KtFile? {
        if (files.isEmpty()) return null
        if (location == null || files.size == 1 && !forceRanking) return files.first()
        DebuggerManagerThreadImpl.assertIsManagerThread()
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

    internal fun tryFindFileByClassNameAndFileName(
        project: Project,
        className: JvmClassName,
        sourceName: String,
        scopes: List<GlobalSearchScope>,
    ): List<KtFile> {
        val fqn = className.fqNameForClassNameWithoutDollars.asString()
        for (scope in scopes) {
            val psiClass = PositionManagerImpl.findClass(project, fqn, scope, false) ?: continue
            val file = psiClass.containingFile as? KtFile ?: continue
            if (file.name == sourceName) {
                return listOf(file)
            }
        }
        for (scope in scopes) {
            val files = FilenameIndex.getFilesByName(project, sourceName, scope)
            if (files.isEmpty()) continue
            val collectedFiles = mutableListOf<KtFile>()
            for (file in files) {
                if (file !is KtFile) continue
                if (isApplicable(file, className)) {
                    collectedFiles.add(file)
                }
            }
            if (collectedFiles.isNotEmpty()) return collectedFiles
        }
        return emptyList()
    }

    private fun isApplicable(file: KtFile, className: JvmClassName): Boolean {
        val classNames = ClassNameCalculator.getClassNames(file).values.map { it.fqnToInternalName() }
        return className.internalName.isInnerClassOfAny(classNames)
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
        val localVariableImpl = this as? LocalVariableImpl ?: return null
        return localVariableImpl.scopeStart..localVariableImpl.scopeEnd
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

        return runDumbAnalyze(callExpression, fallback = false) f@ {
            val call = callExpression.resolveToCall()?.successfulFunctionCallOrNull() ?: return@f false
            val parameter = call.argumentMapping[argumentExpression]?.symbol ?: return@f false
            return@f parameter.isCrossinline
        }
    }

    private fun inlinedLinesNumbers(
        inlineLineNumber: Int, inlineFileName: String,
        destinationTypeFqName: FqName, destinationFileName: String,
        project: Project, sourceSearchScope: GlobalSearchScope
    ): List<Int> {
        val internalName = destinationTypeFqName.asString().fqnToInternalName()
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

internal fun String.isInnerClassOfAny(candidateContainingInternalNames: List<String>) =
    candidateContainingInternalNames.any { containingClass -> isInnerClassOf(containingClass) }

private fun String.isInnerClassOf(containingClassInternalName: String): Boolean {
    if (length < containingClassInternalName.length) return false
    if (!startsWith(containingClassInternalName)) return false
    val isSameClass = length == containingClassInternalName.length
    if (isSameClass) return true
    val isInnerClass = this[containingClassInternalName.length] == '$'
    return isInnerClass
}
