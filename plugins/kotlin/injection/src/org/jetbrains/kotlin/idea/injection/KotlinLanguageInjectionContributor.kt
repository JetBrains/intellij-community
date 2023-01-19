// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.injection

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
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.asSafely
import org.intellij.plugins.intelliLang.Configuration
import org.intellij.plugins.intelliLang.inject.InjectorUtils
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport
import org.intellij.plugins.intelliLang.inject.TemporaryPlacesRegistry
import org.intellij.plugins.intelliLang.inject.config.BaseInjection
import org.intellij.plugins.intelliLang.inject.config.Injection
import org.intellij.plugins.intelliLang.inject.config.InjectionPlace
import org.intellij.plugins.intelliLang.inject.java.JavaLanguageInjectionSupport
import org.intellij.plugins.intelliLang.util.AnnotationUtilEx
import org.jetbrains.kotlin.base.fe10.analysis.findAnnotation
import org.jetbrains.kotlin.base.fe10.analysis.getStringValue
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotated
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.util.match
import org.jetbrains.kotlin.idea.caches.resolve.allowResolveInDispatchThread
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.core.util.runInReadActionWithWriteActionPriority
import org.jetbrains.kotlin.idea.patterns.KotlinFunctionPattern
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.resolveToDescriptors
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.utils.SmartList
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.intellij.lang.annotations.Language as LanguageAnnotation
import org.jetbrains.kotlin.psi.psiUtil.parents

class KotlinLanguageInjectionContributor : LanguageInjectionContributor {
    private val absentKotlinInjection = BaseInjection("ABSENT_KOTLIN_BASE_INJECTION")

    companion object {
        private val STRING_LITERALS_REGEXP = "\"([^\"]*)\"".toRegex()
    }

    private val kotlinSupport: KotlinLanguageInjectionSupport? by lazy {
        ArrayList(InjectorUtils.getActiveInjectionSupports()).filterIsInstance(KotlinLanguageInjectionSupport::class.java).firstOrNull()
    }

    private data class KotlinCachedInjection(val modificationCount: Long, val baseInjection: BaseInjection)

