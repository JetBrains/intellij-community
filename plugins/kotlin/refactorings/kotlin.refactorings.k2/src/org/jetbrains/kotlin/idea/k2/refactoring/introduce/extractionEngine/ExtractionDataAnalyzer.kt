// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.descendantsOfType
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.KtAnalysisNonPublicApi
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.analyzeCopy
import org.jetbrains.kotlin.analysis.api.annotations.KtAnnotationApplicationWithArgumentsInfo
import org.jetbrains.kotlin.analysis.api.annotations.KtArrayAnnotationValue
import org.jetbrains.kotlin.analysis.api.annotations.KtKClassAnnotationValue
import org.jetbrains.kotlin.analysis.api.annotations.annotations
import org.jetbrains.kotlin.analysis.api.components.KtDataFlowExitPointSnapshot
import org.jetbrains.kotlin.analysis.api.components.KtDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtAnnotatedSymbol
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.project.structure.DanglingFileResolutionMode
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.names.FqNames
import org.jetbrains.kotlin.idea.base.util.names.FqNames.OptInFqNames.isRequiresOptInFqName
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractFunctionDescriptorModifier
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractableCodeDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractableCodeDescriptorWithConflicts
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractionData
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractionGeneratorConfiguration
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractionResult
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.MutableParameter
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.inferParametersInfo
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.targetKey
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.AbstractExtractionDataAnalyzer
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ControlFlow
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionGeneratorOptions
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionTarget
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.IExtractableCodeDescriptor
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.IExtractionData
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.IReplacement
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.OutputDescriptor
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ParametersInfo
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ResolveResult
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.TypeDescriptor
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.TypeParameter
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.getDefaultVisibility
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.resolveResult
import org.jetbrains.kotlin.idea.references.ReadWriteAccessChecker
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtBreakExpression
import org.jetbrains.kotlin.psi.KtContinueExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

