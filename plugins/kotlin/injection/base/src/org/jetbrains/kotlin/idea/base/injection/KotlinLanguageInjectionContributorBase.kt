// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.injection

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.lang.injection.general.LanguageInjectionContributor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PatternConditionPlus
import com.intellij.patterns.PsiClassNamePatternCondition
import com.intellij.patterns.ValuePatternCondition
import com.intellij.psi.*
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.asSafely
import org.intellij.plugins.intelliLang.Configuration
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport
import org.intellij.plugins.intelliLang.inject.TemporaryPlacesRegistry
import org.intellij.plugins.intelliLang.inject.config.BaseInjection
import org.intellij.plugins.intelliLang.inject.config.Injection
import org.intellij.plugins.intelliLang.inject.config.InjectionPlace
import org.intellij.plugins.intelliLang.inject.java.JavaLanguageInjectionSupport
import org.intellij.plugins.intelliLang.util.AnnotationUtilEx
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.idea.core.util.runInReadActionWithWriteActionPriority
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.util.match
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import com.intellij.lang.injection.general.Injection as GeneralInjection

@ApiStatus.Internal
abstract class KotlinLanguageInjectionContributorBase : LanguageInjectionContributor {
    // Abstract variables and functions are front-end dependent (K1 or K2).
    abstract val kotlinSupport: KotlinLanguageInjectionSupportBase?

    abstract fun KtCallExpression.hasCallableId(packageName: FqName, callableName: Name): Boolean

    abstract fun resolveReference(reference: PsiReference): PsiElement?

    abstract fun injectionInfoByAnnotation(callableDeclaration: KtCallableDeclaration): InjectionInfo?

    abstract fun injectionInfoByParameterAnnotation(functionReference: KtReference, argumentName: Name?, argumentIndex: Int): InjectionInfo?

    private val absentKotlinInjection: BaseInjection = BaseInjection("ABSENT_KOTLIN_BASE_INJECTION")

    companion object {
        private val STRING_LITERALS_REGEXP: Regex = "\"([^\"]*)\"".toRegex()
    }

    private data class KotlinCachedInjection(val modificationCount: Long, val baseInjection: BaseInjection)

    private var KtElement.cachedInjectionWithModification: KotlinCachedInjection? by UserDataProperty(
        Key.create("CACHED_INJECTION_WITH_MODIFICATION")
    )

    private fun getBaseInjection(ktHost: KtElement, support: LanguageInjectionSupport): Injection {
        if (!RootKindFilter.projectAndLibrarySources.matches(ktHost.containingFile.originalFile)) return absentKotlinInjection

        val needImmediateAnswer = with(ApplicationManager.getApplication()) { isDispatchThread }
        val kotlinCachedInjection = ktHost.cachedInjectionWithModification

        if (needImmediateAnswer) {
            // Can't afford long counting or typing will be laggy. Force cache reuse even if it's outdated.
            kotlinCachedInjection?.baseInjection?.let { return it }
        }

        val modificationCount = PsiManager.getInstance(ktHost.project).modificationTracker.modificationCount

        return when {
            kotlinCachedInjection != null && (modificationCount == kotlinCachedInjection.modificationCount) ->
                // Cache is up-to-date
                kotlinCachedInjection.baseInjection
            else -> {
                fun computeAndCache(): BaseInjection {
                    val computedInjection = computeBaseInjection(ktHost, support) ?: absentKotlinInjection
                    ktHost.cachedInjectionWithModification = KotlinCachedInjection(modificationCount, computedInjection)
                    return computedInjection
                }

                if (ApplicationManager.getApplication().run { !isDispatchThread && isReadAccessAllowed }
                    && ProgressManager.getInstance().progressIndicator == null) {
                    // The action cannot be canceled by caller and by internal checkCanceled() calls.
                    // Force creating new indicator that is canceled on write action start, otherwise there might be lags in typing.
                    runInReadActionWithWriteActionPriority(::computeAndCache) ?: kotlinCachedInjection?.baseInjection
                    ?: absentKotlinInjection
                } else {
                    computeAndCache()
                }
            }
        }
    }

    override fun getInjection(context: PsiElement): GeneralInjection? {
        if (context !is KtElement) return null
        if (!isSupportedElement(context)) return null
        val support = kotlinSupport ?: return null
        @OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
        return allowAnalysisOnEdt {
            allowAnalysisFromWriteAction {
                getBaseInjection(context, support).takeIf { it != absentKotlinInjection }
            }
        }
    }

