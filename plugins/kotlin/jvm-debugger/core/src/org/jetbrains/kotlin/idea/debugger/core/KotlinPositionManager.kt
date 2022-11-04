// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// The package directive doesn't match the file location to prevent API breakage
package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.MultiRequestPositionManager
import com.intellij.debugger.NoDataException
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebuggerUtils.isSynthetic
import com.intellij.debugger.engine.PositionManagerWithMultipleStackFrames
import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.compiled.ClsFileImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentOfType
import com.intellij.util.ThreeState
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.xdebugger.frame.XStackFrame
import com.jetbrains.jdi.LocalVariableImpl
import com.sun.jdi.*
import com.sun.jdi.request.ClassPrepareRequest
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KtUsualClassType
import org.jetbrains.kotlin.analysis.decompiler.psi.file.KtClsFile
import org.jetbrains.kotlin.codegen.inline.KOTLIN_STRATA_NAME
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.psi.getEndLineOffset
import org.jetbrains.kotlin.idea.base.psi.getLineStartOffset
import org.jetbrains.kotlin.idea.base.psi.getStartLineOffset
import org.jetbrains.kotlin.idea.base.util.KOTLIN_FILE_TYPES
import org.jetbrains.kotlin.idea.core.syncNonBlockingReadAction
import org.jetbrains.kotlin.idea.debugger.base.util.*
import org.jetbrains.kotlin.idea.debugger.core.*
import org.jetbrains.kotlin.idea.debugger.core.AnalysisApiBasedInlineUtil.getResolvedFunctionCall
import org.jetbrains.kotlin.idea.debugger.core.AnalysisApiBasedInlineUtil.getValueArgumentForExpression
import org.jetbrains.kotlin.idea.debugger.core.AnalysisApiBasedInlineUtil.isInlinedArgument
import org.jetbrains.kotlin.idea.debugger.core.DebuggerUtils.getBorders
import org.jetbrains.kotlin.idea.debugger.core.DebuggerUtils.isGeneratedIrBackendLambdaMethodName
import org.jetbrains.kotlin.idea.debugger.core.breakpoints.SourcePositionRefiner
import org.jetbrains.kotlin.idea.debugger.core.breakpoints.getElementsAtLineIfAny
import org.jetbrains.kotlin.idea.debugger.core.breakpoints.getLambdasAtLineIfAny
import org.jetbrains.kotlin.idea.debugger.core.stackFrame.InlineStackTraceCalculator
import org.jetbrains.kotlin.idea.debugger.core.stackFrame.KotlinStackFrame
import org.jetbrains.kotlin.idea.debugger.core.stepping.getLineRange
import com.intellij.openapi.application.runReadAction
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.load.java.JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_ARGUMENT
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.jvm.JvmClassName

class KotlinPositionManager(private val debugProcess: DebugProcess) : MultiRequestPositionManager, PositionManagerWithMultipleStackFrames {
    private val stackFrameInterceptor: StackFrameInterceptor? = debugProcess.project.serviceOrNull()

    private val sourceSearchScopes: List<GlobalSearchScope> = listOf(
        debugProcess.searchScope,
        KotlinAllFilesScopeProvider.getInstance(debugProcess.project).getAllKotlinFilesScope()
    )

    override fun getAcceptedFileTypes(): Set<FileType> = KOTLIN_FILE_TYPES

    override fun evaluateCondition(
        context: EvaluationContext,
        frame: StackFrameProxyImpl,
        location: Location,
        expression: String
    ): ThreeState {
        return ThreeState.UNSURE
    }

    override fun createStackFrames(frameProxy: StackFrameProxyImpl, debugProcess: DebugProcessImpl, location: Location): List<XStackFrame> {
        if (!location.isInKotlinSources()) {
            return emptyList()
        }

        // Don't provide inline stack trace for coroutine frames yet
        val coroutineFrame = stackFrameInterceptor?.createStackFrame(frameProxy, debugProcess, location)
        if (coroutineFrame != null) {
            return listOf(coroutineFrame)
        }

        if (Registry.get("debugger.kotlin.inline.stack.trace.enabled").asBoolean()) {
            val inlineStackTrace = InlineStackTraceCalculator.calculateInlineStackTrace(frameProxy)
            if (inlineStackTrace.isNotEmpty()) {
                return inlineStackTrace
            }
        }

        val visibleVariables = InlineStackTraceCalculator.calculateVisibleVariables(frameProxy)
        return listOf(KotlinStackFrame(frameProxy, visibleVariables))
    }

