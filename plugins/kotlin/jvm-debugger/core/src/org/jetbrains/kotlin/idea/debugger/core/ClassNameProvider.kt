// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core

import com.intellij.debugger.SourcePosition
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.base.util.caching.ConcurrentFactoryCache
import org.jetbrains.kotlin.idea.debugger.base.util.ClassNameCalculator
import org.jetbrains.kotlin.idea.debugger.core.breakpoints.getLambdasAtLineIfAny
import org.jetbrains.kotlin.idea.search.isImportUsage
import org.jetbrains.kotlin.idea.util.application.isDispatchThread
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypes
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import java.util.concurrent.ConcurrentHashMap

class ClassNameProvider(
    private val project: Project,
    private val searchScope: GlobalSearchScope,
    private val configuration: Configuration
) {
    data class Configuration(val findInlineUseSites: Boolean, val alwaysReturnLambdaParentClass: Boolean) {
        companion object {
            val DEFAULT = Configuration(findInlineUseSites = true, alwaysReturnLambdaParentClass = true)
        }
    }

    @RequiresReadLock
    fun getCandidates(position: SourcePosition): List<String> {
        val regularClassNames = position.elementAt?.let(::getCandidatesForElement) ?: emptyList()
        val lambdaClassNames = getLambdasAtLineIfAny(position).flatMap { getCandidatesForElement(it) }
        return (regularClassNames + lambdaClassNames).distinct()
    }

    fun getCandidatesForElement(element: PsiElement): List<String> {
        val cache = CachedValuesManager.getCachedValue(element) {
            val storage = ConcurrentHashMap<Configuration, List<String>>()
            CachedValueProvider.Result(ConcurrentFactoryCache(storage), PsiModificationTracker.MODIFICATION_COUNT)
        }

        return cache.get(configuration) {
            computeCandidatesForElement(element, emptySet())
        }
    }

    private fun computeCandidatesForElement(element: PsiElement, alreadyVisited: Set<PsiElement>): List<String> {
        // 'alreadyVisited' is used in inline callable searcher to prevent infinite recursion.
        // In normal cases we only go from leaves to topmost parents upwards.

        val result = ArrayList<String>()

        fun registerClassName(element: KtElement): String? {
            return ClassNameCalculator.getClassName(element)?.also { result += it }
        }

        var current = element

        while (true) {
            when (current) {
                is KtScript, is KtFile -> {
                    registerClassName(current as KtElement)
                    break
                }
                is KtClassOrObject -> {
                    val className = registerClassName(current)
                    if (className != null) {
                        if (current.isInterfaceClass()) {
                            result.add(className + JvmAbi.DEFAULT_IMPLS_SUFFIX)
                        }

                        // Continue searching if inside an object literal
                        break
                    }
                }
                is KtProperty -> {
                    if (configuration.findInlineUseSites && current.hasInlineAccessors) {
                        result += findInlinedCalls(current, alreadyVisited)
                    }

                    val propertyOwner = current.containingClassOrObject
                    if (propertyOwner is KtObjectDeclaration && propertyOwner.isCompanion()) {
                        // Companion object properties are stored as static properties of an enclosing class
                        current = propertyOwner.containingClassOrObject ?: break
                        continue
                    }
                }
                is KtNamedFunction -> {
                    if (configuration.findInlineUseSites && current.hasModifier(KtTokens.INLINE_KEYWORD)) {
                        result += findInlinedCalls(current, alreadyVisited)
                    }

                    if (current.isLocal) {
                        // In old JVM backend, local functions were generated as separate classes
                        registerClassName(current)
                    }
                }
                is KtAnonymousInitializer -> {
                    val initializerOwner = current.containingDeclaration
                    if (initializerOwner is KtObjectDeclaration && initializerOwner.isCompanion()) {
                        // Companion initializers are put into the '<clinit>' of a containing class
                        current = initializerOwner.containingClassOrObject ?: break
                        continue
                    }
                }
                is KtCallableReferenceExpression, is KtLambdaExpression -> {
                    val className = registerClassName(current as KtElement)
                    if (className != null && !configuration.alwaysReturnLambdaParentClass) {
                        break
                    }
                }
                is KtObjectLiteralExpression -> {
                    registerClassName(current)
                    /*
                        Here should be a 'break'.
                        However, in the old JVM BE, literals have prefix with '$$inlined' and '$$special' with complex rules.
                        As it's considerably hard to support these mangling rules, and outer class is returned instead,
                        so 'KotlinPositionManager' can create a '<outer>$*' request.
                    */
                }
            }

            current = current.parent ?: break
        }

        return result
    }

    private fun PsiNamedElement.isInterfaceClass(): Boolean = when (this) {
        is KtClass -> isInterface()
        is PsiClass -> isInterface
        else -> false
    }

    private fun findInlinedCalls(declaration: KtDeclaration, alreadyVisited: Set<PsiElement>): List<String> {
        val searchResult = hashSetOf<PsiElement>()
        val declarationName = declaration.name ?: "<error>"

        val task = Runnable {
            for (reference in ReferencesSearch.search(declaration, getScopeForInlineDeclarationUsages(declaration))) {
                ProgressManager.checkCanceled()
                processInlinedReference(declaration, reference, alreadyVisited)?.let { searchResult += it }
            }
        }

        val isSuccess = if (isDispatchThread()) {
            val progressMessage = KotlinDebuggerCoreBundle.message("find.inline.calls.task.compute.names", declarationName)
            ProgressManager.getInstance().runProcessWithProgressSynchronously(task, progressMessage, true, project)
        } else {
            try {
                // We should not create new indicator when already running in a process,
                // as it will make the outer process not cancellable
                val currentIndicator = ProgressManager.getInstance().progressIndicator
                if (currentIndicator != null) {
                    task.run()
                } else {
                    ProgressManager.getInstance().runProcess(task, ProgressIndicatorBase())
                }
                true
            } catch (e: InterruptedException) {
                false
            }
        }

        if (!isSuccess) {
            val notificationMessage = KotlinDebuggerCoreBundle.message("find.inline.calls.task.cancelled", declarationName)
            XDebuggerManagerImpl.getNotificationGroup().createNotification(notificationMessage, MessageType.WARNING).notify(project)
        }

        val newAlreadyVisited = buildSet {
            addAll(alreadyVisited)
            addAll(searchResult)
            add(declaration)
        }

        return searchResult.flatMap { computeCandidatesForElement(it, newAlreadyVisited) }
    }

    private fun processInlinedReference(declaration: KtDeclaration, reference: PsiReference, alreadyVisited: Set<PsiElement>): PsiElement? {
        if (reference.isImportUsage() || reference.element.language != KotlinLanguage.INSTANCE) {
            return null
        }

        val element = getInlinedReferenceElement(reference)
        return if (!declaration.isAncestor(element) && element !in alreadyVisited) element else null
    }

    private fun getInlinedReferenceElement(reference: PsiReference): PsiElement {
        val element = reference.element

        /*
            The list must be consistent with 'computeCandidatesForElement()' implementation.
            * * *
            In theory, we might use 'reference.element' as is, but it's quite inefficient
            if there are multiple usages inside the same declaration.
        */
        return element.getParentOfTypes(
            strict = false,
            KtDeclaration::class.java,
            KtFile::class.java,
            KtScript::class.java,
            KtCallableReferenceExpression::class.java,
            KtLambdaExpression::class.java,
            KtObjectLiteralExpression::class.java
        ) ?: element
    }

    private fun getScopeForInlineDeclarationUsages(inlineDeclaration: KtDeclaration): GlobalSearchScope {
        val virtualFile = inlineDeclaration.containingFile.virtualFile
        return if (virtualFile != null && RootKindFilter.libraryFiles.matches(project, virtualFile)) {
            searchScope.uniteWith(KotlinSourceFilterScope.librarySources(GlobalSearchScope.allScope(project), project))
        } else {
            searchScope
        }
    }

    private val KtProperty.hasInlineAccessors: Boolean
        get() = hasModifier(KtTokens.INLINE_KEYWORD) || accessors.any { it.hasModifier(KtTokens.INLINE_KEYWORD) }
}