    private var KtElement.cachedInjectionWithModification: KotlinCachedInjection? by UserDataProperty(
        Key.create<KotlinCachedInjection>("CACHED_INJECTION_WITH_MODIFICATION")
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

    override fun getInjection(context: PsiElement): com.intellij.lang.injection.general.Injection? {
        if (context !is KtElement) return null
        if (!isSupportedElement(context)) return null
        val support = kotlinSupport ?: return null
        return getBaseInjection(context, support).takeIf { it != absentKotlinInjection }
    }

    private fun computeBaseInjection(
        ktHost: KtElement,
        support: LanguageInjectionSupport
    ): BaseInjection? {
        val containingFile = ktHost.containingFile

        val languageInjectionHost = when (ktHost) {
            is PsiLanguageInjectionHost -> ktHost
            is KtBinaryExpression -> flattenBinaryExpression(ktHost).firstIsInstanceOrNull<PsiLanguageInjectionHost>()
            else -> null
        } ?: return null

        val unwrapped = unwrapTrims(ktHost) // put before TempInjections for side effects, because TempInjection could also be trim-indented

        val tempInjectedLanguage = TemporaryPlacesRegistry.getInstance(ktHost.project).getLanguageFor(languageInjectionHost, containingFile)
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
        val callFqn = callExpression.resolveToCall(BodyResolveMode.PARTIAL)?.candidateDescriptor?.fqNameOrNull()?.asString()
        if (callFqn == "kotlin.text.trimIndent") {
            ktHost.indentHandler = TrimIndentHandler()
            return dotQualifiedExpression
        }
        if (callFqn == "kotlin.text.trimMargin") {
            val marginChar = callExpression.valueArguments.getOrNull(0)?.getArgumentExpression().asSafely<KtStringTemplateExpression>()
                ?.entries?.singleOrNull()?.asSafely<KtLiteralStringTemplateEntry>()?.text ?: "|"
            ktHost.indentHandler = TrimIndentHandler(marginChar)
            return dotQualifiedExpression
        }
        return ktHost
    }


    private fun findInjectionInfo(place: KtElement, originalHost: Boolean = true): InjectionInfo? {
        return injectWithExplicitCodeInstruction(place)
            ?: injectWithCall(place)
            ?: injectReturnValue(place)
            ?: injectInAnnotationCall(place)
            ?: injectWithReceiver(place)
            ?: injectWithVariableUsage(place, originalHost)
            ?: injectWithMutation(place)
    }

    private val stringMutationOperators = listOf(KtTokens.EQ, KtTokens.PLUSEQ)
    private fun injectWithMutation(host: KtElement): InjectionInfo? {
        val parent = (host.parent as? KtBinaryExpression)?.takeIf { it.operationToken in stringMutationOperators } ?: return null
        if (parent.right != host) return null

        if (isAnalyzeOff(host.project)) return null

        val property = when (val left = parent.left) {
            is KtQualifiedExpression -> left.selectorExpression
            else -> left
        } ?: return null

        for (reference in property.references) {
            ProgressManager.checkCanceled()
            val resolvedTo = reference.resolve()
            if (resolvedTo is KtProperty) {
                val annotation = resolvedTo.findAnnotation(FqName(AnnotationUtil.LANGUAGE)) ?: return null
                return kotlinSupport?.toInjectionInfo(annotation)
            }
        }

        return null
    }

    private fun injectReturnValue(place: KtElement): InjectionInfo? {
        val parent = place.parent

        tailrec fun findReturnExpression(expression: PsiElement?): KtReturnExpression? = when (expression) {
            is KtReturnExpression -> expression
            is KtBinaryExpression -> findReturnExpression(expression.takeIf { it.operationToken == KtTokens.ELVIS }?.parent)
            is KtContainerNodeForControlStructureBody, is KtIfExpression -> findReturnExpression(expression.parent)
            else -> null
        }

        val returnExp = findReturnExpression(parent) ?: return null

        if (returnExp.labeledExpression != null) return null

        val callableDeclaration = PsiTreeUtil.getParentOfType(returnExp, KtDeclaration::class.java) as? KtCallableDeclaration ?: return null
        if (callableDeclaration.annotationEntries.isEmpty()) return null

        val descriptor = callableDeclaration.descriptor ?: return null
        return injectionInfoByAnnotation(descriptor)
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

        val kotlinInjections = Configuration.getProjectInstance(host.project).getInjections(KOTLIN_SUPPORT_ID)

        val calleeName = callee.text
        val possibleNames = collectPossibleNames(kotlinInjections)

        if (calleeName !in possibleNames) {
            return null
        }

        for (reference in callee.references) {
            ProgressManager.checkCanceled()

            val resolvedTo = reference.resolve()
            if (resolvedTo is KtFunction) {
                val injectionInfo = findInjection(resolvedTo.receiverTypeReference, kotlinInjections)
                if (injectionInfo != null) {
                    return injectionInfo
                }
            }
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
        return ReferencesSearch.search(ktProperty, searchScope).asSequence().mapNotNull { psiReference ->
            val element = psiReference.element as? KtElement ?: return@mapNotNull null
            findInjectionInfo(element, false)
        }.firstOrNull()
    }

    private tailrec fun injectWithCall(host: KtElement): InjectionInfo? {
        val argument = getArgument(host) ?: return null
        val callExpression = PsiTreeUtil.getParentOfType(argument, KtCallElement::class.java) ?: return null

        if (getCallableShortName(callExpression) == "arrayOf") return injectWithCall(callExpression)
        val callee = getNameReference(callExpression.calleeExpression) ?: return null

        if (isAnalyzeOff(host.project)) return null

        for (reference in callee.references) {
            ProgressManager.checkCanceled()

            val resolvedTo = allowResolveInDispatchThread { reference.resolve() }
            if (resolvedTo is PsiMethod) {
                val injectionForJavaMethod = injectionForJavaMethod(argument, resolvedTo)
                if (injectionForJavaMethod != null) {
                    return injectionForJavaMethod
                }
            } else if (resolvedTo is KtFunction) {
                val injectionForJavaMethod = injectionForKotlinCall(argument, resolvedTo, reference)
                if (injectionForJavaMethod != null) {
                    return injectionForJavaMethod
                }
            }
        }

        return null
    }

    private fun getNameReference(callee: KtExpression?): KtNameReferenceExpression? {
        if (callee is KtConstructorCalleeExpression)
            return callee.constructorReferenceExpression as? KtNameReferenceExpression
        return callee as? KtNameReferenceExpression
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
            allowResolveInDispatchThread { reference.resolve() }
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
        val argumentIndex = (argument.parent as KtValueArgumentList).arguments.indexOf(argument)
        // Prefer using argument name if present
        val ktParameter = if (argumentName != null) {
            ktFunction.valueParameters.firstOrNull { it.nameAsName == argumentName }
        } else {
            ktFunction.valueParameters.getOrNull(argumentIndex)
        } ?: return null
        val patternInjection = findInjection(ktParameter, Configuration.getProjectInstance(argument.project).getInjections(KOTLIN_SUPPORT_ID))
        if (patternInjection != null) {
            return patternInjection
        }

        // Found psi element after resolve can be obtained from compiled declaration but annotations parameters are lost there.
        // Search for original descriptor from reference.
        val ktReference = reference as? KtReference ?: return null
        val functionDescriptor = allowResolveInDispatchThread {
            val bindingContext = ktReference.element.analyze(BodyResolveMode.PARTIAL_WITH_DIAGNOSTICS)
            ktReference.resolveToDescriptors(bindingContext).singleOrNull() as? FunctionDescriptor
        } ?: return null

        val parameterDescriptor = if (argumentName != null) {
            functionDescriptor.valueParameters.firstOrNull { it.name == argumentName }
        } else {
            functionDescriptor.valueParameters.getOrNull(argumentIndex)
        } ?: return null
        return injectionInfoByAnnotation(parameterDescriptor)
    }

    private fun injectionInfoByAnnotation(annotated: Annotated): InjectionInfo? {
        val injectAnnotation = annotated.findAnnotation<LanguageAnnotation>() ?: return null
        val languageId = injectAnnotation.getStringValue(LanguageAnnotation::value) ?: return null
        val prefix = injectAnnotation.getStringValue(LanguageAnnotation::prefix)
        val suffix = injectAnnotation.getStringValue(LanguageAnnotation::suffix)
        return InjectionInfo(languageId, prefix, suffix)
    }

    private fun findInjection(element: PsiElement?, injections: List<BaseInjection>): InjectionInfo? {
        for (injection in injections) {
            if (injection.acceptsPsiElement(element)) {
                return InjectionInfo(injection.injectedLanguageId, injection.prefix, injection.suffix)
            }
        }

        return null
    }

    private fun isAnalyzeOff(project: Project): Boolean {
        return Configuration.getProjectInstance(project).advancedConfiguration.dfaOption == Configuration.DfaOption.OFF
    }

    private fun processAnnotationInjectionInner(annotations: Array<PsiAnnotation>): InjectionInfo {
        val id = AnnotationUtilEx.calcAnnotationValue(annotations, "value")
        val prefix = AnnotationUtilEx.calcAnnotationValue(annotations, "prefix")
        val suffix = AnnotationUtilEx.calcAnnotationValue(annotations, "suffix")

        return InjectionInfo(id, prefix, suffix)
    }

    private fun createCachedValue(project: Project): CachedValueProvider.Result<HashSet<String>> = with(Configuration.getProjectInstance(project)) {
        CachedValueProvider.Result.create(
            (getInjections(JavaLanguageInjectionSupport.JAVA_SUPPORT_ID) + getInjections(KOTLIN_SUPPORT_ID))
                .asSequence()
                .flatMap { it.injectionPlaces.asSequence() }
                .flatMap { retrieveJavaPlaceTargetClassesFQNs(it).asSequence() + retrieveKotlinPlaceTargetClassesFQNs(it).asSequence() }
                .map { StringUtilRt.getShortName(it) }
                .toHashSet(), this)
    }

    private fun getInjectableTargetClassShortNames(project: Project) = CachedValuesManager.getManager(project).createCachedValue({ createCachedValue(project) }, false)

    private fun fastCheckInjectionsExists(
        annotationShortName: String,
        project: Project
    ) = annotationShortName in getInjectableTargetClassShortNames(project).value

    private fun getCallableShortName(annotationEntry: KtCallElement): String? {
        val referencedName = getNameReference(annotationEntry.calleeExpression)?.getReferencedName() ?: return null
        return KotlinPsiHeuristics.unwrapImportAlias(annotationEntry.containingKtFile, referencedName).singleOrNull() ?: referencedName
    }

    private fun retrieveJavaPlaceTargetClassesFQNs(place: InjectionPlace): Collection<String> {
        val classCondition = place.elementPattern.condition.conditions.firstOrNull { it.debugMethodName == "definedInClass" }
                as? PatternConditionPlus<*, *> ?: return emptyList()
        val psiClassNamePatternCondition =
            classCondition.valuePattern.condition.conditions.firstIsInstanceOrNull<PsiClassNamePatternCondition>() ?: return emptyList()
        val valuePatternCondition =
            psiClassNamePatternCondition.namePattern.condition.conditions.firstIsInstanceOrNull<ValuePatternCondition<String>>()
                ?: return emptyList()
        return valuePatternCondition.values
    }

    private fun retrieveKotlinPlaceTargetClassesFQNs(place: InjectionPlace): Collection<String> {
        val classNames = SmartList<String>()
        fun collect(condition: PatternCondition<*>) {
            when (condition) {
                is PatternConditionPlus<*, *> -> condition.valuePattern.condition.conditions.forEach { collect(it) }
                is KotlinFunctionPattern.DefinedInClassCondition -> classNames.add(condition.fqName)
            }
        }
        place.elementPattern.condition.conditions.forEach { collect(it) }
        return classNames
    }

}

internal fun isSupportedElement(context: KtElement): Boolean {
    if (context.parent?.isConcatenationExpression() != false) return false // we will handle the top concatenation only, will not handle KtFile-s
    if (context is KtStringTemplateExpression && context.isValidHost) return true
    if (context.isConcatenationExpression()) return true
    return false
}

internal var KtElement.indentHandler: IndentHandler? by UserDataProperty(
    Key.create<IndentHandler>("KOTLIN_INDENT_HANDLER")
)