    private fun computeBaseInjection(
        ktHost: KtElement,
        support: LanguageInjectionSupport
    ): BaseInjection? {
        val containingFile = ktHost.containingFile

        val host = when(ktHost) {
            is KtStringTemplateEntry -> ktHost.parent as? KtElement
            else -> ktHost
        } ?: return null

        val languageInjectionHost = when (host) {
            is PsiLanguageInjectionHost -> host
            is KtBinaryExpression -> flattenBinaryExpression(host).firstIsInstanceOrNull<PsiLanguageInjectionHost>()
            else -> null
        } ?: return null

        val unwrapped = unwrapTrims(host) // put before TempInjections for side effects, because TempInjection could also be trim-indented

        val tempInjectedLanguage = TemporaryPlacesRegistry.getInstance(host.project).getLanguageFor(languageInjectionHost, containingFile)
        if (tempInjectedLanguage != null) {
            return BaseInjection(support.id).apply {
                injectedLanguageId = tempInjectedLanguage.id
                prefix = tempInjectedLanguage.prefix
                suffix = tempInjectedLanguage.suffix
            }
        }
        return findInjectionInfo(unwrapped)?.toBaseInjection(support)
    }

    private fun unwrapTrims(ktHost: KtElement): KtElement {
        if (!Registry.`is`("kotlin.injection.handle.trimindent", true)) return ktHost
        val dotQualifiedExpression = ktHost.parent as? KtDotQualifiedExpression ?: return ktHost
        val callExpression = dotQualifiedExpression.selectorExpression.asSafely<KtCallExpression>() ?: return ktHost
        if (callExpression.hasCallableId(FqName("kotlin.text"), Name.identifier("trimIndent"))) {
            ktHost.indentHandler = TrimIndentHandler()
            return dotQualifiedExpression
        }
        if (callExpression.hasCallableId(FqName("kotlin.text"), Name.identifier("trimMargin"))) {
            val marginChar = callExpression.valueArguments.getOrNull(0)?.getArgumentExpression().asSafely<KtStringTemplateExpression>()
                ?.entries?.singleOrNull()?.asSafely<KtLiteralStringTemplateEntry>()?.text ?: "|"
            ktHost.indentHandler = TrimIndentHandler(marginChar)
            return dotQualifiedExpression
        }
        return ktHost
    }

    private fun findInjectionInfo(place: KtElement, originalHost: Boolean = true): InjectionInfo? {
        if (isAnalyzeOff(place.project)) return null

        return injectWithExplicitCodeInstruction(place)
            ?: injectWithCall(place)
            ?: injectReturnValue(place)
            ?: injectInAnnotationCall(place)
            ?: injectWithReceiver(place)
            ?: injectWithVariableUsage(place, originalHost)
            ?: injectWithMutation(place)
            ?: injectWithInfixCallOrOperator(place)
    }

    private val stringMutationOperators: List<KtSingleValueToken> = listOf(KtTokens.EQ, KtTokens.PLUSEQ)

    private fun injectWithMutation(host: KtElement): InjectionInfo? {
        val parent = (host.parent as? KtBinaryExpression)?.takeIf { it.safeOperationToken() in stringMutationOperators } ?: return null
        if (parent.right != host) return null

        if (isAnalyzeOff(host.project)) return null

        val property = when (val left = parent.left) {
            is KtQualifiedExpression -> left.selectorExpression
            else -> left
        } ?: return null

        val support = kotlinSupport ?: return null
        for (reference in property.references) {
            ProgressManager.checkCanceled()
            val resolvedTo = reference.resolve()
            if (resolvedTo is KtProperty) {
                val annotation = with(support) { resolvedTo.findAnnotation(FqName(AnnotationUtil.LANGUAGE)) ?: return null }
                return kotlinSupport?.toInjectionInfo(annotation)
            }
        }

        return null
    }

    private fun injectReturnValue(place: KtElement): InjectionInfo? {
        val parent = place.parent

        tailrec fun findReturnExpression(expression: PsiElement?): KtReturnExpression? = when (expression) {
            is KtReturnExpression -> expression
            is KtBinaryExpression -> findReturnExpression(expression.takeIf { it.safeOperationToken() == KtTokens.ELVIS }?.parent)
            is KtContainerNodeForControlStructureBody, is KtIfExpression -> findReturnExpression(expression.parent)
            else -> null
        }

        val returnExp = findReturnExpression(parent) ?: return null

        if (returnExp.labeledExpression != null) return null

        val callableDeclaration = PsiTreeUtil.getParentOfType(returnExp, KtDeclaration::class.java) as? KtCallableDeclaration ?: return null
        if (callableDeclaration.annotationEntries.isEmpty()) return null

        return injectionInfoByAnnotation(callableDeclaration)
    }

