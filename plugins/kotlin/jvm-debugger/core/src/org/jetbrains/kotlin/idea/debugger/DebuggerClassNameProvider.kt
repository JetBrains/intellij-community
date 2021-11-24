// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.SourcePosition
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.debugger.breakpoints.getLambdasAtLineIfAny
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinDebuggerCaches.Companion.getOrComputeClassNames
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinDebuggerCaches.ComputedClassNames
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinDebuggerCaches.ComputedClassNames.Companion.Cached
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinDebuggerCaches.ComputedClassNames.Companion.EMPTY
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinDebuggerCaches.ComputedClassNames.Companion.NonCached
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.isObjectLiteral
import org.jetbrains.kotlin.psi.psiUtil.isTopLevelInFileOrScript
import org.jetbrains.kotlin.resolve.inline.InlineUtil

class DebuggerClassNameProvider(
    val project: Project, val searchScope: GlobalSearchScope,
    val findInlineUseSites: Boolean = true,
    val alwaysReturnLambdaParentClass: Boolean = true
) {
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
            return runReadAction { PsiTreeUtil.getNonStrictParentOfType(element, *CLASS_ELEMENT_TYPES) }
        }
    }

    private val inlineUsagesSearcher = InlineCallableUsagesSearcher(project, searchScope)

    /**
     * Returns classes in which the given line number *is* present.
     */
    fun getClassesForPosition(position: SourcePosition): Set<String> {
        return doGetClassesForPosition(position)
    }

    /**
     * Returns classes names in JDI format (my.app.App$Nested) in which the given line number *may be* present.
     */
    fun getOuterClassNamesForPosition(position: SourcePosition): List<String> {
        return doGetClassesForPosition(position).toList()
    }

    private fun doGetClassesForPosition(position: SourcePosition): Set<String> {
        val relevantElement = runReadAction {
            position.elementAt?.let { getRelevantElement(it) }
        }

        val result = getOrComputeClassNames(relevantElement) { element ->
            getOuterClassNamesForElement(element, emptySet())
        }.toMutableSet()

        for (lambda in runReadAction { getLambdasAtLineIfAny(position) }) {
            result += getOrComputeClassNames(lambda) { element ->
                getOuterClassNamesForElement(element, emptySet())
            }
        }

        return result
    }

    @PublishedApi
    @Suppress("NON_TAIL_RECURSIVE_CALL")
    internal tailrec fun getOuterClassNamesForElement(element: PsiElement?, alreadyVisited: Set<PsiElement>): ComputedClassNames {
        // 'alreadyVisited' is used in inline callable searcher to prevent infinite recursion.
        // In normal cases we only go from leaves to topmost parents upwards.

        if (element == null) return EMPTY

        return when (element) {
            is KtScript -> {
                ClassNameCalculator.getClassNameCompat(element)?.let { return Cached(it) }
                return EMPTY
            }
            is KtFile -> {
                val fileClassName = runReadAction { JvmFileClassUtil.getFileClassInternalName(element) }.toJdiName()
                Cached(fileClassName)
            }
            is KtClassOrObject -> {
                val enclosingElementForLocal = runReadAction { KtPsiUtil.getEnclosingElementForLocalDeclaration(element) }
                when {
                    enclosingElementForLocal != null ->
                        // A local class
                        getOuterClassNamesForElement(enclosingElementForLocal, alreadyVisited)
                    runReadAction { element.isObjectLiteral() } ->
                        getOuterClassNamesForElement(element.relevantParentInReadAction, alreadyVisited)
                    else ->
                        // Guaranteed to be non-local class or object
                        runReadAction {
                            if (element is KtClass && element.isInterface()) {
                                val name = ClassNameCalculator.getClassNameCompat(element)

                                if (name != null)
                                    Cached(listOf(name, name + JvmAbi.DEFAULT_IMPLS_SUFFIX))
                                else
                                    EMPTY
                            } else {
                              ClassNameCalculator.getClassNameCompat(element)?.let { Cached(it) } ?: EMPTY
                            }
                        }
                }
            }
            is KtProperty -> {
                val nonInlineClasses = if (runReadAction { isTopLevelInFileOrScript(element) }) {
                    // Top level property
                    getOuterClassNamesForElement(element.relevantParentInReadAction, alreadyVisited)
                } else {
                    val enclosingElementForLocal = runReadAction { KtPsiUtil.getEnclosingElementForLocalDeclaration(element) }
                    if (enclosingElementForLocal != null) {
                        // Local class
                        getOuterClassNamesForElement(enclosingElementForLocal, alreadyVisited)
                    } else {
                        val containingClassOrFile = runReadAction {
                            PsiTreeUtil.getParentOfType(element, KtFile::class.java, KtClassOrObject::class.java)
                        }

                        if (containingClassOrFile is KtObjectDeclaration && containingClassOrFile.isCompanionInReadAction) {
                            // Properties from the companion object can be placed in the companion object's containing class
                            (getOuterClassNamesForElement(containingClassOrFile.relevantParentInReadAction, alreadyVisited) +
                                    getOuterClassNamesForElement(containingClassOrFile, alreadyVisited)).distinct()
                        } else if (containingClassOrFile != null) {
                            getOuterClassNamesForElement(containingClassOrFile, alreadyVisited)
                        } else {
                            getOuterClassNamesForElement(element.relevantParentInReadAction, alreadyVisited)
                        }
                    }
                }

                if (findInlineUseSites && (
                            element.isInlineInReadAction ||
                                    runReadAction { element.accessors.any { it.hasModifier(KtTokens.INLINE_KEYWORD) } })
                ) {
                    val inlinedCalls = inlineUsagesSearcher.findInlinedCalls(element, alreadyVisited) { el, newAlreadyVisited ->
                        this.getOuterClassNamesForElement(el, newAlreadyVisited)
                    }
                    nonInlineClasses + inlinedCalls
                } else {
                    return NonCached(nonInlineClasses.classNames)
                }
            }
            is KtNamedFunction -> {
                val classNamesOfContainingDeclaration = getOuterClassNamesForElement(element.relevantParentInReadAction, alreadyVisited)

                var nonInlineClasses: ComputedClassNames = classNamesOfContainingDeclaration

                if (runReadAction { element.name == null || element.isLocal }) {
                    val nameOfAnonymousClass = runReadAction { ClassNameCalculator.getClassNameCompat(element) }
                    if (nameOfAnonymousClass != null) {
                        nonInlineClasses += Cached(nameOfAnonymousClass)
                    }
                }

                if (!findInlineUseSites || !element.isInlineInReadAction) {
                    return NonCached(nonInlineClasses.classNames)
                }

                val inlineCallSiteClasses = inlineUsagesSearcher.findInlinedCalls(element, alreadyVisited) { el, newAlreadyVisited ->
                    this.getOuterClassNamesForElement(el, newAlreadyVisited)
                }

                nonInlineClasses + inlineCallSiteClasses
            }
            is KtAnonymousInitializer -> {
                val initializerOwner = runReadAction { element.containingDeclaration }

                if (initializerOwner is KtObjectDeclaration && initializerOwner.isCompanionInReadAction) {
                    val containingClass = runReadAction { initializerOwner.containingClassOrObject }
                    return getOuterClassNamesForElement(containingClass, alreadyVisited)
                }

                getOuterClassNamesForElement(initializerOwner, alreadyVisited)
            }
            is KtCallableReferenceExpression ->
                getNamesForLambda(element, alreadyVisited)
            is KtFunctionLiteral ->
                getNamesForLambda(element, alreadyVisited)
            else ->
                getOuterClassNamesForElement(element.relevantParentInReadAction, alreadyVisited)
        }
    }

    private fun getNamesForLambda(element: KtElement, alreadyVisited: Set<PsiElement>): ComputedClassNames {
        val names = element.getNamesInReadAction()
        if (!names.isEmpty() && !alwaysReturnLambdaParentClass) {
            if (element !is KtFunctionLiteral || !element.isInlinedArgument()) {
                return names
            }
        }

        return names + getOuterClassNamesForElement(element.relevantParentInReadAction, alreadyVisited)
    }

    private fun KtFunction.isInlinedArgument() =
        InlineUtil.isInlinedArgument(this, analyze(), true)

    private fun KtElement.getNamesInReadAction() =
        runReadAction {
            val name = ClassNameCalculator.getClassNameCompat(this)
            if (name != null) Cached(name) else EMPTY
        }

    private val KtDeclaration.isInlineInReadAction: Boolean
        get() = runReadAction { hasModifier(KtTokens.INLINE_KEYWORD) }

    private val KtObjectDeclaration.isCompanionInReadAction: Boolean
        get() = runReadAction { isCompanion() }

    private val PsiElement.relevantParentInReadAction
        get() = runReadAction { getRelevantElement(this.parent) }
}

private fun String.toJdiName() = replace('/', '.')