    override fun getSourcePosition(location: Location?): SourcePosition? {
        if (location == null) throw NoDataException.INSTANCE

        val fileName = location.safeSourceName() ?: throw NoDataException.INSTANCE
        val lineNumber = location.safeLineNumber()
        if (lineNumber < 0) {
            throw NoDataException.INSTANCE
        }

        if (!DebuggerUtils.isKotlinSourceFile(fileName)) throw NoDataException.INSTANCE

        val psiFile = getAlternativeSource(location) ?: getPsiFileByLocation(location)

        if (psiFile == null) {
            val isKotlinStrataAvailable = location.declaringType().containsKotlinStrata()
            if (isKotlinStrataAvailable) {
                try {
                    val javaSourceFileName = location.sourceName("Java")
                    val javaClassName = JvmClassName.byInternalName(defaultInternalName(location))
                    val project = debugProcess.project

                    val defaultPsiFile = DebuggerUtils.findSourceFileForClass(
                        project, sourceSearchScopes, javaClassName, javaSourceFileName, location
                    )

                    if (defaultPsiFile != null) {
                        return SourcePosition.createFromLine(defaultPsiFile, 0)
                    }
                } catch (e: AbsentInformationException) {
                    // ignored
                }
            }

            throw NoDataException.INSTANCE
        }

        if (psiFile !is KtFile) throw NoDataException.INSTANCE

        // Zero-based line-number for Document.getLineStartOffset()
        val sourceLineNumber = location.safeLineNumber() - 1
        if (sourceLineNumber < 0) {
            throw NoDataException.INSTANCE
        }

        val lambdaOrFunIfInside = getLambdaOrFunIfInside(location, psiFile, sourceLineNumber)
        if (lambdaOrFunIfInside != null) {
            return SourcePosition.createFromElement(lambdaOrFunIfInside.bodyExpression!!)
        }

        val callableReferenceIfInside = getCallableReferenceIfInside(location, psiFile, sourceLineNumber)
        if (callableReferenceIfInside != null) {
            val sourcePosition = SourcePosition.createFromElement(callableReferenceIfInside)
            if (sourcePosition != null) {
                // Never stop on invocation of method reference
                return KotlinReentrantSourcePosition(sourcePosition)
            }
        }

        val elementInDeclaration = getElementForDeclarationLine(location, psiFile, sourceLineNumber)
        if (elementInDeclaration != null) {
            return SourcePosition.createFromElement(elementInDeclaration)
        }

        // There may be several locations for same source line. If same source position would be created for all of them,
        // breakpoints at this line will stop on every location.
        if (location.shouldBeTreatedAsReentrantSourcePosition(psiFile, fileName)) {
            return KotlinReentrantSourcePosition(SourcePosition.createFromLine(psiFile, sourceLineNumber))
        }
        val sourcePosition = SourcePosition.createFromLine(psiFile, sourceLineNumber)
        return decorateSourcePosition(location, sourcePosition)
    }

    private fun Location.shouldBeTreatedAsReentrantSourcePosition(psiFile: PsiFile, sourceFileName: String): Boolean {
        val method = safeMethod() ?: return false
        val sameLineLocations = method
            .safeAllLineLocations()
            .filter {
                it.safeSourceName() == sourceFileName &&
                it.lineNumber() == lineNumber()
            }

        /*
            `finally {}` block code is placed in the class file twice.
            Unless the debugger metadata is available, we can't figure out if we are inside `finally {}`, so we have to check it using PSI.
            This is conceptually wrong and won't work in some cases, but it's still better than nothing.
        */
        if (sameLineLocations.size < 2 || hasFinallyBlockInParent(psiFile)) {
            return false
        }
        val locationsInSameInlinedFunction = findLocationsInSameInlinedFunction(sameLineLocations, method, sourceFileName)
        return locationsInSameInlinedFunction.ifEmpty { sameLineLocations }.indexOf(this) > 0
    }

