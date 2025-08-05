// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.descendantsOfType
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.*
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.components.KaDataFlowExitPointSnapshot
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.components.diagnostics
import org.jetbrains.kotlin.analysis.api.components.expandedSymbol
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.impl.base.components.KaBaseIllegalPsiException
import org.jetbrains.kotlin.analysis.api.projectStructure.KaDanglingFileResolutionMode
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.findClass
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaAnnotatedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.names.FqNames
import org.jetbrains.kotlin.idea.base.util.names.FqNames.OptInFqNames.isRequiresOptInFqName
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.*
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.*
import org.jetbrains.kotlin.idea.references.ReadWriteAccessChecker
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal class ExtractionDataAnalyzer(private val extractionData: ExtractionData) :
    AbstractExtractionDataAnalyzer<KaType, MutableParameter>(extractionData) {

    override fun hasSyntaxErrors(): Boolean {
        return false
    }

    override fun getLocalDeclarationsWithNonLocalUsages(): List<KtNamedDeclaration> {
        val definedDeclarations = mutableListOf<KtNamedDeclaration>()
        extractionData.expressions.forEach { p ->
            p.accept(object : KtTreeVisitorVoid() {
                override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
                    super.visitNamedDeclaration(declaration)
                    ReferencesSearch.search(declaration).asIterable().forEach { ref ->
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
            val afterwardsRef = ReferencesSearch.search(prop).asIterable().firstOrNull { ref ->
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

    @OptIn(KaNonPublicApi::class)
    override fun createOutputDescriptor(): OutputDescriptor<KaType> = analyze(extractionData.commonParent) {
        // FIXME: KTIJ-34278
        @OptIn(KaImplementationDetail::class)
        KaBaseIllegalPsiException.allowIllegalPsiAccess {
            val exitSnapshot: KaDataFlowExitPointSnapshot = computeExitPointSnapshot(extractionData.expressions)

            val defaultExpressionInfo = exitSnapshot.defaultExpressionInfo
            val typeOfDefaultFlow = defaultExpressionInfo?.type?.takeIf {
                //extract as Unit function if the last expression is not used afterward
                !extractionData.options.inferUnitTypeForUnusedValues || defaultExpressionInfo.expression.isUsedAsExpression
            }

            val scope = extractionData.targetSibling
            OutputDescriptor(
                defaultResultExpression = defaultExpressionInfo?.expression,
                typeOfDefaultFlow = approximateWithResolvableType(typeOfDefaultFlow, scope) ?: builtinTypes.unit,
                implicitReturn = exitSnapshot.valuedReturnExpressions.filter { it !is KtReturnExpression }.singleOrNull(),
                lastExpressionHasNothingType = extractionData.expressions.lastOrNull()?.expressionType?.isNothingType == true,
                valuedReturnExpressions = exitSnapshot.valuedReturnExpressions.filter { it is KtReturnExpression },
                returnValueType = approximateWithResolvableType(exitSnapshot.returnValueType, scope) ?: builtinTypes.unit,
                jumpExpressions = exitSnapshot.jumpExpressions.filter { it is KtBreakExpression || it is KtContinueExpression || it is KtReturnExpression && it.returnedExpression == null },
                hasSingleTarget = !exitSnapshot.hasMultipleJumpTargets,
                sameExitForDefaultAndJump = if (exitSnapshot.hasJumps) !exitSnapshot.hasEscapingJumps && defaultExpressionInfo != null else defaultExpressionInfo == null
            )
        }
    }

    override val nameSuggester = KotlinNameSuggester

    override val typeDescriptor: TypeDescriptor<KaType> = KotlinTypeDescriptor(extractionData)

    override fun inferParametersInfo(
        virtualBlock: KtBlockExpression,
        modifiedVariables: Set<String>
    ): ParametersInfo<KaType, MutableParameter> {
        analyze(extractionData.commonParent) {
            return extractionData.inferParametersInfo(
                virtualBlock,
                modifiedVariables,
                typeDescriptor
            )
        }
    }

    @OptIn(KaExperimentalApi::class)
    override fun createDescriptor(
        suggestedFunctionNames: List<String>,
        defaultVisibility: KtModifierKeywordToken?,
        parameters: List<MutableParameter>,
        receiverParameter: MutableParameter?,
        typeParameters: List<TypeParameter>,
        replacementMap: MultiMap<KtSimpleNameExpression, IReplacement<KaType>>,
        flow: ControlFlow<KaType>,
        returnType: KaType
    ): IExtractableCodeDescriptor<KaType> {
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
            modifiers = emptyList(),
            optInMarkers = experimentalMarkers.optInMarkers,
            renderedAnnotations = getRenderedAnnotations(experimentalMarkers.propagatingMarkerClassIds)
        )
        val config = ExtractionGeneratorConfiguration(
            descriptor,
            ExtractionGeneratorOptions(inTempFile = true, allowExpressionBody = false)
        )

        val generatedDeclaration = Generator.generateDeclaration(config, null).declaration
        val illegalSuspendInside = analyzeCopy(generatedDeclaration, KaDanglingFileResolutionMode.PREFER_SELF) {
            generatedDeclaration.descendantsOfType<KtExpression>()
                .flatMap {
                    it.diagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
                        .map { it.diagnosticClass }
                }
                .any { it == KaFirDiagnostic.IllegalSuspendFunctionCall::class || it == KaFirDiagnostic.IllegalSuspendPropertyAccess::class || it == KaFirDiagnostic.NonLocalSuspensionPoint::class }
        }
        if (illegalSuspendInside) {
            descriptor = descriptor.copy(modifiers = listOf(KtTokens.SUSPEND_KEYWORD))
        }


        for (analyser in ExtractFunctionDescriptorModifier.EP_NAME.extensionList) {
            descriptor = analyser.modifyDescriptor(descriptor)
        }
        return descriptor
    }

    @OptIn(KaExperimentalApi::class)
    private fun getRenderedAnnotations(annotationClassIds: Set<ClassId>): List<String> {
        if (annotationClassIds.isEmpty()) return emptyList()
        val container = extractionData.commonParent.getStrictParentOfType<KtNamedFunction>() ?: return emptyList()
        return analyze(container) {
            val owner = container.symbol
            val renderer = KaDeclarationRendererForSource.WITH_QUALIFIED_NAMES.annotationRenderer
            owner.annotations.filter { it.classId in annotationClassIds }.map { annotation ->
                val printer = PrettyPrinter()
                printer.append('@')
                renderer.annotationUseSiteTargetRenderer.renderUseSiteTarget(useSiteSession, annotation, owner, renderer, printer)
                renderer.annotationsQualifiedNameRenderer.renderQualifier(useSiteSession, annotation, owner, renderer, printer)
                renderer.annotationArgumentsRenderer.renderAnnotationArguments(useSiteSession, annotation, owner, renderer, printer)
                printer.append('\n')
                printer.toString()
            }
        }
    }
}

private data class ExperimentalMarkers(
    val propagatingMarkerClassIds: Set<ClassId>,
    val optInMarkers: List<FqName>
) {
    companion object {
        val empty = ExperimentalMarkers(emptySet(), emptyList())
    }
}

context(_: KaSession)
private fun IExtractionData.getExperimentalMarkers(): ExperimentalMarkers {
    fun KaAnnotation.isExperimentalMarker(): Boolean {
        val id = classId
        if (id == null) return false
        val annotations = findClass(id)?.annotations ?: return false
        return annotations.any { isRequiresOptInFqName(it.classId?.asSingleFqName()) }
    }

    val container = commonParent.getStrictParentOfType<KtNamedFunction>() ?: return ExperimentalMarkers.empty

    val propagatingMarkerDescriptors = mutableListOf<KaAnnotation>()
    val optInMarkerNames = mutableListOf<FqName>()
    for (annotationEntry in container.symbol.annotations) {
        val fqName = annotationEntry.classId?.asSingleFqName() ?: continue

        if (fqName in FqNames.OptInFqNames.OPT_IN_FQ_NAMES) {
            fun processValue(value: KaAnnotationValue, isRecursive: Boolean) {
                when (value) {
                    is KaAnnotationValue.ClassLiteralValue -> {
                        val classId = (value.type as? KaClassType)?.classId?.takeUnless { it.isLocal }
                        if (classId != null) {
                            optInMarkerNames.add(classId.asSingleFqName())
                        }
                    }
                    is KaAnnotationValue.ArrayValue -> {
                        if (isRecursive) {
                            value.values.forEach { processValue(it, isRecursive = false) }
                        }
                    }
                    else -> {}
                }
            }

            annotationEntry.arguments.forEach { processValue(it.expression, isRecursive = true) }
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

                    fun processSymbolAnnotations(targetSymbol: KaAnnotatedSymbol) {
                        for (ann in targetSymbol.annotations) {
                            val fqName = ann.classId?.asSingleFqName() ?: continue
                            if (ann.isExperimentalMarker()) {
                                requiredMarkers.add(fqName)
                            }
                        }
                    }

                    val targetSymbol = expression.mainReference.resolveToSymbol() as? KaAnnotatedSymbol ?: return
                    processSymbolAnnotations(targetSymbol)

                    val typeSymbol = (targetSymbol as? KaCallableSymbol)?.returnType?.expandedSymbol ?: return
                    processSymbolAnnotations(typeSymbol)
                }
            })
        }
    }

    val propagatingMarkerClassIds = propagatingMarkerDescriptors
        .mapNotNull { it.classId }
        .filterTo(LinkedHashSet()) { it.asSingleFqName() in requiredMarkers }

    return ExperimentalMarkers(
        propagatingMarkerClassIds,
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

    return analyzeCopy(result.declaration, KaDanglingFileResolutionMode.PREFER_SELF) {
        validateTempResult(result)
    }
}

context(_: KaSession)
@OptIn(KaExperimentalApi::class)
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
    val contextReceiverList = namedFunction?.contextReceiverList
    val typeParameterList = namedFunction?.typeParameterList

    fun processReference(currentRefExpr: KtSimpleNameExpression) {
        val resolveResult = currentRefExpr.resolveResult as? ResolveResult<PsiNamedElement, KtSimpleNameExpression> ?: return
        if (currentRefExpr.parent is KtThisExpression) return

        val diagnostics = currentRefExpr.diagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)

        val currentDescriptor = currentRefExpr.mainReference.resolve()
        if (currentDescriptor is KtParameter && currentDescriptor.parent == valueParameterList) return
        if (currentDescriptor is KtParameter && currentDescriptor.isContextParameter && currentDescriptor.parent == contextReceiverList) return
        if (currentDescriptor is KtTypeParameter && currentDescriptor.parent == typeParameterList) return
        if (currentDescriptor is KtProperty && currentDescriptor.isLocal
            && parameters.any { it.mirrorVarName == currentDescriptor.name }
        ) return

        if (diagnostics.any {
                it.diagnosticClass == KaFirDiagnostic.UnresolvedReference::class ||
                        it.diagnosticClass == KaFirDiagnostic.UnresolvedReferenceWrongReceiver::class
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
            it.diagnosticClass == KaFirDiagnostic.InvisibleReference::class ||
                    it.diagnosticClass == KaFirDiagnostic.InvisibleSetter::class
        }?.let {
            val message = when (it.diagnosticClass) {
                KaFirDiagnostic.InvisibleSetter::class ->
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
                val diagnostics = refExpr.diagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
                diagnostics.firstOrNull { it.diagnosticClass == KaFirDiagnostic.InvisibleReference::class }?.let {
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