@OptIn(KtAnalysisNonPublicApi::class)
internal class ExtractionDataAnalyzer(private val extractionData: ExtractionData) :
    AbstractExtractionDataAnalyzer<KtType, MutableParameter>(extractionData) {

    override fun hasSyntaxErrors(): Boolean {
        return false
    }

    override fun getLocalDeclarationsWithNonLocalUsages(): List<KtNamedDeclaration> {
        val definedDeclarations = mutableListOf<KtNamedDeclaration>()
        extractionData.expressions.forEach { p ->
            p.accept(object : KtTreeVisitorVoid() {
                override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
                    super.visitNamedDeclaration(declaration)
                    ReferencesSearch.search(declaration).forEach { ref ->
                        if (extractionData.expressions.none { PsiTreeUtil.isAncestor(it, ref.element, false) }) {
                            definedDeclarations.add(declaration)
                            return
                        }
                    }
                }
            })
        }
        return definedDeclarations
    }

    override fun getVarDescriptorsAccessedAfterwards(): Set<String> {
        val accessedLocals = mutableSetOf<String>()
        val allProperties = mutableSetOf<KtProperty>()
        val collector = object : VariableCollector() {
            override fun registerModifiedVar(e: KtProperty) {
                allProperties.add(e)
            }
        }
        extractionData.expressions.forEach { it.accept(collector) }
        for (prop in allProperties) {
            val name = prop.name ?: continue
            val afterwardsRef = ReferencesSearch.search(prop).firstOrNull { ref ->
                extractionData.expressions.none { PsiTreeUtil.isAncestor(it, ref.element, false) }
            }
            if (afterwardsRef != null) {
                accessedLocals.add(name)
            }
        }
        return accessedLocals
    }

    override fun getModifiedVars(): Map<String, List<KtExpression>> {
        val map = HashMap<String, ArrayList<KtExpression>>()
        val collector = object : VariableCollector() {
            override fun acceptProperty(prop: KtProperty): Boolean {
                return prop.hasInitializer()
            }

            override fun registerModifiedVar(e: KtProperty) {
                //TODO register assigned expr for duplicates processing https://youtrack.jetbrains.com/issue/KTIJ-29165
                e.name?.let { key -> map.getOrPut(key) { ArrayList() } }
            }
        }
        extractionData.expressions.forEach { it.accept(collector) }
        return map
    }

    private abstract inner class VariableCollector : KtTreeVisitorVoid() {
        private val accessChecker: ReadWriteAccessChecker = ReadWriteAccessChecker.getInstance(extractionData.project)

        open fun acceptProperty(prop: KtProperty): Boolean = true

        override fun visitDeclaration(declaration: KtDeclaration) {
            if (declaration is KtProperty && declaration.isLocal && acceptProperty(declaration)) {
                registerModifiedVar(declaration)
            }
            super.visitDeclaration(declaration)
        }

        override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
            if (accessChecker.readWriteAccessWithFullExpression(expression, true).first.isWrite) {
                val target = expression.mainReference.resolve()
                if (target is KtProperty && target.isLocal) {
                    registerModifiedVar(target)
                }
                super.visitSimpleNameExpression(expression)
            }
        }

        abstract fun registerModifiedVar(e: KtProperty)
    }

    override fun createOutputDescriptor(): OutputDescriptor<KtType> {
        analyze(extractionData.commonParent) {
            val exitSnapshot: KtDataFlowExitPointSnapshot = getExitPointSnapshot(extractionData.expressions)
            val defaultExpressionInfo = exitSnapshot.defaultExpressionInfo
            val typeOfDefaultFlow = defaultExpressionInfo?.type?.takeIf {
                //extract as Unit function if the last expression is not used afterward
                !extractionData.options.inferUnitTypeForUnusedValues || defaultExpressionInfo.expression.isUsedAsExpression()
            }

            val scope = extractionData.targetSibling as KtElement
            return OutputDescriptor(
                defaultResultExpression = defaultExpressionInfo?.expression,
                typeOfDefaultFlow = approximateWithResolvableType(typeOfDefaultFlow, scope) ?: builtinTypes.UNIT,
                implicitReturn = exitSnapshot.valuedReturnExpressions.filter { it !is KtReturnExpression }.singleOrNull(),
                lastExpressionHasNothingType = extractionData.expressions.lastOrNull()?.getKtType()?.isNothing == true,
                valuedReturnExpressions = exitSnapshot.valuedReturnExpressions.filter { it is KtReturnExpression },
                returnValueType = approximateWithResolvableType(exitSnapshot.returnValueType, scope) ?: builtinTypes.UNIT,
                jumpExpressions = exitSnapshot.jumpExpressions.filter { it is KtBreakExpression || it is KtContinueExpression || it is KtReturnExpression && it.returnedExpression == null},
                hasSingleTarget = !exitSnapshot.hasMultipleJumpTargets,
                sameExitForDefaultAndJump = if (exitSnapshot.hasJumps) !exitSnapshot.hasEscapingJumps && defaultExpressionInfo != null else defaultExpressionInfo == null
            )
        }
    }

    override val nameSuggester = KotlinNameSuggester

    override val typeDescriptor: TypeDescriptor<KtType> = KotlinTypeDescriptor(extractionData)

    override fun inferParametersInfo(
        virtualBlock: KtBlockExpression,
        modifiedVariables: Set<String>
    ): ParametersInfo<KtType, MutableParameter> {
        analyze(extractionData.commonParent) {
            return extractionData.inferParametersInfo(
                virtualBlock,
                modifiedVariables,
                typeDescriptor
            )
        }
    }

    override fun createDescriptor(
        suggestedFunctionNames: List<String>,
        defaultVisibility: KtModifierKeywordToken?,
        parameters: List<MutableParameter>,
        receiverParameter: MutableParameter?,
        typeParameters: List<TypeParameter>,
        replacementMap: MultiMap<KtSimpleNameExpression, IReplacement<KtType>>,
        flow: ControlFlow<KtType>,
        returnType: KtType
    ): IExtractableCodeDescriptor<KtType> {
        val experimentalMarkers = analyze(extractionData.commonParent) { extractionData.getExperimentalMarkers() }
        var descriptor = ExtractableCodeDescriptor(
            context = extractionData.commonParent,
            extractionData = extractionData,
            suggestedNames = suggestedFunctionNames,
            visibility = extractionData.getDefaultVisibility(),
            parameters = parameters,
            receiverParameter = receiverParameter,
            typeParameters = typeParameters,
            replacementMap = replacementMap,
            controlFlow = flow,
            returnType = returnType,
            modifiers = if (hasSuspendReference(extractionData)) listOf(KtTokens.SUSPEND_KEYWORD) else emptyList(),
            optInMarkers = experimentalMarkers.optInMarkers,
            annotations = experimentalMarkers.propagatingMarkerDescriptors
        )
        for (analyser in ExtractFunctionDescriptorModifier.EP_NAME.extensionList) {
            descriptor = analyser.modifyDescriptor(descriptor)
        }
        return descriptor
    }

    private fun hasSuspendReference(extractionData: ExtractionData): Boolean {
        return extractionData.expressions
            .flatMap { it.descendantsOfType<KtSimpleNameExpression>() }
            .any { nameExpression ->
                analyze(nameExpression) {
                    val symbol = nameExpression.mainReference.resolveToSymbol()
                    symbol is KtFunctionSymbol && symbol.isSuspend
                }
            }
    }
}