    private fun injectWithExplicitCodeInstruction(host: KtElement): InjectionInfo? {
        val support = kotlinSupport ?: return null
        return InjectionInfo.fromBaseInjection(support.findCommentInjection(host)) ?: support.findAnnotationInjectionLanguageId(host)
    }

    private fun injectWithReceiver(host: KtElement): InjectionInfo? {
        val qualifiedExpression = host.parent as? KtDotQualifiedExpression ?: return null
        if (qualifiedExpression.receiverExpression != host) return null

        val callExpression = qualifiedExpression.selectorExpression as? KtCallExpression ?: return null
        val callee = callExpression.calleeExpression ?: return null

        if (isAnalyzeOff(host.project)) return null

        val kotlinInjections: List<BaseInjection> = Configuration.getProjectInstance(host.project).getInjections(KOTLIN_SUPPORT_ID)

        val calleeName = callee.text
        val possibleNames = collectPossibleNames(kotlinInjections)

        if (calleeName !in possibleNames) {
            return null
        }

        for (reference in callee.references) {
            ProgressManager.checkCanceled()

            val resolvedTo = reference.resolve() as? KtFunction ?: continue
            resolvedTo.receiverTypeReference?.findInjection(kotlinInjections)?.let { return it }
        }

        return null
    }

    private fun collectPossibleNames(injections: List<BaseInjection>): Set<String> {
        val result = HashSet<String>()

        for (injection in injections) {
            val injectionPlaces = injection.injectionPlaces
            for (place in injectionPlaces) {
                val placeStr = place.toString()
                val literals = STRING_LITERALS_REGEXP.findAll(placeStr).map { it.groupValues[1] }
                result.addAll(literals)
            }
        }

        return result
    }

    private fun injectWithVariableUsage(host: KtElement, originalHost: Boolean): InjectionInfo? {
        // Given place is not original host of the injection so we stop to prevent stepping through indirect references
        if (!originalHost) return null

        val ktProperty = host.parent as? KtProperty ?: return null
        if (ktProperty.initializer != host) return null

        if (isAnalyzeOff(host.project)) return null

        val searchScope = LocalSearchScope(arrayOf(ktProperty.containingFile), "", true)
        val targetProperty = getTargetProperty(ktProperty)
        return ReferencesSearch.search(targetProperty, searchScope).asIterable().asSequence().mapNotNull { psiReference ->
            val element = psiReference.element as? KtElement ?: return@mapNotNull null
            findInjectionInfo(element, false)
        }.firstOrNull()
    }

    /**
     * Returns property which can be found in its containing file.
     * For K2 and non-physical copy, one needs to go to the original property to find something
     */
    protected open fun getTargetProperty(ktProperty: KtProperty): KtProperty = ktProperty

    private tailrec fun injectWithCall(host: KtElement): InjectionInfo? {
        val argument = getArgument(host) ?: return null
        val callExpression = PsiTreeUtil.getParentOfType(argument, KtCallElement::class.java) ?: return null

        if (getCallableShortName(callExpression) == "arrayOf") return injectWithCall(callExpression)
        val callee = getNameReference(callExpression.calleeExpression) ?: return null

        if (isAnalyzeOff(host.project)) return null

        for (reference in callee.references) {
            ProgressManager.checkCanceled()

            val resolvedTo = resolveReference(reference)
            if (resolvedTo is PsiMethod) {
                val injectionForJavaMethod = injectionForJavaMethod(argument, resolvedTo)
                if (injectionForJavaMethod != null) {
                    return injectionForJavaMethod
                }
            } else if (resolvedTo is KtFunction) {
                val injectionForKotlinFunction = injectionForKotlinCall(argument, resolvedTo, reference)
                if (injectionForKotlinFunction != null) {
                    return injectionForKotlinFunction
                }
            }
        }

        return null
    }

