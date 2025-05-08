// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// The package directive doesn't match the file location to prevent API breakage
package org.jetbrains.kotlin.idea.debugger

import com.intellij.concurrency.ContextAwareRunnable
import com.intellij.debugger.MultiRequestPositionManager
import com.intellij.debugger.NoDataException
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.*
import com.intellij.debugger.engine.DebuggerUtils.isSynthetic
import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.impl.DexDebugFacility
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.intellij.debugger.ui.breakpoints.Breakpoint
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.withProgressText
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.compiled.ClsFileImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.descendantsOfType
import com.intellij.psi.util.parentOfType
import com.intellij.util.ThreeState
import com.intellij.util.asSafely
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import com.intellij.util.indexing.DumbModeAccessType
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.jetbrains.jdi.ReferenceTypeImpl
import com.sun.jdi.*
import com.sun.jdi.request.ClassPrepareRequest
import kotlinx.coroutines.*
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaUsualClassType
import org.jetbrains.kotlin.analysis.decompiler.psi.file.KtClsFile
import org.jetbrains.kotlin.codegen.inline.KOTLIN_STRATA_NAME
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.psi.*
import org.jetbrains.kotlin.idea.base.util.KOTLIN_FILE_TYPES
import org.jetbrains.kotlin.idea.codeinsight.utils.getInlineArgumentSymbol
import org.jetbrains.kotlin.idea.core.syncNonBlockingReadAction
import org.jetbrains.kotlin.idea.debugger.base.util.*
import org.jetbrains.kotlin.idea.debugger.core.*
import org.jetbrains.kotlin.idea.debugger.core.DebuggerUtils
import org.jetbrains.kotlin.idea.debugger.core.DebuggerUtils.getBorders
import org.jetbrains.kotlin.idea.debugger.core.DebuggerUtils.isGeneratedIrBackendLambdaMethodName
import org.jetbrains.kotlin.idea.debugger.core.breakpoints.*
import org.jetbrains.kotlin.idea.debugger.core.stackFrame.InlineStackTraceCalculator
import org.jetbrains.kotlin.idea.debugger.core.stackFrame.KotlinStackFrame
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.jvm.JvmClassName
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume

