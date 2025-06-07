// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.searching.usages.handlers

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector
import com.intellij.find.FindManager
import com.intellij.find.findUsages.AbstractFindUsagesDialog
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.find.impl.FindManagerImpl
import com.intellij.icons.AllIcons.Actions
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.LambdaUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.FunctionalExpressionSearch
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.usageView.UsageInfo
import com.intellij.util.*
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinCallableFindUsagesOptions
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinFindUsagesHandlerFactory
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinFunctionFindUsagesOptions
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinPropertyFindUsagesOptions
import org.jetbrains.kotlin.idea.base.searching.usages.dialogs.KotlinFindFunctionUsagesDialog
import org.jetbrains.kotlin.idea.base.searching.usages.dialogs.KotlinFindPropertyUsagesDialog
import org.jetbrains.kotlin.idea.base.util.excludeKotlinSources
import org.jetbrains.kotlin.idea.findUsages.KotlinFindUsagesSupport
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.dataClassComponentMethodName
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.filterDataClassComponentsIfDisabled
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.isOverridable
import org.jetbrains.kotlin.idea.search.declarationsSearch.toPossiblyFakeLightMethods
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReadWriteAccessDetector
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchOptions
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchParameters
import org.jetbrains.kotlin.idea.search.isImportUsage
import org.jetbrains.kotlin.idea.search.isOnlyKotlinSearch
import org.jetbrains.kotlin.idea.search.usagesSearch.buildProcessDelegationCallKotlinConstructorUsagesTask
import org.jetbrains.kotlin.idea.util.application.isHeadlessEnvironment
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.util.match
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