    private fun injectWithInfixCallOrOperator(host: KtElement): InjectionInfo? {
        // infix calls and operators are similar from the syntax point of view
        val deparenthesized = host.deparenthesized()
        val arrayAccessExpression = (deparenthesized.parent as? KtContainerNode)?.parent as? KtArrayAccessExpression
        val fixedHost = arrayAccessExpression ?: deparenthesized
        val binaryExpression = fixedHost.parent as? KtBinaryExpression ?: return arrayAccessExpression?.let { injectWithArrayReadAccess(deparenthesized, arrayAccessExpression) }
        val left = binaryExpression.left
        val right = binaryExpression.right
        if (fixedHost != left && fixedHost != right || binaryExpression.isStandardConcatenationExpression()) return null
        val operationExpression = binaryExpression.operationReference
        // all kotlin operators have two parameters, so they could be expressed as `left` `operator` `right`
        // exceptions are get and set operators, those use syntax of array access:
        // e.g. `a[key1, key2...]` for getter, and `a[key, key2...] = value` for setter
        val isArrayAccessExpression = left is KtArrayAccessExpression

        for (reference in operationExpression.references) {
            ProgressManager.checkCanceled()

            val ktFunction = resolveReference(reference) as? KtFunction ?: continue
            when (fixedHost) {
              right -> {
                  injectionForKotlinInfixCallParameter(ktFunction, reference, ktFunction.valueParameters.size - 1)?.let { return it }
              }
              left -> {
                  if (isArrayAccessExpression) {
                      injectionForKotlinInfixCallParameter(ktFunction, reference, left.indexExpressions.indexOf(deparenthesized))?.let { return it }
                  } else {
                      val injectionInfo = ktFunction.receiverTypeReference?.findInjection() ?: injectionInfoByAnnotation(ktFunction)
                      injectionInfo?.let { return it }
                  }
              }
            }
        }

        return null
    }

    private fun KtElement.deparenthesized(): KtElement {
        var element = this
        while (element.parent is KtParenthesizedExpression) {
            element = element.parent as? KtElement ?: break
        }

        return element
    }

    private fun injectWithArrayReadAccess(host: KtElement, arrayAccessExpression: KtArrayAccessExpression): InjectionInfo? {
        // get operator
        for (reference in arrayAccessExpression.references) {
            ProgressManager.checkCanceled()

            val ktFunction = resolveReference(reference) as? KtFunction ?: continue
            injectionForKotlinInfixCallParameter(ktFunction, reference, arrayAccessExpression.indexExpressions.indexOf(host))?.let { return it }
        }
        return null
    }

    private fun getNameReference(callee: KtExpression?): KtSimpleNameExpression? {
        if (callee is KtConstructorCalleeExpression)
            return callee.constructorReferenceExpression
        return callee as? KtSimpleNameExpression
    }

    private fun getArgument(host: KtElement): KtValueArgument? = when (val parent = host.parent) {
        is KtValueArgument -> parent
        is KtCollectionLiteralExpression, is KtCallElement -> parent.parent as? KtValueArgument
        else -> null
    }

    private tailrec fun injectInAnnotationCall(host: KtElement): InjectionInfo? {
        val argument = getArgument(host) ?: return null
        val annotationEntry = argument.parents.match(KtValueArgumentList::class, last = KtCallElement::class) ?: return null

        val callableShortName = getCallableShortName(annotationEntry) ?: return null
        if (callableShortName == "arrayOf") return injectInAnnotationCall(annotationEntry)

        if (!fastCheckInjectionsExists(callableShortName, host.project)) return null

        val calleeExpression = annotationEntry.calleeExpression ?: return null
        val callee = getNameReference(calleeExpression)?.mainReference?.let { reference ->
            resolveReference(reference)
        }
        when (callee) {
            is PsiClass -> {
                val psiClass = callee as? PsiClass ?: return null
                val argumentName = argument.getArgumentName()?.asName?.identifier ?: "value"
                val method = psiClass.findMethodsByName(argumentName, false).singleOrNull() ?: return null
                return findInjection(
                    method,
                    Configuration.getProjectInstance(host.project).getInjections(JavaLanguageInjectionSupport.JAVA_SUPPORT_ID)
                )
            }
            else -> return null
        }

    }

    private fun injectionForJavaMethod(argument: KtValueArgument, javaMethod: PsiMethod): InjectionInfo? {
        val argumentIndex = (argument.parent as KtValueArgumentList).arguments.indexOf(argument)
        val psiParameter = javaMethod.parameterList.parameters.getOrNull(argumentIndex) ?: return null

        val injectionInfo = findInjection(psiParameter,
                                          Configuration.getProjectInstance(argument.project)
                                              .getInjections(JavaLanguageInjectionSupport.JAVA_SUPPORT_ID)
        )
        if (injectionInfo != null) {
            return injectionInfo
        }

        val annotations = AnnotationUtilEx.getAnnotationFrom(
            psiParameter,
            Configuration.getProjectInstance(argument.project).advancedConfiguration.languageAnnotationPair,
            true
        )

        if (annotations.isNotEmpty()) {
            return processAnnotationInjectionInner(annotations)
        }

        return null
    }