class KotlinPositionManager(private val debugProcess: DebugProcess) : MultiRequestPositionManager, PositionManagerWithMultipleStackFrames,
                                                                      PositionManagerAsync {
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

    override fun createStackFrames(descriptor: StackFrameDescriptorImpl): List<XStackFrame>? {
        DebuggerManagerThreadImpl.assertIsManagerThread()
        if (descriptor.location?.isInKotlinSources() != true) {
            return null
        }
        val frameProxy = descriptor.frameProxy
        // Don't provide inline stack trace for coroutine frames yet
        val coroutineFrames = StackFrameInterceptor.instance?.createStackFrames(frameProxy, descriptor.debugProcess as DebugProcessImpl)
        if (coroutineFrames != null) {
            return coroutineFrames
        }

        if (Registry.get("debugger.kotlin.inline.stack.trace.enabled").asBoolean()) {
            val inlineStackTrace = InlineStackTraceCalculator.calculateInlineStackTrace(descriptor)
            if (inlineStackTrace.isNotEmpty()) {
                return inlineStackTrace
            }
        }

        val visibleVariables = InlineStackTraceCalculator.calculateVisibleVariables(frameProxy)
        return listOf(KotlinStackFrame(descriptor, visibleVariables))
    }

    override suspend fun getSourcePositionAsync(location: Location?): SourcePosition? {
        DebuggerManagerThreadImpl.assertIsManagerThread()
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
                    val javaSourceFileName = location.sourceName(DebugProcess.JAVA_STRATUM)
                    val javaClassName = JvmClassName.byInternalName(defaultInternalName(location))
                    val project = debugProcess.project

                    val defaultPsiFile = DebuggerUtils.findSourceFileForClass(
                        project, sourceSearchScopes, javaClassName, javaSourceFileName, location
                    )

                    if (defaultPsiFile != null) {
                        return readAction { SourcePosition.createFromLine(defaultPsiFile, 0) }
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

        if (location.safeMethod()?.isGeneratedErasedLambdaMethod() == true) {
            return null
        }

        PositionManagerImpl.adjustPositionForConditionalReturn(debugProcess, location, psiFile, sourceLineNumber)?.let {
            return it
        }

        val sourcePosition = createSourcePosition(location, psiFile, sourceLineNumber)
            ?: readAction { SourcePosition.createFromLine(psiFile, sourceLineNumber) }

        // There may be several locations for same source line. If same source position would be created for all of them,
        // breakpoints at this line will stop on every location.
        if (sourcePosition !is KotlinReentrantSourcePosition && location.shouldBeTreatedAsReentrantSourcePosition(psiFile, fileName)) {
            return KotlinReentrantSourcePosition(sourcePosition)
        }

        return sourcePosition
    }

    private suspend fun createSourcePosition(location: Location, file: KtFile, sourceLineNumber: Int): SourcePosition? {
        val lambdaOrFunIfInside = getLambdaOrFunOnLineIfInside(location, file, sourceLineNumber)
        if (lambdaOrFunIfInside != null) {
            return readAction {
                val elementAt = getFirstElementInsideLambdaOnLine(file, lambdaOrFunIfInside, sourceLineNumber)
                elementAt?.let { SourcePosition.createFromElement(it) }
                    ?: SourcePosition.createFromLine(file, sourceLineNumber)
            }
        }

        val callableReferenceIfInside = getCallableReferenceIfInside(location, file, sourceLineNumber)
        if (callableReferenceIfInside != null) {
            val sourcePosition = readAction { SourcePosition.createFromElement(callableReferenceIfInside) }
            if (sourcePosition != null) {
                // Never stop on invocation of method reference
                return KotlinReentrantSourcePosition(sourcePosition)
            }
        }

        val elementInDeclaration = getElementForDeclarationLine(location, file, sourceLineNumber)
        if (elementInDeclaration != null) {
            return readAction { SourcePosition.createFromElement(elementInDeclaration) }
        }
        return null
    }

    private fun getFirstElementInsideLambdaOnLine(file: PsiFile, lambda: KtFunction, line: Int): PsiElement? {
        val lineRange = file.getRangeOfLine(line) ?: return null
        val elementsOnLine = file.findElementsOfTypeInRange<PsiElement>(lineRange)
            .filter { it.startOffset in lineRange && it.parents.contains(lambda) }

        // Prefer elements that are inside body range
        val bodyRange = lambda.bodyExpression!!.textRange
        elementsOnLine.firstOrNull {it.startOffset in bodyRange } ?.let { return it }

        // Prefer KtElements
        elementsOnLine.firstOrNull { it is KtElement } ?.let { return it }

        return elementsOnLine.firstOrNull()
    }

    private suspend fun Location.shouldBeTreatedAsReentrantSourcePosition(psiFile: PsiFile, sourceFileName: String): Boolean {
        val method = safeMethod() ?: return false
        val sameLineLocations = method
            .safeAllLineLocations()
            .filter {
                it.safeSourceName() == sourceFileName &&
                it.lineNumber() == lineNumber()
            }

        //  The `finally {}` block code is placed in the class file twice.
        //  Unless the debugger metadata is available, we can't figure out if we are inside `finally {}`, so we have to check it using PSI.
        //  This is conceptually wrong and won't work in some cases, but it's still better than nothing.
        if (sameLineLocations.size < 2 || hasFinallyBlockInParent(psiFile)) {
            return false
        }
        val locationsInSameInlinedFunction = findLocationsInSameInlinedFunction(sameLineLocations, method)
        return locationsInSameInlinedFunction.ifEmpty { sameLineLocations }.indexOf(this) > 0
    }

    private suspend fun Location.hasFinallyBlockInParent(psiFile: PsiFile): Boolean {
        val line = getZeroBasedLineNumber()
        return readAction {
            val elementAt = psiFile.getLineStartOffset(line)?.let { psiFile.findElementAt(it) }
            elementAt?.parentOfType<KtFinallySection>() != null
        }
    }

    private fun Location.findLocationsInSameInlinedFunction(locations: List<Location>, method: Method): List<Location> {
        val leastEnclosingBorders = method
            .getInlineFunctionBorders()
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

    private fun Method.getInlineFunctionBorders(): List<ClosedRange<Location>> =
        getInlineFunctionOrArgumentVariables().mapNotNull { it.getBorders() }.toList()

    private suspend fun getAlternativeSource(location: Location): PsiFile? {
        val manager = PsiManager.getInstance(debugProcess.project)
        val qName = location.declaringType().name()
        return readAction {
            val alternativeFileUrl = DebuggerUtilsEx.getAlternativeSourceUrl(qName, debugProcess.project) ?: return@readAction null
            val alternativePsiFile = VirtualFileManager.getInstance().findFileByUrl(alternativeFileUrl) ?: return@readAction null
            manager.findFile(alternativePsiFile)
        }
    }

    // Returns a property or a constructor if debugger stops at class declaration
    private suspend fun getElementForDeclarationLine(location: Location, file: KtFile, lineNumber: Int): KtElement? {
        val (locationElement, contextElement) = readAction {
            val lineStartOffset = file.getLineStartOffset(lineNumber) ?: return@readAction null
            val elementAt = file.findElementAt(lineStartOffset)
            elementAt to CodeFragmentContextTuner.getInstance().tuneContextElement(elementAt)
        } ?: return null
        
        if (locationElement == null || contextElement == null) return null

        if (contextElement !is KtClass) return null
        val methodName = location.method().name()

        return readAction {
            when {
                JvmAbi.isGetterName(methodName) -> {
                    val valueParameters = contextElement.primaryConstructor?.valueParameters ?: emptyList()
                    valueParameters.find { it.hasValOrVar() && it.name != null && JvmAbi.getterName(it.name!!) == methodName }
                }

                methodName == "<init>" -> {
                    val locationParent = locationElement.parent
                    if (locationParent is KtParameter && locationElement == locationParent.valOrVarKeyword) {
                        // if location points to the val parameter from the primary constructor,
                        // use this val parameter as a declaration in debugger
                        locationParent
                    } else {
                        contextElement.primaryConstructor
                    }
                }
                else -> null
            }
        }
    }

    private suspend fun getCallableReferenceIfInside(location: Location, file: KtFile, lineNumber: Int): KtCallableReferenceExpression? {
        val currentLocationClassName = location.getClassName() ?: return null
        val allReferenceExpressions = readAction { getElementsAtLineIfAny<KtCallableReferenceExpression>(file, lineNumber) }
        if (allReferenceExpressions.isEmpty()) return null
        val (inlinedReference, notInlined) = allReferenceExpressions.separateInlinedAndNonInlinedElements(location)
        if (inlinedReference != null) return inlinedReference
        return readAction {
            notInlined.firstOrNull {
                it.calculatedClassNameMatches(currentLocationClassName, false)
            }
        }
    }

    private suspend fun getLambdaOrFunOnLineIfInside(location: Location, file: KtFile, lineNumber: Int): KtFunction? {
        val currentLocationClassName = location.getClassName() ?: return null

        val start = readAction { getStartLineOffset(file, lineNumber) }
        val end = readAction { getEndLineOffset(file, lineNumber) }
        if (start == null || end == null) return null

        val literalsOrFunctions = readAction {
            getLambdasAtLine(file, lineNumber)
                // We are not interested in lambdas when we're in the middle of them and no more lambdas on the line
                // because in such case there is only one possible source position on the line.
                .takeIf { lambdas -> lambdas.any { it.isStartingOrEndingOnLine(lineNumber) } }
        } ?: return null

        val (innermostContainingLiteral, notInlinedLambdas) = literalsOrFunctions.separateInlinedAndNonInlinedElements(location)
        if (innermostContainingLiteral != null) return innermostContainingLiteral

        return readAction { notInlinedLambdas.getAppropriateLiteralBasedOnDeclaringClassName(currentLocationClassName) }
            ?: readAction { notInlinedLambdas.getAppropriateLiteralForCrossinlineLambda(currentLocationClassName) }
            ?: notInlinedLambdas.getAppropriateLiteralBasedOnLambdaName(location, lineNumber)

    }

    private suspend fun <T : KtExpression> List<T>.separateInlinedAndNonInlinedElements(location: Location): Pair<T?, List<T>> {
        val notInlined = mutableListOf<T>()
        var innermostInlinedElement: T? = null
        for (expression in this) {
            val isCrossinline = dumbAnalyze(expression, fallback = false) {
                getInlineArgumentSymbol(expression)?.isCrossinline
            }
            if (isCrossinline != null && (!isCrossinline || isInlinedArgument(expression, location))) {
                if (isInsideInlineArgument(expression, location)) {
                    innermostInlinedElement = expression
                }
            } else {
                notInlined.add(expression)
            }
        }
        return innermostInlinedElement to notInlined
    }

    private fun List<KtFunction>.getAppropriateLiteralBasedOnDeclaringClassName(currentLocationClassName: String): KtFunction? {
        return firstOrNull { it.firstChild.calculatedClassNameMatches(currentLocationClassName, true) }
    }

    /**
     * Crossinline lambda generated class name contains $$inlined$<CALL METHOD NAME>$N substring
     * where N is the sequential number of the lambda with the same call method name
     */
    private fun List<KtFunction>.getAppropriateLiteralForCrossinlineLambda(currentLocationClassName: String): KtFunction? {
        if (isEmpty()) return null
        val crossinlineLambdaPrefix = "\$\$inlined\$"
        if (crossinlineLambdaPrefix !in currentLocationClassName) return null
        if (size == 1) return first()

        val fittingCallMethodName = filter {
            val name = it.getLambdaCallMethod()?.getBytecodeMethodName() ?: return@filter false
            "$crossinlineLambdaPrefix$name\$" in currentLocationClassName
        }
        if (fittingCallMethodName.isEmpty()) return null
        if (fittingCallMethodName.size == 1) return fittingCallMethodName.first()

        // Now we try to guess the exact index of crossinline lambda.
        // However, we cannot distinguish crossinline lambdas from usual ones with the same method name,
        // so this works only when a method contains only crossinline lambdas before the target one.
        val callMethodName = fittingCallMethodName.first().getLambdaCallMethod()?.getBytecodeMethodName() ?: return null
        val containingMethod = fittingCallMethodName.first().getContainingMethod() ?: return null

        val allLambdasInMethod = containingMethod
            .descendantsOfType<KtFunction>()
            .filter { it is KtFunctionLiteral || it.name == null }
            .filter { it.getLambdaCallMethod()?.getBytecodeMethodName() == callMethodName }.toList()

        val candidatesBySequenceNumber = mutableListOf<KtFunction>()
        for (call in fittingCallMethodName) {
            val indexInOuterMethod = allLambdasInMethod.indexOf(call)
            val candidateName = "$crossinlineLambdaPrefix$callMethodName\$${indexInOuterMethod + 1}"
            if (candidateName in currentLocationClassName) {
                candidatesBySequenceNumber.add(call)
            }
        }

        return candidatesBySequenceNumber.singleOrNull()
    }

    private fun KtFunction.getLambdaCallMethod(): KtCallExpression? = parentOfType<KtCallExpression>()

    private fun KtCallExpression.getBytecodeMethodName(): String? = runDumbAnalyze(this, fallback = null) f@{
        val resolvedCall = resolveToCall()?.successfulFunctionCallOrNull() ?: return@f null
        val symbol = resolvedCall.partiallyAppliedSymbol.symbol as? KaNamedFunctionSymbol ?: return@f null
        getByteCodeMethodName(symbol)
    }

    private fun PsiElement.calculatedClassNameMatches(currentLocationClassName: String, isLambda: Boolean): Boolean {
        return ClassNameProvider(ClassNameProvider.Configuration.STOP_AT_LAMBDA)
            .getCandidatesForElement(this)
            .run { if (isLambda) filter(::isNestedClassName) else this }
            .any { it == currentLocationClassName }
    }

    private fun isNestedClassName(name: String): Boolean = "\$" in name

    private suspend fun List<KtFunction>.getAppropriateLiteralBasedOnLambdaName(location: Location, lineNumber: Int): KtFunction? {
        val method = location.safeMethod() ?: return null
        if (!method.name().isGeneratedIrBackendLambdaMethodName()) {
            return null
        }

        val lambdas = location.declaringType().methods()
            .filter {
              it.name().isGeneratedIrBackendLambdaMethodName() &&
              !it.isGeneratedErasedLambdaMethod() &&
              DebuggerUtilsEx.locationsOfLine(it, lineNumber + 1).isNotEmpty()
            }
            // Kotlin indy lambdas can come in wrong order, sort by order and hierarchy
            .sortedBy { IrLambdaDescriptor(it.name()) }

        if (lambdas.isEmpty()) {
            return null
        }

        // To bring the list of fun literals into conformity with list of lambda-methods in bytecode above
        // it is needed to filter out literals without executable code on current line.
        val suitableFunLiterals = filter { it.hasExecutableCodeInsideOnLine(lineNumber) }

        val methodIdx = lambdas.indexOf(method)
        if (lambdas.size == suitableFunLiterals.size) {
            // All lambdas on the line compiled into methods
            return suitableFunLiterals[methodIdx]
        }
        // SAM lambdas compiled into methods, and other non-SAM lambdas on same line compiled into anonymous classes
        return suitableFunLiterals.getSamLambdaWithIndex(methodIdx)
    }

    private suspend fun KtFunction.hasExecutableCodeInsideOnLine(lineNumber: Int): Boolean {
        val file = readAction { containingFile.virtualFile }
        return hasExecutableCodeInsideOnLine(file, lineNumber, debugProcess.project) { element ->
            when (element) {
                is KtNamedFunction -> ApplicabilityResult.UNKNOWN
                is KtElement -> {
                    val visitor = LineBreakpointExpressionVisitor.of(file, lineNumber)
                    if (visitor != null) {
                        element.accept(visitor, null)
                    } else {
                        ApplicabilityResult.UNKNOWN
                    }
                }
                else -> ApplicabilityResult.UNKNOWN
            }
        } || hasImplicitReturnOnLine(this, lineNumber)
    }

    private suspend fun hasImplicitReturnOnLine(function: KtFunction, lineNumber: Int): Boolean {
        if (function !is KtFunctionLiteral || readAction { function.getLineNumber(start = false) } != lineNumber) {
            return false
        }
        val isUnitReturnType = runDumbAnalyze(function, fallback = false) {
            val functionalType = function.functionType
            (functionalType as? KaFunctionType)?.returnType?.isUnitType == true
        }
        if (!isUnitReturnType) {
            // We always must specify return explicitly
            return false
        }
        // This check does not cover some more complex cases (e.g. "if" or "when" block expressions)
        return readAction { function.lastStatementSkippingComments() } !is KtReturnExpression
    }

    private fun KtFunction.lastStatementSkippingComments(): KtElement? {
        return bodyBlockExpression?.childrenOfType<KtElement>()?.lastOrNull()
    }

    private suspend fun List<KtFunction>.getSamLambdaWithIndex(index: Int): KtFunction? {
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

    private suspend fun getPsiFileByLocation(location: Location): PsiFile? {
        val sourceName = location.safeSourceName() ?: return null
        if (!DebuggerUtils.isKotlinSourceFile(sourceName)) return null

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
        val files = findFileCandidatesWithBackgroundProcess(project, className, sourceName, sourceSearchScopes)
        return DebuggerUtils.chooseApplicableFile(files, location)
    }

    private fun defaultInternalName(location: Location): String {
        //no stratum or source path => use default one
        val referenceFqName = location.declaringType().name()
        // JDI names are of form "package.Class$InnerClass"
        return referenceFqName.fqnToInternalName()
    }

    override fun getAllClasses(sourcePosition: SourcePosition): List<ReferenceType> {
        val psiFile = sourcePosition.file
        if (psiFile is KtFile) {

            val candidates = syncNonBlockingReadAction(psiFile.project) {
                if (!RootKindFilter.projectAndLibrarySources.matches(psiFile)) return@syncNonBlockingReadAction null
                getCandidates(sourcePosition)
            } ?: return emptyList()
            val (classes, classesWithInlinedCode) = candidates.getReferenceTypesCandidates(sourcePosition)

            val referenceTypesInKtFile = classes.ifNotEmpty { findTargetClasses(this, sourcePosition) } ?: emptyList()

            val composeClassesIfNeeded = if (sourcePosition.isInsideProjectWithCompose()) {
                getComposableSingletonsClasses(debugProcess, psiFile)
            } else {
                emptyList()
            }

            return (referenceTypesInKtFile + classesWithInlinedCode + composeClassesIfNeeded).distinct()
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
    private fun getCandidates(sourcePosition: SourcePosition): List<ClassNameProvider.ClassNameCandidateInfo> =
        ClassNameProvider().getCandidatesInfo(sourcePosition)

    private fun List<ClassNameProvider.ClassNameCandidateInfo>.getReferenceTypesCandidates(sourcePosition: SourcePosition): CandidatesSet {
        val classes = flatMap { (className, _) -> debugProcess.virtualMachineProxy.classesByName(className) }

        val candidatesWithInline = filterHasInlineElements()
        if (candidatesWithInline.isEmpty()) return CandidatesSet(classes, emptyList())

        val line = sourcePosition.line + 1
        val classesWithInlinedCode = getClassesWithInlinedCode(candidatesWithInline, line)
        return CandidatesSet(classes, classesWithInlinedCode)
    }

    private fun getClassesWithInlinedCode(candidatesWithInline: List<String>, line: Int): List<ReferenceType> {
        val candidatesWithInlineInternalNames = candidatesWithInline.map { it.fqnToInternalName() }
        val futures = debugProcess.virtualMachineProxy.allClasses().map { type ->
            hasInlinedLinesToAsync(type, line, candidatesWithInlineInternalNames)
                .thenApply { hasInlinedLines -> type.takeIf { hasInlinedLines } }
                .exceptionally { e ->
                    val exception = DebuggerUtilsAsync.unwrap(e)
                    if (exception is ObjectCollectedException) null else throw e
                }
        }.toTypedArray()
        CompletableFuture.allOf(*futures).join()
        return futures.mapNotNull { it.get() }
    }

    @RequiresReadLockAbsence
    private fun findTargetClasses(candidates: List<ReferenceType>, sourcePosition: SourcePosition): List<ReferenceType> {
        try {
            val matchingCandidates = candidates
                .flatMap { referenceType -> debugProcess.findTargetClasses(referenceType, sourcePosition.line) }

            return matchingCandidates.ifEmpty { candidates }
        } catch (e: IncompatibleThreadStateException) {
            return emptyList()
        } catch (e: VMDisconnectedException) {
            return emptyList()
        }
    }

    override fun locationsOfLine(type: ReferenceType, position: SourcePosition): List<Location> {
        if (position.file !is KtFile) {
            throw NoDataException.INSTANCE
        }
        try {
            val virtualMachine = type.virtualMachine()
            if (DexDebugFacility.isDex(virtualMachine) && !virtualMachine.canGetSourceDebugExtension()) {
                // If we cannot get source debug extension information, we approximate information for inline functions.
                // This allows us to stop on some breakpoints in inline functions, but does not work very well.
                // Source debug extensions are not available on Android devices before Android O.
                val inlineLocations = ReadAction.nonBlocking<List<Location>> {
                    DebuggerUtils.getLocationsOfInlinedLine(type, position, debugProcess.searchScope)
                }.executeSynchronously()
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
        val xBreakpoint = requestor.asSafely<Breakpoint<*>>()?.xBreakpoint
        val xSession = debugProcess.asSafely<DebugProcessImpl>()?.xdebugProcess?.session.asSafely<XDebugSessionImpl>()
        return runBlockingMaybeCancellable {
            if (xBreakpoint == null || xSession == null) {
                collectPrepareRequestsWithProgress(requestor, position, isInsideProjectWithCompose, file)
            } else {
                cancelIfExpired({ readAction { xSession.isBreakpointActive(xBreakpoint) } }) {
                    collectPrepareRequestsWithProgress(requestor, position, isInsideProjectWithCompose, file)
                }
            }
        }.mapNotNull { request ->
            debugProcess.requestsManager.createClassPrepareRequest(request.requestor, request.pattern)?.apply {
                val vmProxy = debugProcess.virtualMachineProxy as? VirtualMachineProxyImpl ?: return@apply
                if (request.fileName != null && vmProxy.canUseSourceNameFilters()) {
                    addSourceNameFilter(request.fileName)
                }
            }
        }
    }

    private suspend fun collectPrepareRequestsWithProgress(
        requestor: ClassPrepareRequestor,
        position: SourcePosition,
        isInsideProjectWithCompose: Boolean,
        file: KtFile
    ): List<PrepareRequest> = readAction {
        collectPrepareRequests(requestor, position, isInsideProjectWithCompose, file)
    }

    private fun collectPrepareRequests(
        requestor: ClassPrepareRequestor, position: SourcePosition,
        isInsideProjectWithCompose: Boolean, file: KtFile
    ): List<PrepareRequest> {
        val kotlinRequests = getKotlinClassPrepareRequests(requestor, position)
        return if (isInsideProjectWithCompose) {
            val singletonRequestPattern = getClassPrepareRequestPatternForComposableSingletons(file)
            kotlinRequests + PrepareRequest(requestor, singletonRequestPattern)
        } else {
            kotlinRequests
        }
    }

    @RequiresReadLock
    private fun getKotlinClassPrepareRequests(requestor: ClassPrepareRequestor, position: SourcePosition): List<PrepareRequest> {
        val refinedPosition = when (requestor) {
            is SourcePositionRefiner -> requestor.refineSourcePosition(position)
            else -> position
        }

        val classRequests = mutableListOf<PrepareRequest>()
        val candidates = getCandidates(refinedPosition)
        candidates.flatMap { (name, _) -> listOf(name, "$name$*") }.mapTo(classRequests) { PrepareRequest(requestor, it) }

        val candidatesWithInline = candidates.filterHasInlineElements().map { it.fqnToInternalName() }
        if (candidatesWithInline.isNotEmpty()) {
            val line = refinedPosition.line + 1
            // We install a 'prepare request' for all classes that have mapping to this source position.
            // We do not know the class name for these classes, so we pass `null` pattern, meaning that all classes are suitable.
            // But we do know that the target class must have mapping to the file, so we pass the file name as a source pattern.
            val inlineCallRequest = PrepareRequest(
                InlineCallRequestorWrapper(requestor, line, candidatesWithInline),
                pattern = null,
                fileName = refinedPosition.file.name
            )
            classRequests.add(inlineCallRequest)
        }
        return classRequests
    }
}

private data class CandidatesSet(val classes: List<ReferenceType>, val classesWithInlinedCode: List<ReferenceType>)
private data class PrepareRequest(val requestor: ClassPrepareRequestor, val pattern: String?, val fileName: String? = null)

private fun List<ClassNameProvider.ClassNameCandidateInfo>.filterHasInlineElements() = filter { it.hasInlineElements }.map { it.name }

private class InlineCallRequestorWrapper(
    private val originalRequestor: ClassPrepareRequestor,
    private val line: Int,
    private val sourceCandidatesInternalName: List<String>,
) : ClassPrepareRequestor {
    override fun processClassPrepare(debuggerProcess: DebugProcess?, referenceType: ReferenceType?) {
        if (referenceType == null) return
        val hasInlinedLines = if (referenceType is ReferenceTypeImpl) {
            referenceType.hasMappedLineTo(KOTLIN_STRATA_NAME, line) { path ->
                sourcePathMatchesCandidates(path, sourceCandidatesInternalName)
            }
        } else {
            fallbackHasInlinedLinesTo(referenceType, line, sourceCandidatesInternalName)
        }
        if (hasInlinedLines) {
            originalRequestor.processClassPrepare(debuggerProcess, referenceType)
        }
    }
}

private fun hasInlinedLinesToAsync(
    referenceType: ReferenceType,
    line: Int,
    sourceCandidatesInternalName: List<String>
): CompletableFuture<Boolean> {
    return if (referenceType is ReferenceTypeImpl) {
        referenceType.hasMappedLineToAsync(KOTLIN_STRATA_NAME, line) { path ->
            sourcePathMatchesCandidates(path, sourceCandidatesInternalName)
        }
    } else {
        CompletableFuture.completedFuture(fallbackHasInlinedLinesTo(referenceType, line, sourceCandidatesInternalName))
    }
}

private fun fallbackHasInlinedLinesTo(referenceType: ReferenceType, line: Int, sourceCandidatesInternalName: List<String>): Boolean {
    val locations = try {
        DebuggerUtilsAsync.locationsOfLineSync(referenceType, KOTLIN_STRATA_NAME, null, line)
    } catch (e: AbsentInformationException) {
        emptyList()
    }
    return locations.any { location ->
        val sourcePath = location.safeSourcePath(KOTLIN_STRATA_NAME) ?: return@any false
        return sourcePathMatchesCandidates(sourcePath, sourceCandidatesInternalName)
    }
}

private fun sourcePathMatchesCandidates(sourcePath: String, sourceCandidatesInternalName: List<String>): Boolean {
    val internalName = FileUtil.toSystemIndependentName(sourcePath)
    return internalName.isInnerClassOfAny(sourceCandidatesInternalName)
}

private suspend fun findFileCandidatesWithBackgroundProcess(
    project: Project,
    className: JvmClassName,
    sourceName: String,
    scopes: List<GlobalSearchScope>
): List<KtFile> = withContext(Dispatchers.Default) {
    withBackgroundProgress(
        project, KotlinDebuggerCoreBundle.message("progress.title.kt.file.search"),
        cancellable = false
    ) {
        val files = try {
            readAction {
                FileBasedIndex.getInstance().ignoreDumbMode(DumbModeAccessType.RELIABLE_DATA_ONLY, ThrowableComputable {
                    val files = DebuggerUtils.findSourceFilesForClass(project, scopes, className, sourceName)
                    if (files.isNotEmpty()) return@ThrowableComputable files
                    DebuggerUtils.tryFindFileByClassNameAndFileName(project, className, sourceName, scopes)
                })
            }
        } catch (_: IndexNotReadyException) {
            emptyList()
        }
        if (files.isNotEmpty()) return@withBackgroundProgress files

        withProgressText(KotlinDebuggerCoreBundle.message("progress.text.waiting.for.smart.mode")) {
            waitForSmartMode(project)
        }
        smartReadAction(project) {
            DebuggerUtils.findSourceFilesForClass(project, scopes, className, sourceName)
        }
    }
}

private val FUNCTION_TYPES = arrayOf(
    KtFunction::class.java,
    KtClassInitializer::class.java,
    KtPropertyAccessor::class.java,
    KtScript::class.java
)

fun PsiElement.getContainingMethod(excludingElement: Boolean = true): KtExpression? =
    PsiTreeUtil.getParentOfType(this, excludingElement, *FUNCTION_TYPES)

fun PsiElement.getContainingBlockOrMethod(excludingElement: Boolean = true): KtExpression? =
    PsiTreeUtil.getParentOfType(this, excludingElement, KtBlockExpression::class.java, *FUNCTION_TYPES)

// Kotlin compiler generates private final static <outer-method>$lambda$0 method
// per each lambda that takes lambda (kotlin.jvm.functions.FunctionN) as the first parameter
// and all the rest are the lambda parameters (java.lang.Object).
// This generated method just calls the lambda with provided parameters.
// However, this method contains a line number (where a lambda is defined), so it should be ignored.
internal fun Method.isGeneratedErasedLambdaMethod(): Boolean {
    if (name().isGeneratedIrBackendLambdaMethodName() && isPrivate && isStatic) {
        val args = argumentTypeNames()
        val kotlinFunctionPrefix = "kotlin.jvm.functions.Function"
        if (args.size >= 2 && args[0].startsWith(kotlinFunctionPrefix)) {
            val parameterCount = args[0].removePrefix(kotlinFunctionPrefix).toIntOrNull()
            if (parameterCount != null && args.size == parameterCount + 1 &&
                args.drop(1).all { it == "java.lang.Object" }
            ) {
                return true
            }
        }
    }
    return false
}

private fun Location.getZeroBasedLineNumber(): Int =
    DebuggerUtilsEx.getLineNumber(this, true)

fun Location.getClassName(): String? {
    val currentLocationFqName = declaringType().name() ?: return null
    return JvmClassName.byFqNameWithoutInnerClasses(FqName(currentLocationFqName)).internalName.internalNameToFqn()
}

private fun DebugProcess.findTargetClasses(outerClass: ReferenceType, lineAt: Int): List<ReferenceType> {
    val targetClasses = ArrayList<ReferenceType>(1)

    try {
        if (!outerClass.isPrepared) {
            return emptyList()
        }

        for (location in outerClass.safeAllLineLocations()) {
            val locationLine = location.getZeroBasedLineNumber()
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
        val nestedTypes = virtualMachineProxy.nestedTypes(outerClass)
        for (nested in nestedTypes) {
            targetClasses += findTargetClasses(nested, lineAt)
        }
    } catch (_: AbsentInformationException) {
    } catch (_: ObjectCollectedException) {
        return emptyList()
    }

    return targetClasses
}

private suspend fun KtFunction.isSamLambda(): Boolean {
    if (this !is KtFunctionLiteral && this !is KtNamedFunction) {
        return false
    }

    return dumbAnalyze(this, fallback = false) f@{
        val parentCall = KtPsiUtil.getParentCallIfPresent(this@isSamLambda) as? KtCallExpression ?: return@f false
        val call = parentCall.resolveToCall()?.successfulFunctionCallOrNull() ?: return@f false
        val valueArgument = parentCall.getContainingValueArgument(this@isSamLambda) ?: return@f false
        val argument = call.argumentMapping[valueArgument.getArgumentExpression()]?.symbol ?: return@f false
        argument.returnType is KaUsualClassType
    }
}

private class IrLambdaDescriptor(name: String) : Comparable<IrLambdaDescriptor> {
    private val lambdaId: List<Int>

    init {
        require(name.isGeneratedIrBackendLambdaMethodName())
        val parts = name.split("\\\$lambda[$-]".toRegex())
        lambdaId = if (parts.isEmpty()) emptyList() else parts.drop(1).mapNotNull { it.toIntOrNull() }
    }

    override fun compareTo(other: IrLambdaDescriptor): Int {
        for ((left, right) in lambdaId.zip(other.lambdaId)) {
            if (left != right) return left - right
        }
        return lambdaId.size - other.lambdaId.size
    }
}

private suspend fun <T> CoroutineScope.cancelIfExpired(condition: suspend () -> Boolean, action: suspend () -> T): T {
    val cancellationJob = cancelWhenExpired(this, condition)
    try {
        return action()
    } finally {
        cancellationJob.cancel()
    }
}

private suspend fun cancelWhenExpired(scope: CoroutineScope, condition: suspend () -> Boolean): Job {
    suspend fun checkExpired() {
        if (!condition()) {
            scope.cancel()
        }
    }
    checkExpired()
    return scope.launch {
        while (true) {
            checkExpired()
            delay(500)
        }
    }
}

// Replace with platform implementation when IJPL-805 is fixed
private suspend fun waitForSmartMode(project: Project) = suspendCancellableCoroutine {
    DumbService.getInstance(project).runWhenSmart(ContextAwareRunnable { it.resume(Unit) })
}