abstract class KotlinFindMemberUsagesHandler<T : KtNamedDeclaration> protected constructor(
    declaration: T,
    elementsToSearch: Collection<PsiElement>,
    factory: KotlinFindUsagesHandlerFactory
) : KotlinFindUsagesHandler<T>(declaration, elementsToSearch, factory) {

    private class Function(
        declaration: KtFunction,
        elementsToSearch: Collection<PsiElement>,
        factory: KotlinFindUsagesHandlerFactory
    ) : KotlinFindMemberUsagesHandler<KtFunction>(declaration, elementsToSearch, factory) {

        override fun getFindUsagesOptions(dataContext: DataContext?): FindUsagesOptions = factory.findFunctionOptions

        override fun getPrimaryElements(): Array<PsiElement> =
            if (factory.findFunctionOptions.isSearchForBaseMethod) {
                val supers = KotlinFindUsagesSupport.getSuperMethods(psiElement as KtFunction, null)
                if (supers.contains(psiElement)) supers.toTypedArray() else (supers + psiElement).toTypedArray()
            } else super.getPrimaryElements()

        override fun getFindUsagesDialog(
            isSingleFile: Boolean,
            toShowInNewTab: Boolean,
            mustOpenInNewTab: Boolean
        ): AbstractFindUsagesDialog {
            val options = factory.findFunctionOptions
            return KotlinFindFunctionUsagesDialog(getElement(), project, options, toShowInNewTab, mustOpenInNewTab, isSingleFile, this)
        }

        override fun createKotlinReferencesSearchOptions(options: FindUsagesOptions, forHighlight: Boolean): KotlinReferencesSearchOptions {
            val kotlinOptions = options as KotlinFunctionFindUsagesOptions
            return KotlinReferencesSearchOptions(
                acceptCallableOverrides = true,
                acceptOverloads = kotlinOptions.isIncludeOverloadUsages,
                acceptExtensionsOfDeclarationClass = kotlinOptions.isIncludeOverloadUsages,
                searchForExpectedUsages = kotlinOptions.searchExpected,
                searchForComponentConventions = !forHighlight
            )
        }

        override fun applyQueryFilters(
            element: PsiElement,
            options: FindUsagesOptions,
            fromHighlighting: Boolean,
            query: Query<PsiReference>
        ): Query<PsiReference> {
            val kotlinOptions = options as KotlinFunctionFindUsagesOptions
            return query
                .applyFilter(kotlinOptions.isSkipImportStatements) { !it.isImportUsage() }
                .applyFilter(!fromHighlighting) { it.element !is KtLabelReferenceExpression }
        }
    }

    private class Property(
        propertyDeclaration: KtNamedDeclaration,
        elementsToSearch: Collection<PsiElement>,
        factory: KotlinFindUsagesHandlerFactory
    ) : KotlinFindMemberUsagesHandler<KtNamedDeclaration>(propertyDeclaration, elementsToSearch, factory) {

        override fun processElementUsages(
            element: PsiElement,
            processor: Processor<in UsageInfo>,
            options: FindUsagesOptions
        ): Boolean {

            if (isUnitTestMode() ||
                !isPropertyOfDataClass ||
                psiElement.getDisableComponentAndDestructionSearch(resetSingleFind = false)
            ) return super.processElementUsages(element, processor, options)

            val indicator = ProgressManager.getInstance().progressIndicator

            val notificationCanceller = scheduleNotificationForDataClassComponent(project, element, indicator)
            try {
                return super.processElementUsages(element, processor, options)
            } finally {
                Disposer.dispose(notificationCanceller)
            }
        }

        private val isPropertyOfDataClass = true == runReadAction {
            propertyDeclaration.parents.match(KtParameterList::class, KtPrimaryConstructor::class, last = KtClass::class)?.isData()
        }

        override fun getPrimaryElements(): Array<PsiElement> {
            val element = psiElement as KtNamedDeclaration
            if (element is KtParameter && !element.hasValOrVar() && factory.findPropertyOptions.isSearchInOverridingMethods) {
                return ActionUtil.underModalProgress(project, KotlinBundle.message("find.usages.progress.text.declaration.superMethods")) { getPrimaryElementsUnderProgress(element) }
            } else if (factory.findPropertyOptions.isSearchForBaseAccessors) {
                val supers = KotlinFindUsagesSupport.getSuperMethods(element, null)
                return if (supers.contains(psiElement)) supers.toTypedArray() else (supers + psiElement).toTypedArray()
            }

            return super.getPrimaryElements()
        }

        private fun getPrimaryElementsUnderProgress(element: KtParameter): Array<PsiElement> {
            val function = element.ownerFunction
            if (function != null && function.isOverridable()) {
                val parameterIndex = element.parameterIndex()
                val offset = if ((function as? KtFunction)?.receiverTypeReference != null) 1 else 0
                return super.getPrimaryElements() + KotlinFindUsagesSupport.searchOverriders(function, function.useScope)
                    .mapNotNull { overrider ->
                        when (overrider) {
                            is KtNamedFunction -> overrider.valueParameters[parameterIndex]
                            is PsiMethod -> {
                                overrider.parameterList.takeIf { it.parametersCount > parameterIndex + offset }?.getParameter(parameterIndex + offset)
                            }
                            else -> null
                        }
                    }
                    .toList()
                    .toTypedArray()
            }
            return super.getPrimaryElements()
        }

        override fun getFindUsagesOptions(dataContext: DataContext?): FindUsagesOptions = factory.findPropertyOptions

        override fun getFindUsagesDialog(
            isSingleFile: Boolean,
            toShowInNewTab: Boolean,
            mustOpenInNewTab: Boolean
        ): AbstractFindUsagesDialog {
            return KotlinFindPropertyUsagesDialog(
                getElement(),
                project,
                factory.findPropertyOptions,
                toShowInNewTab,
                mustOpenInNewTab,
                isSingleFile,
                this
            )
        }

        override fun applyQueryFilters(
            element: PsiElement,
            options: FindUsagesOptions,
            fromHighlighting: Boolean,
            query: Query<PsiReference>
        ): Query<PsiReference> {
            val kotlinOptions = options as KotlinPropertyFindUsagesOptions

            if (!kotlinOptions.isReadAccess && !kotlinOptions.isWriteAccess) {
                return EmptyQuery()
            }

            val result = query.applyFilter(kotlinOptions.isSkipImportStatements) { !it.isImportUsage() }

            if (!kotlinOptions.isReadAccess || !kotlinOptions.isWriteAccess) {
                val detector = KotlinReadWriteAccessDetector()

                return FilteredQuery(result) {
                    when (detector.getReferenceAccess(element, it)) {
                        ReadWriteAccessDetector.Access.Read -> kotlinOptions.isReadAccess
                        ReadWriteAccessDetector.Access.Write -> kotlinOptions.isWriteAccess
                        ReadWriteAccessDetector.Access.ReadWrite -> kotlinOptions.isReadWriteAccess
                    }
                }
            }
            return result
        }

        private fun PsiElement.getDisableComponentAndDestructionSearch(resetSingleFind: Boolean): Boolean {

            if (!isPropertyOfDataClass) return false

            if (forceDisableComponentAndDestructionSearch) return true

            if (isHeadlessEnvironment() && !isUnitTestMode()) return true
            if (KotlinFindPropertyUsagesDialog.getDisableComponentAndDestructionSearch(project)) return true

            return if (getUserData(FIND_USAGES_ONES_FOR_DATA_CLASS_KEY) == true) {
                if (resetSingleFind) {
                    putUserData(FIND_USAGES_ONES_FOR_DATA_CLASS_KEY, null)
                }
                true
            } else false
        }


        override fun createKotlinReferencesSearchOptions(options: FindUsagesOptions, forHighlight: Boolean): KotlinReferencesSearchOptions {
            val kotlinOptions = options as KotlinPropertyFindUsagesOptions

            val disabledComponentsAndOperatorsSearch =
                !forHighlight && psiElement.getDisableComponentAndDestructionSearch(resetSingleFind = true)

            return KotlinReferencesSearchOptions(
                acceptCallableOverrides = true,
                acceptOverloads = false,
                acceptExtensionsOfDeclarationClass = false,
                searchForExpectedUsages = kotlinOptions.searchExpected,
                searchForOperatorConventions = !disabledComponentsAndOperatorsSearch,
                searchForComponentConventions = !disabledComponentsAndOperatorsSearch
            )
        }
    }

    override fun createSearcher(
        element: PsiElement,
        processor: Processor<in UsageInfo>,
        options: FindUsagesOptions
    ): Searcher {
        return MySearcher(element, processor, options)
    }

    private inner class MySearcher(
        element: PsiElement, processor: Processor<in UsageInfo>, options: FindUsagesOptions
    ) : Searcher(element, processor, options) {

        private val kotlinOptions = options as KotlinCallableFindUsagesOptions

        override fun buildTaskList(forHighlight: Boolean): Boolean {
            val referenceProcessor = createReferenceProcessor(processor)
            val uniqueProcessor = CommonProcessors.UniqueProcessor(processor)

            if (options.isUsages) {
                val baseKotlinSearchOptions = createKotlinReferencesSearchOptions(options, forHighlight)
                var kotlinSearchOptions = if (element is KtNamedFunction && KotlinPsiHeuristics.isPossibleOperator(element)) {
                    baseKotlinSearchOptions
                } else {
                    baseKotlinSearchOptions.copy(searchForOperatorConventions = false)
                }
                if (element is KtDeclaration && element.isExpectDeclaration() && !kotlinOptions.searchExpected) {
                    kotlinSearchOptions = kotlinSearchOptions.copy(searchForExpectedUsages = true)
                }

                val searchParameters = KotlinReferencesSearchParameters(element, options.searchScope, kotlinOptions = kotlinSearchOptions)

                addTask { applyQueryFilters(element, options, forHighlight, ReferencesSearch.search(searchParameters)).forEach(referenceProcessor) }

                if (element is KtElement && !isOnlyKotlinSearch(options.searchScope)) {
                    val nonKotlinSources = options.searchScope.excludeKotlinSources(project)
                    val psiMethodScopeSearch = when {
                        element is KtParameter && element.dataClassComponentMethodName != null -> {
                            nonKotlinSources
                        }
                        else -> options.searchScope
                    }

                    for (psiMethod in element.toLightMethods().filterDataClassComponentsIfDisabled(kotlinSearchOptions)) {
                        addTask {
                            // function as property syntax when there is java super
                            val query = MethodReferencesSearch.search(psiMethod, psiMethodScopeSearch, true)
                            applyQueryFilters(
                                element,
                                options,
                                forHighlight,
                                query
                            ).forEach(referenceProcessor)
                        }
                    }

                    if (element is KtPrimaryConstructor) {
                        val containingClass = element.containingClass()
                        if (containingClass?.isAnnotation() == true) {
                            addTask {
                                val query = ReferencesSearch.search(containingClass, nonKotlinSources)
                                applyQueryFilters(
                                    element,
                                    options,
                                    forHighlight,
                                    query
                                ).forEach(referenceProcessor)
                            }
                        }
                    }
                }

                if (element is KtConstructor<*>) {
                    addTask(
                        element.buildProcessDelegationCallKotlinConstructorUsagesTask(options.searchScope) { callElement ->
                            callElement.calleeExpression?.let { callee ->
                                val reference = callee.mainReference
                                reference == null || referenceProcessor.process(reference) } != false
                        }
                    )
                }
            }


            if (kotlinOptions.searchOverrides) {
                addTask {
                    val overriders = KotlinFindUsagesSupport.searchOverriders(element, options.searchScope)
                    val processor: (PsiElement) -> Boolean = all@{
                        val element = runReadAction { it.takeIf { it.isValid }?.navigationElement } ?: return@all true
                        processUsage(uniqueProcessor, element)
                    }
                    if (!overriders.all(processor)) {
                        false
                    } else {
                        val psiClass = runReadAction {
                            when (element) {
                                is KtNamedFunction -> element.toPossiblyFakeLightMethods().singleOrNull()
                                else -> null
                            }?.containingClass?.takeIf { LambdaUtil.isFunctionalClass(it) }
                        }

                        if (psiClass != null) {
                            FunctionalExpressionSearch.search(psiClass, options.searchScope).asIterable().all(processor)
                        } else {
                            true
                        }
                    }
                }
            }

            return true
        }
    }

    protected abstract fun createKotlinReferencesSearchOptions(
        options: FindUsagesOptions,
        forHighlight: Boolean
    ): KotlinReferencesSearchOptions

    protected abstract fun applyQueryFilters(
        element: PsiElement,
        options: FindUsagesOptions,
        fromHighlighting: Boolean,
        query: Query<PsiReference>
    ): Query<PsiReference>

    protected override fun isSearchForTextOccurrencesAvailable(psiElement: PsiElement, isSingleFile: Boolean): Boolean =
        !isSingleFile && psiElement !is KtParameter

    override fun findReferencesToHighlight(target: PsiElement, searchScope: SearchScope): Collection<PsiReference> {

        val baseDeclarations = KotlinSearchUsagesSupport.SearchUtils.findDeepestSuperMethodsNoWrapping(target)

        return if (baseDeclarations.isNotEmpty()) {
            baseDeclarations.flatMap {
                val handler = (FindManager.getInstance(project) as FindManagerImpl).findUsagesManager.getFindUsagesHandler(it, true)
                handler?.findReferencesToHighlight(it, searchScope) ?: emptyList()
            }
        } else {
            super.findReferencesToHighlight(target, searchScope)
        }
    }

    companion object {

        @Volatile
        @get:TestOnly
        var forceDisableComponentAndDestructionSearch = false


        private const val DISABLE_ONCE = "DISABLE_ONCE"
        private const val DISABLE = "DISABLE"
        private val DISABLE_COMPONENT_AND_DESTRUCTION_SEARCH_TEXT
            @Nls
            get() = KotlinBundle.message(
                "find.usages.text.find.usages.for.data.class.components.and.destruction.declarations",
                DISABLE_ONCE,
                DISABLE
            )

        private const val DISABLE_COMPONENT_AND_DESTRUCTION_SEARCH_TIMEOUT = 5000

        private val FIND_USAGES_ONES_FOR_DATA_CLASS_KEY = Key<Boolean>("FIND_USAGES_ONES")

        private fun scheduleNotificationForDataClassComponent(
            project: Project,
            element: PsiElement,
            indicator: ProgressIndicator
        ): Disposable {
            val notification = {
                val listener = HyperlinkListener { event ->
                    if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                        indicator.cancel()
                        if (event.description == DISABLE) {
                            KotlinFindPropertyUsagesDialog.setDisableComponentAndDestructionSearch(project, /* value = */ true)
                        } else {
                            element.putUserData(FIND_USAGES_ONES_FOR_DATA_CLASS_KEY, true)
                        }
                        FindManager.getInstance(project).findUsages(element)
                    }
                }

                val windowManager = ToolWindowManager.getInstance(project)
                windowManager.getToolWindow(ToolWindowId.FIND)?.let { toolWindow ->
                    windowManager.notifyByBalloon(
                        toolWindow.id,
                        MessageType.INFO,
                        DISABLE_COMPONENT_AND_DESTRUCTION_SEARCH_TEXT,
                        Actions.Find,
                        listener
                    )
                }

                Unit
            }

            return Alarm().also {
                it.addRequest(notification, DISABLE_COMPONENT_AND_DESTRUCTION_SEARCH_TIMEOUT)
            }
        }

        fun getInstance(
            declaration: KtNamedDeclaration,
            elementsToSearch: Collection<PsiElement> = emptyList(),
            factory: KotlinFindUsagesHandlerFactory
        ): KotlinFindMemberUsagesHandler<out KtNamedDeclaration> {
            return if (declaration is KtFunction)
                Function(declaration, elementsToSearch, factory)
            else
                Property(declaration, elementsToSearch, factory)
        }
    }
}


fun Query<PsiReference>.applyFilter(flag: Boolean, condition: (PsiReference) -> Boolean): Query<PsiReference> {
    return if (flag) FilteredQuery(this, condition) else this
}
