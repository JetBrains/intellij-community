// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.SourcePosition
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.idea.debugger.breakpoints.getLambdasAtLineIfAny
import org.jetbrains.kotlin.idea.debugger.core.AnalysisApiBasedInlineUtil
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinDebuggerCaches.ComputedClassNames
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinDebuggerCaches.ComputedClassNames.Companion.Cached
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinDebuggerCaches.ComputedClassNames.Companion.EMPTY
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinDebuggerCaches.ComputedClassNames.Companion.NonCached
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.isObjectLiteral
import org.jetbrains.kotlin.psi.psiUtil.isTopLevelInFileOrScript
import java.util.*
import kotlin.collections.HashMap

class ClassNameProvider(val project: Project, val searchScope: GlobalSearchScope, val configuration: Configuration) {
    companion object {
        private val CLASS_ELEMENT_TYPES = arrayOf<Class<out PsiElement>>(
            KtScript::class.java,
            KtFile::class.java,
            KtClassOrObject::class.java,
            KtProperty::class.java,
            KtNamedFunction::class.java,
            KtFunctionLiteral::class.java,
            KtAnonymousInitializer::class.java
        )

        internal fun getRelevantElement(element: PsiElement?): PsiElement? {
            if (element == null) {
                return null
            }

            for (elementType in CLASS_ELEMENT_TYPES) {
                if (elementType.isInstance(element)) {
                    return element
                }
            }

            // Do not copy the array (*elementTypes) if the element is one we look for
            return PsiTreeUtil.getNonStrictParentOfType(element, *CLASS_ELEMENT_TYPES)
        }
    }

    data class Configuration(val findInlineUseSites: Boolean, val alwaysReturnLambdaParentClass: Boolean) {
        companion object {
            val DEFAULT = Configuration(findInlineUseSites = true, alwaysReturnLambdaParentClass = true)
        }
    }

    private val inlineUsagesSearcher = InlineCallableUsagesSearcher(project, searchScope)

    @RequiresReadLock
    fun getCandidates(position: SourcePosition): List<String> {
        val relevantElement = position.elementAt?.let { getRelevantElement(it) }

        val regularClassNames = if (relevantElement != null) getCandidatesForElementCached(relevantElement) else emptyList()
        val lambdaClassNames = getLambdasAtLineIfAny(position).flatMap { getCandidatesForElementCached(it) }
        return (regularClassNames + lambdaClassNames).distinct()
    }

    private fun getCandidatesForElementCached(element: PsiElement): List<String> {
        val cache = CachedValuesManager.getCachedValue(element) {
            val value = Collections.synchronizedMap(HashMap<Configuration, List<String>>())
            CachedValueProvider.Result(value, PsiModificationTracker.MODIFICATION_COUNT)
        }

        return cache.computeIfAbsent(configuration) {
            getOuterClassNamesForElement(element, emptySet()).classNames
        }
    }

