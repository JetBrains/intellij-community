// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core

import com.intellij.debugger.SourcePosition
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.util.caching.ConcurrentFactoryCache
import org.jetbrains.kotlin.idea.debugger.base.util.ClassNameCalculator
import org.jetbrains.kotlin.idea.debugger.core.breakpoints.getLambdasAtLineIfAny
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import java.util.concurrent.ConcurrentHashMap

class ClassNameProvider(private val configuration: Configuration = Configuration.DEFAULT) {
    // findInlineUseSites property preserved for API compatibility, actually not used
    data class Configuration internal constructor(val findInlineUseSites: Boolean = false, val alwaysReturnLambdaParentClass: Boolean) {
        companion object {
            val DEFAULT = Configuration(alwaysReturnLambdaParentClass = true)
            val STOP_AT_LAMBDA = Configuration(alwaysReturnLambdaParentClass = false)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    @Deprecated("Use primary constructor}", replaceWith = ReplaceWith("ClassNameProvider(configuration)"))
    constructor(project: Project, searchScope: GlobalSearchScope, configuration: Configuration) : this(configuration)

    fun getCandidates(position: SourcePosition): List<String> = getCandidatesInfo(position).map { it.name }

    @ApiStatus.Internal
    @RequiresReadLock
    fun getCandidatesInfo(position: SourcePosition): List<ClassNameCandidateInfo> {
        val regularClassNames = position.elementAt?.let(::getCandidatesForElementInternal) ?: emptyList()
        val lambdaClassNames = getLambdasAtLineIfAny(position).flatMap(::getCandidatesForElementInternal)
        return (regularClassNames + lambdaClassNames).distinct()
    }

    fun getCandidatesForElement(element: PsiElement): List<String> = getCandidatesForElementInternal(element).map { it.name }
    private fun getCandidatesForElementInternal(element: PsiElement): List<ClassNameCandidateInfo> {
        val cache = CachedValuesManager.getCachedValue(element) {
            val storage = ConcurrentHashMap<Configuration, List<ClassNameCandidateInfo>>()
            CachedValueProvider.Result(ConcurrentFactoryCache(storage), PsiModificationTracker.MODIFICATION_COUNT)
        }

        return cache.get(configuration) {
            computeCandidatesForElement(element)
        }
    }

    private fun computeCandidatesForElement(element: PsiElement): List<ClassNameCandidateInfo> {
        val result = ArrayList<ClassNameCandidateInfo>()
        var hasInlineElements = false

        fun registerClassName(name: String) {
            result.add(ClassNameCandidateInfo(name, hasInlineElements))
        }

        fun registerClassName(element: KtElement): String? {
            return ClassNameCalculator.getClassName(element)?.also { registerClassName(it) }
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
                            registerClassName(className + JvmAbi.DEFAULT_IMPLS_SUFFIX)
                        }

                        if (current !is KtEnumEntry) {
                            // Continue searching if inside an object literal
                            break
                        }
                    }
                }
                is KtProperty -> {
                    if (current.hasInlineAccessors) {
                        hasInlineElements = true
                    }

                    val propertyOwner = current.containingClassOrObject
                    if (propertyOwner is KtObjectDeclaration && propertyOwner.isCompanion()) {
                        // Companion object properties are stored as static properties of an enclosing class
                        current = propertyOwner.containingClassOrObject ?: break
                        continue
                    }
                }
                is KtNamedFunction -> {
                    if (current.hasModifier(KtTokens.INLINE_KEYWORD)) {
                        hasInlineElements = true
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

    private val KtProperty.hasInlineAccessors: Boolean
        get() = hasModifier(KtTokens.INLINE_KEYWORD) || accessors.any { it.hasModifier(KtTokens.INLINE_KEYWORD) }

    /**
     * @param name candidate calculated FQN
     * @param hasInlineElements marks that this class has inline elements,
     * so its code may be inlined to other classes
     */
    @ApiStatus.Internal
    data class ClassNameCandidateInfo(val name: String, val hasInlineElements: Boolean)
}