    private fun injectionForKotlinCall(argument: KtValueArgument, ktFunction: KtFunction, reference: PsiReference): InjectionInfo? {
        val argumentName = argument.getArgumentName()?.asName
        val argumentListIndex = (argument.parent as KtValueArgumentList).arguments.indexOf(argument)
        val valueParameters = ktFunction.valueParameters
        val argumentIndex = if (argumentName != null) {
            null
        } else {
            // Skip vararg check if name is present
            (argumentListIndex downTo 0).firstNotNullOfOrNull { index ->
                valueParameters.getOrNull(index)?.takeIf { it.isVarArg }?.let { index }
            }
        } ?: argumentListIndex

        // Prefer using argument name if present
         val ktParameter = if (argumentName != null) {
            valueParameters.firstOrNull { it.nameAsName == argumentName }
        } else {
            valueParameters.getOrNull(argumentIndex)
        } ?: return null
        return injectionForKotlinCall(ktParameter, reference, argumentName, argumentIndex)
    }

    private fun injectionForKotlinInfixCallParameter(ktFunction: KtFunction, reference: PsiReference, argumentIndex: Int): InjectionInfo? {
        val ktParameter = ktFunction.valueParameters.getOrNull(argumentIndex) ?: return null
        return injectionForKotlinCall(ktParameter, reference, null, argumentIndex)
    }

    private fun injectionForKotlinCall(
        ktParameter: KtParameter,
        reference: PsiReference,
        argumentName: Name?,
        argumentIndex: Int
    ): InjectionInfo? {
        ktParameter.findInjection()?.let { return it }

        // Found psi element after resolve can be obtained from compiled declaration but annotations parameters are lost there.
        // Search for original descriptor for K1 or symbol for K2 from reference.
        val ktReference = reference as? KtReference ?: return null
        return injectionInfoByParameterAnnotation(ktReference, argumentName, argumentIndex)
    }

    private fun findInjection(element: PsiElement?, injections: List<BaseInjection>): InjectionInfo? {
        for (injection in injections) {
            if (injection.acceptsPsiElement(element)) {
                return InjectionInfo(injection.injectedLanguageId, injection.prefix, injection.suffix)
            }
        }

        return null
    }

    private fun KtElement.findInjection(injections: List<BaseInjection>? = null): InjectionInfo? =
        findInjection(
            this,
            injections ?: Configuration.getProjectInstance(this.project).getInjections(KOTLIN_SUPPORT_ID)
        )?.let { return it }

    private fun isAnalyzeOff(project: Project): Boolean {
        return Configuration.getProjectInstance(project).advancedConfiguration.dfaOption == Configuration.DfaOption.OFF
    }

    private fun processAnnotationInjectionInner(annotations: Array<PsiAnnotation>): InjectionInfo {
        val id = AnnotationUtilEx.calcAnnotationValue(annotations, "value")
        val prefix = AnnotationUtilEx.calcAnnotationValue(annotations, "prefix")
        val suffix = AnnotationUtilEx.calcAnnotationValue(annotations, "suffix")

        return InjectionInfo(id, prefix, suffix)
    }

    private fun createCachedValue(project: Project): CachedValueProvider.Result<Set<String>> {
        return with(Configuration.getProjectInstance(project)) {
            val result:MutableSet<String> = mutableSetOf()
            for (injection in getInjections(JavaLanguageInjectionSupport.JAVA_SUPPORT_ID)) {
                for (injectionPlace in injection.injectionPlaces) {
                    appendJavaPlaceTargetClassShortNames(injectionPlace, result)
                    appendKotlinPlaceTargetClassShortNames(injectionPlace, result)
                }
            }
            for (injection in getInjections(KOTLIN_SUPPORT_ID)) {
                for (injectionPlace in injection.injectionPlaces) {
                    appendJavaPlaceTargetClassShortNames(injectionPlace, result)
                    appendKotlinPlaceTargetClassShortNames(injectionPlace, result)
                }
            }
            CachedValueProvider.Result.create(result, this)
        }
    }

    private fun getInjectableTargetClassShortNames(project: Project): CachedValue<Set<String>> = CachedValuesManager.getManager(project).createCachedValue({ createCachedValue(project) }, false)