private data class ExperimentalMarkers(
    val propagatingMarkerDescriptors: List<KtAnnotationApplicationWithArgumentsInfo>,
    val optInMarkers: List<FqName>
) {
    companion object {
        val empty = ExperimentalMarkers(emptyList(), emptyList())
    }
}

context(KtAnalysisSession)
private fun IExtractionData.getExperimentalMarkers(): ExperimentalMarkers {
    fun KtAnnotationApplicationWithArgumentsInfo.isExperimentalMarker(): Boolean {
        val id = classId
        if (id == null) return false
        val annotations = getClassOrObjectSymbolByClassId(id)?.annotations ?: return false
        return annotations.any { isRequiresOptInFqName(it.classId?.asSingleFqName()) }
    }

    val container = commonParent.getStrictParentOfType<KtNamedFunction>() ?: return ExperimentalMarkers.empty

    val propagatingMarkerDescriptors = mutableListOf<KtAnnotationApplicationWithArgumentsInfo>()
    val optInMarkerNames = mutableListOf<FqName>()
    for (annotationEntry in container.getSymbol().annotations) {
        val fqName = annotationEntry.classId?.asSingleFqName() ?: continue

        if (fqName in FqNames.OptInFqNames.OPT_IN_FQ_NAMES) {
            for (argument in annotationEntry.arguments) {
                val expression = argument.expression
                if (expression is KtKClassAnnotationValue.KtNonLocalKClassAnnotationValue) {
                    optInMarkerNames.add(expression.classId.asSingleFqName())
                } else if (expression is KtArrayAnnotationValue) {
                    expression.values.filterIsInstance<KtKClassAnnotationValue.KtNonLocalKClassAnnotationValue>()
                        .forEach { optInMarkerNames.add(it.classId.asSingleFqName()) }
                }
            }
        } else if (annotationEntry.isExperimentalMarker()) {
            propagatingMarkerDescriptors.add(annotationEntry)
        }
    }

    val requiredMarkers = mutableSetOf<FqName>()
    if (propagatingMarkerDescriptors.isNotEmpty() || optInMarkerNames.isNotEmpty()) {
        originalElements.forEach { element ->
            element.accept(object : KtTreeVisitorVoid() {
                override fun visitReferenceExpression(expression: KtReferenceExpression) {
                    super.visitReferenceExpression(expression)
                    val descriptor = expression.mainReference.resolveToSymbol() as? KtAnnotatedSymbol ?: return

                    for (ann in descriptor.annotations) {
                        val fqName = ann.classId?.asSingleFqName() ?: continue
                        if (ann.isExperimentalMarker()) {
                            requiredMarkers.add(fqName)
                        }
                    }
                }
            })
        }
    }

    return ExperimentalMarkers(
        propagatingMarkerDescriptors.filter {
            val classId = it.classId
            classId != null && classId.asSingleFqName() in requiredMarkers
        },
        optInMarkerNames.filter { it in requiredMarkers }
    )
}