    private fun Location.hasFinallyBlockInParent(psiFile: PsiFile): Boolean {
        val elementAt = psiFile.getLineStartOffset(lineNumber())?.let { psiFile.findElementAt(it) }
        return elementAt?.parentOfType<KtFinallySection>() != null
    }

    private fun Location.findLocationsInSameInlinedFunction(locations: List<Location>, method: Method, sourceFileName: String): List<Location> {
        val leastEnclosingBorders = method
            .getInlineFunctionBorders(sourceFileName)
            .getLeastEnclosingBorders(this)
            ?: return emptyList()
        return locations.filter { leastEnclosingBorders.contains(it) }
    }

    private fun List<ClosedRange<Location>>.getLeastEnclosingBorders(location: Location): ClosedRange<Location>? {
        var result: ClosedRange<Location>? = null
        for (range in this) {
            if (location in range && (result == null || range.start > result.start)) {
                result = range
            }
        }
        return result
    }

    private fun Method.getInlineFunctionBorders(sourceFileName: String): List<ClosedRange<Location>> {
        return getInlineFunctionOrArgumentVariables()
            .mapNotNull { it.getBorders() }
            .filter { it.start.safeSourceName() == sourceFileName }
            .toList()
    }

    private fun getAlternativeSource(location: Location): PsiFile? {
        val manager = PsiManager.getInstance(debugProcess.project)
        val qName = location.declaringType().name()
        val alternativeFileUrl = DebuggerUtilsEx.getAlternativeSourceUrl(qName, debugProcess.project) ?: return null
        val alternativePsiFile = VirtualFileManager.getInstance().findFileByUrl(alternativeFileUrl) ?: return null
        return manager.findFile(alternativePsiFile)
    }

    // Returns a property or a constructor if debugger stops at class declaration
    private fun getElementForDeclarationLine(location: Location, file: KtFile, lineNumber: Int): KtElement? {
        val lineStartOffset = file.getLineStartOffset(lineNumber) ?: return null
        val elementAt = file.findElementAt(lineStartOffset)
        val contextElement = getContextElement(elementAt)

        if (contextElement !is KtClass) return null

        val methodName = location.method().name()
        return when {
            JvmAbi.isGetterName(methodName) -> {
                val valueParameters = contextElement.primaryConstructor?.valueParameters ?: emptyList()
                valueParameters.find { it.hasValOrVar() && it.name != null && JvmAbi.getterName(it.name!!) == methodName }
            }
            methodName == "<init>" -> contextElement.primaryConstructor
            else -> null
        }
    }

    private fun getCallableReferenceIfInside(location: Location, file: KtFile, lineNumber: Int): KtCallableReferenceExpression? {
        val currentLocationClassName = location.getClassName() ?: return null
        val allReferenceExpressions = getElementsAtLineIfAny<KtCallableReferenceExpression>(file, lineNumber)

        return allReferenceExpressions.firstOrNull {
            it.calculatedClassNameMatches(currentLocationClassName)
        }
    }

    private fun getLambdaOrFunIfInside(location: Location, file: KtFile, lineNumber: Int): KtFunction? {
        val currentLocationClassName = location.getClassName() ?: return null

        val start = getStartLineOffset(file, lineNumber)
        val end = getEndLineOffset(file, lineNumber)
        if (start == null || end == null) return null

        val literalsOrFunctions = getLambdasAtLineIfAny(file, lineNumber)
        if (literalsOrFunctions.isEmpty()) return null

        return literalsOrFunctions.getAppropriateLiteralBasedOnDeclaringClassName(location, currentLocationClassName) ?:
               literalsOrFunctions.getAppropriateLiteralBasedOnLambdaName(location, lineNumber)
    }