    private fun fastCheckInjectionsExists(
        annotationShortName: String,
        project: Project
    ): Boolean = annotationShortName in getInjectableTargetClassShortNames(project).value

    private fun getCallableShortName(annotationEntry: KtCallElement): String? {
        val referencedName = getNameReference(annotationEntry.calleeExpression)?.getReferencedName() ?: return null
        return KotlinPsiHeuristics.unwrapImportAlias(annotationEntry.containingKtFile, referencedName).singleOrNull() ?: referencedName
    }

    private fun appendJavaPlaceTargetClassShortNames(place: InjectionPlace, classNames: MutableCollection<String>) {
        val classCondition = place.elementPattern.condition.conditions.firstOrNull { it.debugMethodName == "definedInClass" }
                as? PatternConditionPlus<*, *> ?: return
        val psiClassNamePatternCondition =
            classCondition.valuePattern.condition.conditions.firstIsInstanceOrNull<PsiClassNamePatternCondition>() ?: return
        val valuePatternCondition =
            psiClassNamePatternCondition.namePattern.condition.conditions.firstIsInstanceOrNull<ValuePatternCondition<String>>()
                ?: return
        valuePatternCondition.values.forEach { classNames.add(StringUtilRt.getShortName(it)) }
    }

    private fun appendKotlinPlaceTargetClassShortNames(place: InjectionPlace, classNames: MutableCollection<String>) {
        fun collect(condition: PatternCondition<*>) {
            when (condition) {
                is PatternConditionPlus<*, *> -> condition.valuePattern.condition.conditions.forEach { collect(it) }
                is KotlinFunctionPatternBase.DefinedInClassCondition -> classNames.add(StringUtilRt.getShortName(condition.fqName))
            }
        }
        place.elementPattern.condition.conditions.forEach { collect(it) }
    }

}

internal fun isSupportedElement(context: KtElement): Boolean {
    if (context.parent?.isStandardConcatenationExpression() != false) return false // we will handle the top concatenation only, will not handle KtFile-s
    if (context is KtStringTemplateExpression && context.isValidHost) return true
    if (context.isStandardConcatenationExpression()) return true
    if (context.parent.parent is KtBinaryExpression) {
        return true
    }
    return false
}

internal var KtElement.indentHandler: IndentHandler? by UserDataProperty(
    Key.create("KOTLIN_INDENT_HANDLER")
)

private fun flattenBinaryExpression(root: KtBinaryExpression): Sequence<KtExpression> = sequence {
    root.left?.let { lOperand ->
        if (lOperand.isStandardConcatenationExpression())
            yieldAll(flattenBinaryExpression(lOperand as KtBinaryExpression))
        else
            yield(lOperand)
    }
    root.right?.let { yield(it) }
}

private fun KtExpression?.isSimpleConcatenationSubexpression(): Boolean =
    // "foo"
    this is KtStringTemplateExpression ||
    // 42
            this is KtConstantExpression ||
            // ("foo" + "bar")
            (this as? KtBinaryExpression)?.isSimpleStandardConcatenationExpression() == true

private fun KtBinaryExpression.isSimpleStandardConcatenationExpression(): Boolean =
    this.safeOperationToken() == KtTokens.PLUS &&
            left.isSimpleConcatenationSubexpression() && right.isSimpleConcatenationSubexpression()

private fun KtBinaryExpression.safeOperationToken(): IElementType? {
    // this.operationToken can throw NPE
    val operationReference = node.findChildByType(KtNodeTypes.OPERATION_REFERENCE)?.psi as? KtOperationReferenceExpression
    return operationReference?.getReferencedNameElementType()
}

@OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
private fun PsiElement.isStandardConcatenationExpression(): Boolean {
    if (this !is KtBinaryExpression || this.safeOperationToken() != KtTokens.PLUS) return false
    if (isSimpleStandardConcatenationExpression()) return true

    val referenceExpression = this.operationReference
    val packageFqName = allowAnalysisOnEdt {
        allowAnalysisFromWriteAction {
            analyze(referenceExpression) {
                val singleFunctionCallOrNull = referenceExpression.resolveToCall()?.singleFunctionCallOrNull()
                singleFunctionCallOrNull?.symbol?.callableId?.packageName
            }
        }
    } ?: return true
    val packageName = packageFqName.asString()
    val kotlinPackage = StandardNames.BUILT_INS_PACKAGE_NAME.asString()
    return packageName == kotlinPackage || packageName.startsWith(kotlinPackage)
}