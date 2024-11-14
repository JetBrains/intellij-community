// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.searching

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.patterns.StandardPatterns.string
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.asJava.ImpreciseResolveResult
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toPsiParameters
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.KotlinIconProvider.Companion.getBaseIcon
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.search.PsiBasedClassResolver
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import kotlin.contracts.contract

/**
 * Tests:
 * - [org.jetbrains.kotlin.search.AnnotatedMembersSearchTestGenerated]
 * - [org.jetbrains.kotlin.idea.fir.search.FirAnnotatedMembersSearchTestGenerated]
 */
class KotlinAnnotatedElementsSearcher : QueryExecutor<PsiModifierListOwner, AnnotatedElementsSearch.Parameters> {

    override fun execute(p: AnnotatedElementsSearch.Parameters, consumer: Processor<in PsiModifierListOwner>): Boolean {
        return processAnnotatedMembers(p.annotationClass, p.scope) { declaration, useSiteTarget ->
            when (declaration) {
                is KtClassOrObject -> {
                    val lightClass = declaration.toLightClass()
                    consumer.process(lightClass)
                }
                is KtNamedFunction, is KtConstructor<*> -> {
                    val wrappedMethod = LightClassUtil.getLightClassMethod(declaration as KtFunction)
                    consumer.process(wrappedMethod)
                }
                is KtProperty -> {
                    when (useSiteTarget?.getAnnotationUseSiteTarget()) {
                        AnnotationUseSiteTarget.PROPERTY_GETTER ->
                            return@processAnnotatedMembers LightClassUtil.getLightClassPropertyMethods(declaration).getter?.let { consumer.process(it) } != false
                        AnnotationUseSiteTarget.PROPERTY_SETTER ->
                            return@processAnnotatedMembers LightClassUtil.getLightClassPropertyMethods(declaration).setter?.let { consumer.process(it) } != false
                        AnnotationUseSiteTarget.FIELD ->
                            return@processAnnotatedMembers LightClassUtil.getLightClassBackingField(declaration)?.let { consumer.process(it) } != false
                        AnnotationUseSiteTarget.PROPERTY -> true // not visible to java
                        else -> {
                            val backingField = LightClassUtil.getLightClassBackingField(declaration)
                            if (backingField != null) {
                                return@processAnnotatedMembers consumer.process(backingField)
                            }

                            LightClassUtil.getLightClassPropertyMethods(declaration).all { consumer.process(it) }
                        }
                    }
                }
                is KtPropertyAccessor -> {
                    val method = LightClassUtil.getLightClassAccessorMethod(declaration)
                    return@processAnnotatedMembers consumer.process(method)
                }
                is KtParameter -> {
                    when (useSiteTarget?.getAnnotationUseSiteTarget()) {
                        AnnotationUseSiteTarget.CONSTRUCTOR_PARAMETER ->
                            return@processAnnotatedMembers declaration.toPsiParameters().all { consumer.process(it) }
                        AnnotationUseSiteTarget.PROPERTY_GETTER -> {
                            return@processAnnotatedMembers LightClassUtil.getLightClassPropertyMethods(declaration).getter?.let { consumer.process(it) } != false
                        }
                        AnnotationUseSiteTarget.PROPERTY_SETTER ->
                            return@processAnnotatedMembers LightClassUtil.getLightClassPropertyMethods(declaration).setter?.let { consumer.process(it) } != false
                        AnnotationUseSiteTarget.FIELD ->
                            return@processAnnotatedMembers LightClassUtil.getLightClassBackingField(declaration)?.let { consumer.process(it) } != false
                        else -> {
                            if (!declaration.toPsiParameters().all { consumer.process(it) }) return@processAnnotatedMembers false
                            LightClassUtil.getLightClassBackingField(declaration)?.let {
                                if (!consumer.process(it)) return@processAnnotatedMembers false
                            }
                            LightClassUtil.getLightClassPropertyMethods(declaration).all { consumer.process(it) }
                        }
                    }
                }
                else -> true
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance("#com.intellij.psi.impl.search.AnnotatedMembersSearcher")

        fun processAnnotatedMembers(
            annClass: PsiClass,
            useScope: SearchScope,
            preFilter: (KtAnnotationEntry) -> Boolean = { true },
            consumer: (KtDeclaration, KtAnnotationUseSiteTarget?) -> Boolean
        ): Boolean {
            assert(annClass.isAnnotationType) { "Annotation type should be passed to annotated members search" }

            val psiBasedClassResolver = PsiBasedClassResolver.getInstance(annClass)
            val annotationFQN = annClass.qualifiedName!!

            val candidates = getKotlinAnnotationCandidates(annClass, useScope)
            for (elt in candidates) {
                if (notKtAnnotationEntry(elt)) continue

                val result = runReadAction(fun(): Boolean {
                    if (!preFilter(elt)) return true

                    val declaration = elt.getStrictParentOfType<KtDeclaration>() ?: return true

                    val psiBasedResolveResult = elt.calleeExpression?.constructorReferenceExpression?.let { ref ->
                        elt.getBaseIcon()
                        psiBasedClassResolver.canBeTargetReference(ref)
                    }

                    if (psiBasedResolveResult == ImpreciseResolveResult.NO_MATCH) return true
                    if (psiBasedResolveResult == ImpreciseResolveResult.UNSURE) {
                        @OptIn(KaAllowAnalysisOnEdt::class)
                        allowAnalysisOnEdt {
                            @OptIn(KaAllowAnalysisFromWriteAction::class)
                            allowAnalysisFromWriteAction {
                                analyze(elt) {
                                    val annotationSymbol = elt.resolveToCall()?.singleConstructorCallOrNull()?.symbol
                                        ?: return false
                                    val annotationType = annotationSymbol.returnType as? KaClassType ?: return false
                                    val fqName = annotationType.classId.asFqNameString()
                                    if (fqName != annotationFQN) return true
                                }
                            }
                        }
                    }

                    if (!consumer(declaration, elt.useSiteTarget)) return false

                    return true
                })
                if (!result) return false
            }

            return true
        }

        /* Return all elements annotated with given annotation name. Aliases don't work now. */
        private fun getKotlinAnnotationCandidates(annClass: PsiClass, useScope: SearchScope): Collection<PsiElement> {
            return runReadAction(fun(): Collection<PsiElement> {
                if (useScope is GlobalSearchScope) {
                    val name = annClass.name ?: return emptyList()
                    val scope = KotlinSourceFilterScope.everything(useScope, annClass.project)
                    return KotlinAnnotationsIndex.get(name, annClass.project, scope)
                }

                return (useScope as LocalSearchScope).scope.flatMap { it.collectDescendantsOfType<KtAnnotationEntry>() }
            })
        }

        @OptIn(kotlin.contracts.ExperimentalContracts::class)
        private fun notKtAnnotationEntry(found: PsiElement): Boolean {
            contract {
                returns(false) implies (found is KtAnnotationEntry)
            }

            if (found is KtAnnotationEntry) return false

            val faultyContainer = PsiUtilCore.getVirtualFile(found)
            LOG.error("Non annotation in annotations list: $faultyContainer; element:$found")
            if (faultyContainer != null && faultyContainer.isValid) {
                FileBasedIndex.getInstance().requestReindex(faultyContainer)
            }

            return true
        }
    }

}