    private fun List<KtFunction>.getAppropriateLiteralBasedOnDeclaringClassName(
        location: Location,
        currentLocationClassName: String
    ): KtFunction? {
        val firstLiteral = firstOrNull() ?: return null
        analyze(firstLiteral) {
            forEach { literal ->
                if (isInlinedArgument(literal, true)) {
                    if (isInsideInlineArgument(literal, location, debugProcess as DebugProcessImpl)) {
                        return literal
                    }
                } else if (literal.firstChild.calculatedClassNameMatches(currentLocationClassName)) {
                    return literal
                }
            }
        }

        return null
    }

    private fun PsiElement.calculatedClassNameMatches(currentLocationClassName: String): Boolean {
        val classNameProvider = ClassNameProvider(
            debugProcess.project,
            debugProcess.searchScope,
            ClassNameProvider.Configuration.DEFAULT.copy(alwaysReturnLambdaParentClass = false)
        )

        return classNameProvider.getCandidatesForElement(this).any { it == currentLocationClassName }
    }

    private fun List<KtFunction>.getAppropriateLiteralBasedOnLambdaName(location: Location, lineNumber: Int): KtFunction? {
        val method = location.safeMethod() ?: return null
        if (!method.name().isGeneratedIrBackendLambdaMethodName()) {
            return null
        }

        val lambdas = location.declaringType().methods()
            .filter {
              it.name().isGeneratedIrBackendLambdaMethodName() &&
              DebuggerUtilsEx.locationsOfLine(it, lineNumber + 1).isNotEmpty()
            }

        return getSamLambdaWithIndex(lambdas.indexOf(method))
    }

    private fun List<KtFunction>.getSamLambdaWithIndex(index: Int): KtFunction? {
        var samLambdaCounter = 0
        for (literal in this) {
            if (literal.isSamLambda()) {
                if (samLambdaCounter == index) {
                    return literal
                }
                samLambdaCounter++
            }
        }
        return null
    }

    private fun getPsiFileByLocation(location: Location): PsiFile? {
        val sourceName = location.safeSourceName() ?: return null

        val referenceInternalName = try {
            if (location.declaringType().containsKotlinStrata()) {
                //replace is required for windows
                location.sourcePath().replace('\\', '/')
            } else {
                defaultInternalName(location)
            }
        } catch (e: AbsentInformationException) {
            defaultInternalName(location)
        }

        val className = JvmClassName.byInternalName(referenceInternalName)

        val project = debugProcess.project

        return DebuggerUtils.findSourceFileForClass(project, sourceSearchScopes, className, sourceName, location)
    }

    private fun defaultInternalName(location: Location): String {
        //no stratum or source path => use default one
        val referenceFqName = location.declaringType().name()
        // JDI names are of form "package.Class$InnerClass"
        return referenceFqName.replace('.', '/')
    }

    override fun getAllClasses(sourcePosition: SourcePosition): List<ReferenceType> {
        val psiFile = sourcePosition.file
        if (psiFile is KtFile) {
            if (!RootKindFilter.projectAndLibrarySources.matches(psiFile)) return emptyList()

            val referenceTypesInKtFile = syncNonBlockingReadAction(psiFile.project) {
                getReferenceTypesForPositionInKotlinFile(sourcePosition)
            }

            if (sourcePosition.isInsideProjectWithCompose()) {
                return referenceTypesInKtFile + getComposableSingletonsClasses(debugProcess, psiFile)
            }

            return referenceTypesInKtFile
        }

        if (psiFile is ClsFileImpl) {
            val decompiledPsiFile = runReadAction { psiFile.decompiledPsiFile }
            if (decompiledPsiFile is KtClsFile && runReadAction { sourcePosition.line } == -1) {
                val className = JvmFileClassUtil.getFileClassInternalName(decompiledPsiFile)
                return debugProcess.virtualMachineProxy.classesByName(className)
            }
        }

        throw NoDataException.INSTANCE
    }

