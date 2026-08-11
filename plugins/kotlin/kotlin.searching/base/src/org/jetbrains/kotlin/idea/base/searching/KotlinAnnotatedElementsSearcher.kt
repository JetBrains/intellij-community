// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.searching

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.resolution.singleConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.asJava.ImpreciseResolveResult
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toPsiParameters
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.search.PsiBasedClassResolver
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtAnnotationUseSiteTarget
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
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
        val handler = handler@{ declaration: KtDeclaration, useSiteTarget: KtAnnotationUseSiteTarget? ->
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
                            return@handler LightClassUtil.getLightClassPropertyMethods(declaration).getter?.let { consumer.process(it) } != false
                        AnnotationUseSiteTarget.PROPERTY_SETTER ->
                            return@handler LightClassUtil.getLightClassPropertyMethods(declaration).setter?.let { consumer.process(it) } != false
                        AnnotationUseSiteTarget.FIELD ->
                            return@handler LightClassUtil.getLightClassBackingField(declaration)?.let { consumer.process(it) } != false
                        AnnotationUseSiteTarget.PROPERTY -> true // not visible to java
                        else -> {
                            val backingField = LightClassUtil.getLightClassBackingField(declaration)
                            if (backingField != null) {
                                return@handler consumer.process(backingField)
                            }

                            LightClassUtil.getLightClassPropertyMethods(declaration).all { consumer.process(it) }
                        }
                    }
                }
                is KtPropertyAccessor -> {
                    val method = LightClassUtil.getLightClassAccessorMethod(declaration)
                    return@handler consumer.process(method)
                }
                is KtParameter -> {
                    when (useSiteTarget?.getAnnotationUseSiteTarget()) {
                        AnnotationUseSiteTarget.CONSTRUCTOR_PARAMETER ->
                            return@handler declaration.toPsiParameters().all { consumer.process(it) }
                        AnnotationUseSiteTarget.PROPERTY_GETTER -> {
                            return@handler LightClassUtil.getLightClassPropertyMethods(declaration).getter?.let { consumer.process(it) } != false
                        }
                        AnnotationUseSiteTarget.PROPERTY_SETTER ->
                            return@handler LightClassUtil.getLightClassPropertyMethods(declaration).setter?.let { consumer.process(it) } != false
                        AnnotationUseSiteTarget.FIELD ->
                            return@handler LightClassUtil.getLightClassBackingField(declaration)?.let { consumer.process(it) } != false
                        else -> {
                            if (!declaration.toPsiParameters().all { consumer.process(it) }) return@handler false
                            LightClassUtil.getLightClassBackingField(declaration)?.let {
                                if (!consumer.process(it)) return@handler false
                            }
                            LightClassUtil.getLightClassPropertyMethods(declaration).all { consumer.process(it) }
                        }
                    }
                }
                else -> true
            }
        }

        val annClass = p.annotationClass
        return if (annClass != null) {
            processAnnotatedMembers(annClass, p.scope, consumer = handler)
        } else {
            processAnnotatedMembers(null, p.annotationName!!, p.project, p.scope, { true }, consumer = handler)
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
            return processAnnotatedMembers(annClass, null, null, useScope, preFilter, consumer)
        }

        private fun processAnnotatedMembers(
            annClass: PsiClass?,
            explicitFqn: String?,
            explicitProject: Project?,
            useScope: SearchScope,
            preFilter: (KtAnnotationEntry) -> Boolean,
            consumer: (KtDeclaration, KtAnnotationUseSiteTarget?) -> Boolean
        ): Boolean {
            val candidates = ReadAction.nonBlocking(fun(): Collection<SmartPsiElementPointer<KtAnnotationEntry>> {
                val annotationFQN = explicitFqn ?: annClass?.qualifiedName ?: return emptyList()
                val project = explicitProject ?: annClass?.project ?: return emptyList()
                val shortName = StringUtil.getShortName(annotationFQN)
                // Resolve-free fast path is only available when the search was set up with a concrete PsiClass.
                val psiBasedClassResolver = annClass?.let { PsiBasedClassResolver.getInstance(it) }

                val annotations = if (useScope is GlobalSearchScope) {
                    val scope = KotlinSourceFilterScope.everything(useScope, project)
                    val entries = KotlinAnnotationsIndex[shortName, project, scope]
                    entries.filterNot { notKtAnnotationEntry(it) }
                } else {
                    (useScope as LocalSearchScope).scope.flatMap { it.collectDescendantsOfType<KtAnnotationEntry>() }
                }

                val candidates = mutableListOf<SmartPsiElementPointer<KtAnnotationEntry>>()
                for (elt in annotations) {
                    fun acceptCandidateAnnotation(): Boolean {
                        if (!preFilter(elt)) return false
                        val psiBasedResolveResult = psiBasedClassResolver?.let { resolver ->
                            elt.calleeExpression?.constructorReferenceExpression?.let { ref ->
                                resolver.canBeTargetReference(ref)
                            }
                        } ?: ImpreciseResolveResult.UNSURE

                        if (psiBasedResolveResult == ImpreciseResolveResult.NO_MATCH) return false
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
                                        if (fqName != annotationFQN) return false
                                    }
                                }
                            }
                        }

                        return true
                    }

                    if (acceptCandidateAnnotation()) {
                        candidates.add(SmartPointerManager.createPointer(elt))
                    }
                }
                return candidates
            }).executeSynchronously()

            for (pt in candidates) {
                if (runReadActionBlocking {
                    val elt = pt.element
                    val declaration = elt?.getStrictParentOfType<KtDeclaration>()
                    declaration != null && !consumer(declaration, elt.useSiteTarget)
                }) {
                    return false
                }
            }

            return true
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
