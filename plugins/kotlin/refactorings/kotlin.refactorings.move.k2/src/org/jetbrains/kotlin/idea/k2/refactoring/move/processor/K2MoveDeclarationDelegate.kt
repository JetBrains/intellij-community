// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.move.moveInner.MoveInnerClassUsagesHandler
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveSourceDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.types.Variance

@ApiStatus.Internal
sealed interface K2MoveDeclarationDelegate {
    fun preprocessDeclaration(moveTarget: K2MoveTargetDescriptor, originalDeclaration: KtNamedDeclaration) {}
    fun preprocessUsages(project: Project, moveSource: K2MoveSourceDescriptor<*>, usages: List<UsageInfo>) {}
    fun findInternalUsages(moveSource: K2MoveSourceDescriptor<*>): List<UsageInfo> = emptyList()
    fun collectConflicts(moveTarget: K2MoveTargetDescriptor, internalUsages: MutableSet<UsageInfo>, conflicts: MultiMap<PsiElement, String>) {}
    fun postprocessDeclaration(moveTarget: K2MoveTargetDescriptor, originalDeclaration: PsiElement, newDeclaration: PsiElement) {}

    object TopLevel : K2MoveDeclarationDelegate {

    }

    class NestedClass(
        val newClassName: String? = null,
        private val outerInstanceParameterName: String? = null
    ) : K2MoveDeclarationDelegate {

        override fun preprocessUsages(
            project: Project,
            moveSource: K2MoveSourceDescriptor<*>,
            usages: List<UsageInfo>
        ) {
            if (outerInstanceParameterName == null) return
            val psiFactory = KtPsiFactory(project)
            val newOuterInstanceRef = psiFactory.createExpression(outerInstanceParameterName)
            val classToMove = moveSource.elements.singleOrNull() as? KtClass

            for (usage in usages) {
                if (usage is MoveRenameUsageInfo) {
                    val referencedNestedClass = usage.referencedElement?.unwrapped as? KtClassOrObject
                    if (referencedNestedClass == classToMove) {
                        val outerClass = referencedNestedClass?.containingClassOrObject
                        val lightOuterClass = outerClass?.toLightClass()
                        if (lightOuterClass != null) {
                            MoveInnerClassUsagesHandler.EP_NAME
                                .forLanguage(usage.element?.language ?: return)
                                ?.correctInnerClassUsage(usage, lightOuterClass, outerInstanceParameterName)
                        }
                    }
                }

                when (usage) {
                    is OuterInstanceReferenceUsageInfo.ExplicitThis -> {
                        usage.expression.replace(newOuterInstanceRef)
                    }

                    is OuterInstanceReferenceUsageInfo.ImplicitReceiver -> {
                        usage.callElement.let { it.replace(psiFactory.createExpressionByPattern("$0.$1", outerInstanceParameterName, it)) }
                    }
                }
            }
        }

        override fun collectConflicts(
            moveTarget: K2MoveTargetDescriptor,
            internalUsages: MutableSet<UsageInfo>,
            conflicts: MultiMap<PsiElement, String>
        ) {
            val usageIterator = internalUsages.iterator()
            while (usageIterator.hasNext()) {
                val usage = usageIterator.next()
                val element = usage.element ?: continue

                val isConflict = when (usage) {
                    is ImplicitCompanionAsDispatchReceiverUsageInfo -> {
                        val isValidTarget = isValidTargetForImplicitCompanionAsDispatchReceiver(moveTarget, usage.companionObject)
                        if (!isValidTarget) {
                            conflicts.putValue(
                                element,
                                KotlinBundle.message("text.implicit.companion.object.will.be.inaccessible.0", element.text)
                            )
                        }
                        true
                    }

                    is OuterInstanceReferenceUsageInfo -> usage.reportConflictIfAny(conflicts)

                    else -> false
                }
                if (isConflict) {
                    usageIterator.remove()
                }
            }
        }

        override fun findInternalUsages(moveSource: K2MoveSourceDescriptor<*>): List<UsageInfo> {
            val classToMove = moveSource.elements.singleOrNull() as? KtClass ?: return emptyList()
            return collectOuterInstanceReferences(classToMove)
        }

        @OptIn(KaExperimentalApi::class)
        override fun preprocessDeclaration(moveTarget: K2MoveTargetDescriptor, originalDeclaration: KtNamedDeclaration) {
            with(originalDeclaration) {
                newClassName?.let { setName(it) }

                if (this is KtClass) {
                    // TODO: Potentially allow for moving into classes
                    if (hasModifier(KtTokens.INNER_KEYWORD)) removeModifier(KtTokens.INNER_KEYWORD)
                    if (hasModifier(KtTokens.PROTECTED_KEYWORD)) removeModifier(KtTokens.PROTECTED_KEYWORD)

                    if (outerInstanceParameterName != null) {
                        val containingClass = containingClassOrObject ?: return
                        analyze(originalDeclaration) {
                            // Use the fully qualified type because we have not moved it to the new location yet
                            val type = containingClass.classSymbol?.defaultType?.render(
                                renderer = KaTypeRendererForSource.WITH_QUALIFIED_NAMES,
                                position = Variance.INVARIANT
                            ) ?: return
                            val parameter = KtPsiFactory(project).createParameter("private val $outerInstanceParameterName: $type")
                            createPrimaryConstructorParameterListIfAbsent().addParameter(parameter)
                        }
                    }
                }
            }
        }

        private fun shortenAddedOuterParameter(originalDeclaration: PsiElement, newDeclaration: PsiElement) {
            if (outerInstanceParameterName == null) return
            if (originalDeclaration !is KtClass || newDeclaration !is KtClass) return
            val primaryConstructor = newDeclaration.primaryConstructor ?: return
            val addedParameter = primaryConstructor.valueParameters.firstOrNull { it.name == outerInstanceParameterName } ?: return
            shortenReferences(addedParameter)
        }

        override fun postprocessDeclaration(
            moveTarget: K2MoveTargetDescriptor,
            originalDeclaration: PsiElement,
            newDeclaration: PsiElement
        ) {
            shortenAddedOuterParameter(originalDeclaration, newDeclaration)
        }
    }
}