    @PublishedApi
    @Suppress("NON_TAIL_RECURSIVE_CALL")
    internal tailrec fun getOuterClassNamesForElement(element: PsiElement?, alreadyVisited: Set<PsiElement>): ComputedClassNames {
        // 'alreadyVisited' is used in inline callable searcher to prevent infinite recursion.
        // In normal cases we only go from leaves to topmost parents upwards.

        if (element == null) return EMPTY

        return when (element) {
            is KtScript -> {
                ClassNameCalculator.getClassName(element)?.let { return Cached(it) }
                return EMPTY
            }
            is KtFile -> {
                val fileClassName = JvmFileClassUtil.getFileClassInternalName(element).toJdiName()
                Cached(fileClassName)
            }
            is KtClassOrObject -> {
                val enclosingElementForLocal = KtPsiUtil.getEnclosingElementForLocalDeclaration(element)
                when {
                    enclosingElementForLocal != null ->
                        // A local class
                        getOuterClassNamesForElement(enclosingElementForLocal, alreadyVisited)
                    element.isObjectLiteral() ->
                        getOuterClassNamesForElement(element.relevantParent, alreadyVisited)
                    else -> {
                        // Guaranteed to be non-local class or object
                        if (element is KtClass && element.isInterface()) {
                            val name = ClassNameCalculator.getClassName(element)

                            if (name != null)
                                Cached(listOf(name, name + JvmAbi.DEFAULT_IMPLS_SUFFIX))
                            else
                                EMPTY
                        } else {
                            ClassNameCalculator.getClassName(element)?.let { Cached(it) } ?: EMPTY
                        }
                    }
                }
            }
            is KtProperty -> {
                val nonInlineClasses = if (isTopLevelInFileOrScript(element)) {
                    // Top level property
                    getOuterClassNamesForElement(element.relevantParent, alreadyVisited)
                } else {
                    val enclosingElementForLocal = KtPsiUtil.getEnclosingElementForLocalDeclaration(element)
                    if (enclosingElementForLocal != null) {
                        // Local class
                        getOuterClassNamesForElement(enclosingElementForLocal, alreadyVisited)
                    } else {
                        val containingClassOrFile = PsiTreeUtil.getParentOfType(element, KtFile::class.java, KtClassOrObject::class.java)

                        if (containingClassOrFile is KtObjectDeclaration && containingClassOrFile.isCompanion()) {
                            // Properties from the companion object can be placed in the companion object's containing class
                            (getOuterClassNamesForElement(containingClassOrFile.relevantParent, alreadyVisited) +
                                    getOuterClassNamesForElement(containingClassOrFile, alreadyVisited)).distinct()
                        } else if (containingClassOrFile != null) {
                            getOuterClassNamesForElement(containingClassOrFile, alreadyVisited)
                        } else {
                            getOuterClassNamesForElement(element.relevantParent, alreadyVisited)
                        }
                    }
                }

                if (configuration.findInlineUseSites && element.hasInlineAccessors) {
                    val inlinedCalls = inlineUsagesSearcher.findInlinedCalls(element, alreadyVisited) { el, newAlreadyVisited ->
                        this.getOuterClassNamesForElement(el, newAlreadyVisited)
                    }
                    nonInlineClasses + inlinedCalls
                } else {
                    return NonCached(nonInlineClasses.classNames)
                }
            }
            is KtNamedFunction -> {
                val classNamesOfContainingDeclaration = getOuterClassNamesForElement(element.relevantParent, alreadyVisited)

                var nonInlineClasses: ComputedClassNames = classNamesOfContainingDeclaration

                if (element.name == null || element.isLocal) {
                    val nameOfAnonymousClass = ClassNameCalculator.getClassName(element)
                    if (nameOfAnonymousClass != null) {
                        nonInlineClasses += Cached(nameOfAnonymousClass)
                    }
                }

                if (!configuration.findInlineUseSites || !element.hasModifier(KtTokens.INLINE_KEYWORD)) {
                    return NonCached(nonInlineClasses.classNames)
                }

                val inlineCallSiteClasses = inlineUsagesSearcher.findInlinedCalls(element, alreadyVisited) { el, newAlreadyVisited ->
                    this.getOuterClassNamesForElement(el, newAlreadyVisited)
                }

                nonInlineClasses + inlineCallSiteClasses
            }
            is KtAnonymousInitializer -> {
                val initializerOwner = element.containingDeclaration

                if (initializerOwner is KtObjectDeclaration && initializerOwner.isCompanion()) {
                    val containingClass = initializerOwner.containingClassOrObject
                    return getOuterClassNamesForElement(containingClass, alreadyVisited)
                }

                getOuterClassNamesForElement(initializerOwner, alreadyVisited)
            }
            is KtCallableReferenceExpression ->
                getNamesForLambda(element, alreadyVisited)
            is KtFunctionLiteral ->
                getNamesForLambda(element, alreadyVisited)
            else ->
                getOuterClassNamesForElement(element.relevantParent, alreadyVisited)
        }
    }

    private fun getNamesForLambda(element: KtElement, alreadyVisited: Set<PsiElement>): ComputedClassNames {
        val name = ClassNameCalculator.getClassName(element) ?: return EMPTY
        val names = Cached(name)

        if (!names.isEmpty() && !configuration.alwaysReturnLambdaParentClass) {
            if (element !is KtFunctionLiteral || !AnalysisApiBasedInlineUtil.isInlinedArgument(element, true)) {
                return names
            }
        }

        return names + getOuterClassNamesForElement(element.relevantParent, alreadyVisited)
    }

    private val KtProperty.hasInlineAccessors: Boolean
        get() = hasModifier(KtTokens.INLINE_KEYWORD) || accessors.any { it.hasModifier(KtTokens.INLINE_KEYWORD) }

    private inline val PsiElement.relevantParent
        get() = getRelevantElement(this.parent)
}

private fun String.toJdiName() = replace('/', '.')
