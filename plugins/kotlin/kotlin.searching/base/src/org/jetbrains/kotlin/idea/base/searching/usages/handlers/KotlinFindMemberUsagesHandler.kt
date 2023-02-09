// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.searching.usages.handlers

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector
import com.intellij.find.FindManager
import com.intellij.find.findUsages.AbstractFindUsagesDialog
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.find.impl.FindManagerImpl
import com.intellij.icons.AllIcons.Actions
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.usageView.UsageInfo
import com.intellij.util.*
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.asJava.unwrapped
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
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.dataClassComponentMethodName
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.filterDataClassComponentsIfDisabled
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.isOverridable
import org.jetbrains.kotlin.idea.search.declarationsSearch.HierarchySearchRequest
import org.jetbrains.kotlin.idea.search.declarationsSearch.searchOverriders
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReadWriteAccessDetector
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchOptions
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchParameters
import org.jetbrains.kotlin.idea.search.isImportUsage
import org.jetbrains.kotlin.idea.search.isOnlyKotlinSearch
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.*
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
            val lightMethod = getElement().toLightMethods().firstOrNull()
            if (lightMethod != null) {
                return KotlinFindFunctionUsagesDialog(lightMethod, project, options, toShowInNewTab, mustOpenInNewTab, isSingleFile, this)
            }

            return super.getFindUsagesDialog(isSingleFile, toShowInNewTab, mustOpenInNewTab)
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

        override fun applyQueryFilters(element: PsiElement, options: FindUsagesOptions, query: Query<PsiReference>): Query<PsiReference> {
            val kotlinOptions = options as KotlinFunctionFindUsagesOptions
            return query
                .applyFilter(kotlinOptions.isSkipImportStatements) { !it.isImportUsage() }
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
                val function = element.ownerFunction
                if (function != null && function.isOverridable()) {
                    function.toLightMethods().singleOrNull()?.let { method ->
                        if (OverridingMethodsSearch.search(method).any()) {
                            val parametersCount = method.parameterList.parametersCount
                            val parameterIndex = element.parameterIndex()

                            assert(parameterIndex < parametersCount)
                            return OverridingMethodsSearch.search(method, true)
                                .filter { method.parameterList.parametersCount == parametersCount }
                                .mapNotNull { method.parameterList.parameters[parameterIndex].unwrapped }
                                .toTypedArray()
                        }
                    }
                }
            } else if (factory.findPropertyOptions.isSearchForBaseAccessors) {
                val supers = KotlinFindUsagesSupport.getSuperMethods(element, null)
                return if (supers.contains(psiElement)) supers.toTypedArray() else (supers + psiElement).toTypedArray()
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

        override fun applyQueryFilters(element: PsiElement, options: FindUsagesOptions, query: Query<PsiReference>): Query<PsiReference> {
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

            if (org.jetbrains.kotlin.idea.base.searching.usages.dialogs.KotlinFindPropertyUsagesDialog.getDisableComponentAndDestructionSearch(project)) return true

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
                val kotlinSearchOptions = if (element is KtNamedFunction && KotlinPsiHeuristics.isPossibleOperator(element)) {
                    baseKotlinSearchOptions
                } else {
                    baseKotlinSearchOptions.copy(searchForOperatorConventions = false)
                }

                val searchParameters = KotlinReferencesSearchParameters(element, options.searchScope, kotlinOptions = kotlinSearchOptions)

                addTask { applyQueryFilters(element, options, ReferencesSearch.search(searchParameters)).forEach(referenceProcessor) }

                if (element is KtElement && !isOnlyKotlinSearch(options.searchScope)) {
                    // TODO: very bad code!! ReferencesSearch does not work correctly for constructors and annotation parameters
                    val psiMethodScopeSearch = when {
                        element is KtParameter && element.dataClassComponentMethodName != null ->
                            options.searchScope.excludeKotlinSources(project)
                        else -> options.searchScope
                    }

                    for (psiMethod in element.toLightMethods().filterDataClassComponentsIfDisabled(kotlinSearchOptions)) {
                        addTask {
                            val query = MethodReferencesSearch.search(psiMethod, psiMethodScopeSearch, true)
                            applyQueryFilters(
                                element,
                                options,
                                query
                            ).forEach(referenceProcessor)
                        }
                    }
                }
            }

            if (kotlinOptions.searchOverrides) {
                addTask {
                    val overriders = HierarchySearchRequest(element, options.searchScope, true).searchOverriders()
                    overriders.all {
                        val element = runReadAction { it.takeIf { it.isValid }?.navigationElement } ?: return@all true
                        processUsage(uniqueProcessor, element)
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
        query: Query<PsiReference>
    ): Query<PsiReference>

    override fun isSearchForTextOccurrencesAvailable(psiElement: PsiElement, isSingleFile: Boolean): Boolean =
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
