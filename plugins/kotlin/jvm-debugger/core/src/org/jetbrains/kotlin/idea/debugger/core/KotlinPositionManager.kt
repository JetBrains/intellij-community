// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// The package directive doesn't match the file location to prevent API breakage
package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.MultiRequestPositionManager
import com.intellij.debugger.NoDataException
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebuggerUtils.isSynthetic
import com.intellij.debugger.engine.PositionManagerImpl
import com.intellij.debugger.engine.PositionManagerWithMultipleStackFrames
import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.intellij.debugger.ui.breakpoints.Breakpoint
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFileManager
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
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.jetbrains.jdi.LocalVariableImpl
import com.sun.jdi.*
import com.sun.jdi.request.ClassPrepareRequest
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KtConstantAnnotationValue
import org.jetbrains.kotlin.analysis.api.annotations.annotations
import org.jetbrains.kotlin.analysis.api.base.KtConstantValue
import org.jetbrains.kotlin.analysis.api.calls.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtUsualClassType
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
import org.jetbrains.kotlin.idea.debugger.core.DebuggerUtils.getBorders
import org.jetbrains.kotlin.idea.debugger.core.DebuggerUtils.isGeneratedIrBackendLambdaMethodName
import org.jetbrains.kotlin.idea.debugger.core.breakpoints.*
import org.jetbrains.kotlin.idea.debugger.core.stackFrame.InlineStackTraceCalculator
import org.jetbrains.kotlin.idea.debugger.core.stackFrame.KotlinStackFrame
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.load.java.JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_ARGUMENT
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.jvm.JvmClassName
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

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

    override fun createStackFrames(descriptor: StackFrameDescriptorImpl): List<XStackFrame> {
        val location = descriptor.location
        if (location == null || !location.isInKotlinSources()) {
            return emptyList()
        }
        val frameProxy = descriptor.frameProxy
        // Don't provide inline stack trace for coroutine frames yet
        val coroutineFrame = stackFrameInterceptor?.createStackFrame(frameProxy, descriptor.debugProcess as DebugProcessImpl, location)
        if (coroutineFrame != null) {
            return listOf(coroutineFrame)
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

        if (location.safeMethod()?.isGeneratedErasedLambdaMethod() == true) {
            return null
        }

        PositionManagerImpl.adjustPositionForConditionalReturn(debugProcess, location, psiFile, sourceLineNumber)?.let {
            return it
        }

        val sourcePosition = createSourcePosition(location, psiFile, sourceLineNumber)
            ?: SourcePosition.createFromLine(psiFile, sourceLineNumber)

        // There may be several locations for same source line. If same source position would be created for all of them,
        // breakpoints at this line will stop on every location.
        if (sourcePosition !is KotlinReentrantSourcePosition && location.shouldBeTreatedAsReentrantSourcePosition(psiFile, fileName)) {
            return KotlinReentrantSourcePosition(sourcePosition)
        }

        return sourcePosition
    }

    private fun createSourcePosition(location: Location, file: KtFile, sourceLineNumber: Int): SourcePosition? {
        val lambdaOrFunIfInside = getLambdaOrFunOnLineIfInside(location, file, sourceLineNumber)
        if (lambdaOrFunIfInside != null) {
            val elementAt = getFirstElementInsideLambdaOnLine(file, lambdaOrFunIfInside, sourceLineNumber)
            if (elementAt != null) {
                return SourcePosition.createFromElement(elementAt)
            }
            return SourcePosition.createFromLine(file, sourceLineNumber)
        }

        val callableReferenceIfInside = getCallableReferenceIfInside(location, file, sourceLineNumber)
        if (callableReferenceIfInside != null) {
            val sourcePosition = SourcePosition.createFromElement(callableReferenceIfInside)
            if (sourcePosition != null) {
                // Never stop on invocation of method reference
                return KotlinReentrantSourcePosition(sourcePosition)
            }
        }

        val elementInDeclaration = getElementForDeclarationLine(location, file, sourceLineNumber)
        if (elementInDeclaration != null) {
            return SourcePosition.createFromElement(elementInDeclaration)
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

    private fun Location.shouldBeTreatedAsReentrantSourcePosition(psiFile: PsiFile, sourceFileName: String): Boolean {
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
        val locationsInSameInlinedFunction = findLocationsInSameInlinedFunction(sameLineLocations, method, sourceFileName)
        return locationsInSameInlinedFunction.ifEmpty { sameLineLocations }.indexOf(this) > 0
    }

    private fun Location.hasFinallyBlockInParent(psiFile: PsiFile): Boolean {
        val elementAt = psiFile.getLineStartOffset(getZeroBasedLineNumber())?.let { psiFile.findElementAt(it) }
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
        val contextElement = CodeFragmentContextTuner.getInstance().tuneContextElement(elementAt)

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
            it.calculatedClassNameMatches(currentLocationClassName, false)
        }
    }

    private fun getLambdaOrFunOnLineIfInside(location: Location, file: KtFile, lineNumber: Int): KtFunction? {
        val currentLocationClassName = location.getClassName() ?: return null

        val start = getStartLineOffset(file, lineNumber)
        val end = getEndLineOffset(file, lineNumber)
        if (start == null || end == null) return null

        val literalsOrFunctions = getLambdasAtLine(file, lineNumber)
        // We are not interested in lambdas when we're in the middle of them and no more lambdas on the line
        // because in such case there is only one possible source position on the line.
        if (literalsOrFunctions.none { it.isStartingOrEndingOnLine(lineNumber) }) {
            return null
        }
        analyze(literalsOrFunctions.first()) {
            val notInlinedLambdas = mutableListOf<KtFunction>()
            var innermostContainingLiteral: KtFunction? = null
            for (literal in literalsOrFunctions) {
                val inlineArgument = getInlineArgumentSymbol(literal)
                if (inlineArgument != null && (!inlineArgument.isCrossinline || isInlinedArgument(literal, location))) {
                    if (isInsideInlineArgument(literal, location, debugProcess as DebugProcessImpl)) {
                        innermostContainingLiteral = literal
                    }
                } else {
                    notInlinedLambdas.add(literal)
                }
            }
            if (innermostContainingLiteral != null) return innermostContainingLiteral

            return notInlinedLambdas.getAppropriateLiteralBasedOnDeclaringClassName(currentLocationClassName) ?:
                   notInlinedLambdas.getAppropriateLiteralForCrossinlineLambda(currentLocationClassName) ?:
                   notInlinedLambdas.getAppropriateLiteralBasedOnLambdaName(location, lineNumber)
        }
    }

    private fun List<KtFunction>.getAppropriateLiteralBasedOnDeclaringClassName(currentLocationClassName: String): KtFunction? {
        return firstOrNull { it.firstChild.calculatedClassNameMatches(currentLocationClassName, true) }
    }

    /**
     * Crossinline lambda generated class name contains $$inlined$<CALL METHOD NAME>$N substring
     * where N is the sequential number of the lambda with the same call method name
     */
    context(KtAnalysisSession)
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

    context(KtAnalysisSession)
    private fun KtCallExpression.getBytecodeMethodName(): String? {
        val resolvedCall = resolveCall()?.successfulFunctionCallOrNull() ?: return null
        val symbol = resolvedCall.partiallyAppliedSymbol.symbol.asSafely<KtFunctionSymbol>() ?: return null
        val jvmName = symbol.annotations
          .filter { it.classId?.asFqNameString() == "kotlin.jvm.JvmName" }
          .firstNotNullOfOrNull {
              it.arguments.singleOrNull { a -> a.name.asString() == "name" }
                ?.expression?.asSafely<KtConstantAnnotationValue>()
                ?.constantValue?.asSafely<KtConstantValue.KtStringConstantValue>()?.value
          }
        if (jvmName != null) return jvmName
        return symbol.name.identifier
    }

    private fun PsiElement.calculatedClassNameMatches(currentLocationClassName: String, isLambda: Boolean): Boolean {
        val classNameProvider = ClassNameProvider(
            debugProcess.project,
            debugProcess.searchScope,
            ClassNameProvider.Configuration.DEFAULT.copy(alwaysReturnLambdaParentClass = false)
        )

        return classNameProvider.getCandidatesForElement(this)
          .run { if (isLambda) filter(::isNestedClassName) else this }
          .any { it == currentLocationClassName }
    }

    private fun isNestedClassName(name: String): Boolean = "\$" in name

    private fun List<KtFunction>.getAppropriateLiteralBasedOnLambdaName(location: Location, lineNumber: Int): KtFunction? {
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

        // To bring the list of fun literals into conformity with list of lambda-methods in bytecode above
        // it is needed to filter out literals without executable code on current line.
        val suitableFunLiterals = filter { it.hasExecutableCodeInsideOnLine(lineNumber) }

        if (lambdas.size == suitableFunLiterals.size) {
            // All lambdas on the line compiled into methods
            return suitableFunLiterals[lambdas.indexOf(method)]
        }
        // SAM lambdas compiled into methods, and other non-SAM lambdas on same line compiled into anonymous classes
        return suitableFunLiterals.getSamLambdaWithIndex(lambdas.indexOf(method))
    }

    private fun KtFunction.hasExecutableCodeInsideOnLine(lineNumber: Int): Boolean {
        val file = containingFile.virtualFile
        return hasExecutableCodeInsideOnLine(file, lineNumber, project) { element ->
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

    private fun hasImplicitReturnOnLine(function: KtFunction, lineNumber: Int): Boolean {
        if (function !is KtFunctionLiteral || function.getLineNumber(start = false) != lineNumber) {
            return false
        }
        val isUnitReturnType = analyze(function) {
            val functionalType = function.getFunctionalType()
            (functionalType as? KtFunctionalType)?.returnType?.isUnit == true
        }
        if (!isUnitReturnType) {
            // We always must specify return explicitly
            return false
        }
        // This check does not cover some more complex cases (e.g. "if" or "when" block expressions)
        return function.lastStatementSkippingComments() !is KtReturnExpression
    }

    private fun KtFunction.lastStatementSkippingComments(): KtElement? {
        return bodyBlockExpression?.childrenOfType<KtElement>()?.lastOrNull()
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

            val candidates = syncNonBlockingReadAction(psiFile.project) {
                if (!RootKindFilter.projectAndLibrarySources.matches(psiFile)) return@syncNonBlockingReadAction null
                getReferenceTypesCandidates(sourcePosition)
            } ?: return emptyList()

            val referenceTypesInKtFile = candidates.ifNotEmpty { findTargetClasses(this, sourcePosition) } ?: emptyList()

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
    private fun getReferenceTypesCandidates(sourcePosition: SourcePosition): List<ReferenceType> {
        val classNameProvider = ClassNameProvider(debugProcess.project, debugProcess.searchScope, ClassNameProvider.Configuration.DEFAULT)
        return classNameProvider.getCandidates(sourcePosition)
            .flatMap { className -> debugProcess.virtualMachineProxy.classesByName(className) }
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

    @ApiStatus.ScheduledForRemoval
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
            if (DexDebugFacility.isDex(debugProcess) &&
                (debugProcess.virtualMachineProxy as? VirtualMachineProxyImpl)?.canGetSourceDebugExtension() != true) {
                // If we cannot get source debug extension information, we approximate information for inline functions.
                // This allows us to stop on some breakpoints in inline functions, but does not work very well.
                // Source debug extensions are not available on Android devices before Android O.
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
        var nonBlocking = ReadAction.nonBlocking<List<ClassPrepareRequest>> {
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
        }
            .inSmartMode(debugProcess.project)

        val xBreakpoint = requestor.safeAs<Breakpoint<*>>()?.xBreakpoint
        val xSession = debugProcess.asSafely<DebugProcessImpl>()?.xdebugProcess?.session.asSafely<XDebugSessionImpl>()
        if (xBreakpoint != null && xSession != null) {
            nonBlocking = nonBlocking.expireWhen { !xSession.isBreakpointActive(xBreakpoint) }
        }
        try {
            return nonBlocking.executeSynchronously()
        } catch (_: ProcessCanceledException) {
            return emptyList()
        }
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
    if (this !is KtFunctionLiteral && this !is KtNamedFunction) {
        return false
    }

    analyze(this) {
        val parentCall = KtPsiUtil.getParentCallIfPresent(this@isSamLambda) as? KtCallExpression ?: return false
        val call = parentCall.resolveCall()?.successfulFunctionCallOrNull() ?: return false
        val valueArgument = parentCall.getContainingValueArgument(this@isSamLambda) ?: return false
        val argument = call.argumentMapping[valueArgument.getArgumentExpression()]?.symbol ?: return false
        return argument.returnType is KtUsualClassType
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