    @RequiresReadLock
    private fun getReferenceTypesForPositionInKotlinFile(sourcePosition: SourcePosition): List<ReferenceType> {
        val classNameProvider = ClassNameProvider(debugProcess.project, debugProcess.searchScope, ClassNameProvider.Configuration.DEFAULT)
        val lineNumber = sourcePosition.line

        try {
            val allCandidates = classNameProvider.getCandidates(sourcePosition)
                .flatMap { className -> debugProcess.virtualMachineProxy.classesByName(className) }

            val matchingCandidates = allCandidates
                .flatMap { referenceType -> debugProcess.findTargetClasses(referenceType, lineNumber) }

            return matchingCandidates.ifEmpty { allCandidates }
        } catch (e: IncompatibleThreadStateException) {
            return emptyList()
        } catch (e: VMDisconnectedException) {
            return emptyList()
        }
    }

    @Deprecated("Use 'ClassNameProvider' directly")
    fun originalClassNamesForPosition(position: SourcePosition): List<String> {
        return runReadAction {
            val classNameProvider = ClassNameProvider(
                debugProcess.project,
                debugProcess.searchScope,
                ClassNameProvider.Configuration.DEFAULT.copy(findInlineUseSites = false)
            )
            classNameProvider.getCandidates(position)
        }
    }

    override fun locationsOfLine(type: ReferenceType, position: SourcePosition): List<Location> {
        if (position.file !is KtFile) {
            throw NoDataException.INSTANCE
        }
        try {
            if (DexDebugFacility.isDex(debugProcess)) {
                val inlineLocations = runReadAction { DebuggerUtils.getLocationsOfInlinedLine(type, position, debugProcess.searchScope) }
                if (inlineLocations.isNotEmpty()) {
                    return inlineLocations
                }
            }

            val line = position.line + 1

            val locations = DebuggerUtilsAsync.locationsOfLineSync(type, KOTLIN_STRATA_NAME, null, line)
            if (locations.isNullOrEmpty()) {
                throw NoDataException.INSTANCE
            }

            return locations.filter { it.sourceName(KOTLIN_STRATA_NAME) == position.file.name }
        } catch (e: AbsentInformationException) {
            throw NoDataException.INSTANCE
        }
    }

    @Deprecated(
        "Since Idea 14.0.3 use createPrepareRequests fun",
        ReplaceWith("createPrepareRequests(classPrepareRequestor, sourcePosition).firstOrNull()")
    )
    override fun createPrepareRequest(classPrepareRequestor: ClassPrepareRequestor, sourcePosition: SourcePosition): ClassPrepareRequest? {
        return createPrepareRequests(classPrepareRequestor, sourcePosition).firstOrNull()
    }

    override fun createPrepareRequests(requestor: ClassPrepareRequestor, position: SourcePosition): List<ClassPrepareRequest> {
        val file = position.file
        if (file !is KtFile) {
            throw NoDataException.INSTANCE
        }

        val isInsideProjectWithCompose = position.isInsideProjectWithCompose()
        return DumbService.getInstance(debugProcess.project).runReadActionInSmartMode(Computable {
            val kotlinRequests = createKotlinClassPrepareRequests(requestor, position)
            if (isInsideProjectWithCompose) {
                val singletonRequest = getClassPrepareRequestForComposableSingletons(debugProcess, requestor, file)
                if (singletonRequest == null)
                    kotlinRequests
                else
                    kotlinRequests + singletonRequest
            } else {
                kotlinRequests
            }
        })
    }

    @RequiresReadLock
    private fun createKotlinClassPrepareRequests(requestor: ClassPrepareRequestor, position: SourcePosition): List<ClassPrepareRequest> {
        val refinedPosition = when (requestor) {
            is SourcePositionRefiner -> requestor.refineSourcePosition(position)
            else -> position
        }

        return ClassNameProvider(debugProcess.project, debugProcess.searchScope, ClassNameProvider.Configuration.DEFAULT)
            .getCandidates(refinedPosition)
            .flatMap { name ->
                listOfNotNull(
                    debugProcess.requestsManager.createClassPrepareRequest(requestor, name),
                    debugProcess.requestsManager.createClassPrepareRequest(requestor, "$name$*")
                )
            }
    }
}

