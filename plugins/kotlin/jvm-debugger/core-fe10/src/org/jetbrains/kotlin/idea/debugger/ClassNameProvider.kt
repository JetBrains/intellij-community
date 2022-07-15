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
import org.jetbrains.kotlin.idea.debugger.breakpoints.getLambdasAtLineIfAny
import org.jetbrains.kotlin.idea.refactoring.isInterfaceClass
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import java.util.*
import kotlin.collections.ArrayList
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
        val regularClassNames = position.elementAt?.let(::getCandidatesForElement) ?: emptyList()
        val lambdaClassNames = getLambdasAtLineIfAny(position).flatMap { getCandidatesForElement(it) }
        return (regularClassNames + lambdaClassNames).distinct()
    }

    fun getCandidatesForElement(element: PsiElement): List<String> {
        val cache = CachedValuesManager.getCachedValue(element) {
            val value = Collections.synchronizedMap(HashMap<Configuration, List<String>>())
            CachedValueProvider.Result(value, PsiModificationTracker.MODIFICATION_COUNT)
        }

        return cache.computeIfAbsent(configuration) {
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
                        result += inlineUsagesSearcher.findInlinedCalls(current, alreadyVisited, ::computeCandidatesForElement)
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
                        result += inlineUsagesSearcher.findInlinedCalls(current, alreadyVisited, ::computeCandidatesForElement)
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

    private val KtProperty.hasInlineAccessors: Boolean
        get() = hasModifier(KtTokens.INLINE_KEYWORD) || accessors.any { it.hasModifier(KtTokens.INLINE_KEYWORD) }

    private inline val PsiElement.relevantParent
        get() = getRelevantElement(this.parent)
}