fun ExtractableCodeDescriptor.validate(target: ExtractionTarget = ExtractionTarget.FUNCTION): ExtractableCodeDescriptorWithConflicts {
    val config = ExtractionGeneratorConfiguration(
        this,
        ExtractionGeneratorOptions(
            inTempFile = true,
            allowExpressionBody = false,
            target = target
        )
    )
    val result = Generator.generateDeclaration(config, null)

    return analyzeCopy(result.declaration, DanglingFileResolutionMode.PREFER_SELF) {
        validateTempResult(result)
    }
}

context(KtAnalysisSession)
private fun ExtractableCodeDescriptor.validateTempResult(
    result: ExtractionResult,
): ExtractableCodeDescriptorWithConflicts {
    fun getDeclarationMessage(declaration: PsiElement, messageKey: String, capitalize: Boolean = true): String {
        val declarationStr = RefactoringUIUtil.getDescription(declaration, true)
        val message = KotlinBundle.message(messageKey, declarationStr)
        return if (capitalize) message.capitalize() else message
    }

    val conflicts = MultiMap<PsiElement, String>()

    val namedFunction = result.declaration as? KtNamedFunction
    val valueParameterList = namedFunction?.valueParameterList
    val typeParameterList = namedFunction?.typeParameterList

    fun processReference(currentRefExpr: KtSimpleNameExpression) {
        val resolveResult = currentRefExpr.resolveResult as? ResolveResult<PsiNamedElement, KtSimpleNameExpression> ?: return
        if (currentRefExpr.parent is KtThisExpression) return

        val diagnostics = currentRefExpr.getDiagnostics(KtDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)

        val currentDescriptor = currentRefExpr.mainReference.resolve()
        if (currentDescriptor is KtParameter && currentDescriptor.parent == valueParameterList) return
        if (currentDescriptor is KtTypeParameter && currentDescriptor.parent == typeParameterList) return
        if (currentDescriptor is KtProperty && currentDescriptor.isLocal
            && parameters.any { it.mirrorVarName == currentDescriptor.name }
        ) return

        if (diagnostics.any {
                it.diagnosticClass == KtFirDiagnostic.UnresolvedReference::class ||
                        it.diagnosticClass == KtFirDiagnostic.UnresolvedReferenceWrongReceiver::class
            }
            || (currentDescriptor != null
                    && currentDescriptor != resolveResult.descriptor
                    && currentDescriptor.getCopyableUserData(targetKey) != resolveResult.descriptor.getCopyableUserData(targetKey))) {
            conflicts.putValue(
                resolveResult.originalRefExpr,
                getDeclarationMessage(resolveResult.declaration, "0.will.no.longer.be.accessible.after.extraction")
            )
            return
        }

        diagnostics.firstOrNull {
            it.diagnosticClass == KtFirDiagnostic.InvisibleReference::class ||
                    it.diagnosticClass == KtFirDiagnostic.InvisibleSetter::class
        }?.let {
            val message = when (it.diagnosticClass) {
                KtFirDiagnostic.InvisibleSetter::class ->
                    getDeclarationMessage(resolveResult.declaration, "setter.of.0.will.become.invisible.after.extraction", false)

                else ->
                    getDeclarationMessage(resolveResult.declaration, "0.will.become.invisible.after.extraction")
            }
            conflicts.putValue(resolveResult.originalRefExpr, message)
        }

    }

    result.declaration.accept(
        object : KtTreeVisitorVoid() {
            override fun visitUserType(userType: KtUserType) {
                val refExpr = userType.referenceExpression ?: return
                val diagnostics = refExpr.getDiagnostics(KtDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
                diagnostics.firstOrNull { it.diagnosticClass == KtFirDiagnostic.InvisibleReference::class }?.let {
                    val declaration = refExpr.mainReference.resolve() as? PsiNamedElement ?: return
                    conflicts.putValue(declaration, getDeclarationMessage(declaration, "0.will.become.invisible.after.extraction"))
                }
            }

            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                processReference(expression)
            }
        }
    )

    return ExtractableCodeDescriptorWithConflicts(this, conflicts)
}