// This method detects whether we should highlight the entire line of a source position or not.
// Consider the example:
//
//    1.also { // Stop on a breakpoint here and perform a step over
//        println(it)
//    }.also { // You will get to this line
//        println(it)
//    }
//
// In this example the entire line with `also` should be highlighted.
// Another example:
//
//    1.also {
//        println(it) // Stop on a breakpoint here and perform a step over
//    }.also { // You will get to this line
//        println(it)
//    }
//
// Now we should highlight the line before the curly brace, since we are still inside the `also` inline lambda.
private fun decorateSourcePosition(location: Location, sourcePosition: SourcePosition): SourcePosition {
    val lambda = sourcePosition.elementAt?.parent as? KtFunctionLiteral ?: return sourcePosition
    val lines = lambda.getLineRange() ?: return sourcePosition
    if (!location.hasVisibleInlineLambdasOnLines(lines)) {
        return KotlinSourcePositionWithEntireLineHighlighted(sourcePosition)
    }
    return sourcePosition
}

private fun Location.getZeroBasedLineNumber(): Int =
    DebuggerUtilsEx.getLineNumber(this, true)

private fun Location.hasVisibleInlineLambdasOnLines(lines: IntRange): Boolean {
    val method = safeMethod() ?: return false
    return method.getInlineFunctionAndArgumentVariablesToBordersMap()
        .asSequence()
        .filter { (variable, _) ->
            variable is LocalVariableImpl &&
            variable.isVisible(this) &&
            variable.name().startsWith(LOCAL_VARIABLE_NAME_PREFIX_INLINE_ARGUMENT)
        }
        .any { (_, borders) ->
            borders.start.getZeroBasedLineNumber() in lines &&
            borders.endInclusive.getZeroBasedLineNumber() in lines
        }
}

// Copied from com.jetbrains.jdi.LocalVariableImpl.isVisible
private fun LocalVariableImpl.isVisible(location: Location): Boolean =
    scopeStart <= location && scopeEnd >= location

fun Location.getClassName(): String? {
    val currentLocationFqName = declaringType().name() ?: return null
    return JvmClassName.byFqNameWithoutInnerClasses(FqName(currentLocationFqName)).internalName.replace('/', '.')
}

private fun DebugProcess.findTargetClasses(outerClass: ReferenceType, lineAt: Int): List<ReferenceType> {
    val vmProxy = virtualMachineProxy

    try {
        if (!outerClass.isPrepared) {
            return emptyList()
        }
    } catch (e: ObjectCollectedException) {
        return emptyList()
    }

    val targetClasses = ArrayList<ReferenceType>(1)

    try {
        for (location in outerClass.safeAllLineLocations()) {
            val locationLine = location.lineNumber() - 1
            if (locationLine < 0) {
                // such locations do not correspond to real lines in code
                continue
            }

            if (lineAt == locationLine) {
                val method = location.method()
                if (method == null || isSynthetic(method) || method.isBridge) {
                    // skip synthetic methods
                    continue
                }

                targetClasses += outerClass
                break
            }
        }

        // The same line number may appear in different classes, so we have to scan nested classes as well.
        // In the next example line 3 appears in both Foo and Foo$Companion.
        //     class Foo {
        //         companion object {
        //             val a = Foo() /* line 3 */
        //          }
        //     }
        val nestedTypes = vmProxy.nestedTypes(outerClass)
        for (nested in nestedTypes) {
            targetClasses += findTargetClasses(nested, lineAt)
        }
    } catch (_: AbsentInformationException) {
    }

    return targetClasses
}

private fun KtFunction.isSamLambda(): Boolean {
    if (this !is KtFunctionLiteral && this !is KtNamedFunction) return false
    return analyze(this) {
        val parentCall = KtPsiUtil.getParentCallIfPresent(this@isSamLambda) as? KtCallExpression ?: return false
        val call = getResolvedFunctionCall(parentCall) ?: return false
        val valueArgument = parentCall.getValueArgumentForExpression(this@isSamLambda) ?: return false
        val argument = call.argumentMapping[valueArgument.getArgumentExpression()]?.symbol ?: return false

        return argument.returnType is KtUsualClassType
    }
}
