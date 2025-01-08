// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.move.moveInner.MoveInnerClassUsagesHandler
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.reformatted
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveOperationDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveSourceDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor.DeclarationTarget
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor.DeclarationTarget.DeclarationTargetType
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.usages.K2MoveRenameUsageInfo
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.usages.OuterInstanceReferenceUsageInfo
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.isIdentifier
import org.jetbrains.kotlin.types.Variance

class K2MoveNestedDeclarationsRefactoringProcessor(
    operationDescriptor: K2MoveOperationDescriptor.NestedDeclarations,
) : K2BaseMoveDeclarationsRefactoringProcessor<K2MoveOperationDescriptor.NestedDeclarations>(operationDescriptor) {
    private fun findInternalUsages(moveSource: K2MoveSourceDescriptor<*>): List<UsageInfo> {
        val declarationToMove = moveSource.elements.singleOrNull() as? KtNamedDeclaration ?: return emptyList()
        return collectOuterInstanceReferences(declarationToMove)
    }

    override fun getUsages(moveDescriptor: K2MoveDescriptor): List<UsageInfo> {
        return super.getUsages(moveDescriptor) + findInternalUsages(moveDescriptor.source)
    }

    private fun PsiElement.willLoseOuterInstanceReference(): Boolean {
        if (this !is KtDeclaration) return false
        val containingClassOrObject = containingClassOrObject ?: return false
        // For properties, any outer instance reference is a conflict because we do not process them.
        val canReferenceOuterInstance = (this is KtFunction || this is KtClass) &&
                operationDescriptor.outerInstanceParameterName != null
        // For anything else, it is a conflict if it is contained in a class rather than an object
        return !canReferenceOuterInstance && containingClassOrObject !is KtObjectDeclaration
    }

    override fun collectConflicts(
        moveDescriptor: K2MoveDescriptor,
        allUsages: MutableSet<UsageInfo>
    ) {
        val usageIterator = allUsages.iterator()
        while (usageIterator.hasNext()) {
            val usage = usageIterator.next()
            val element = usage.element ?: continue

            val isConflict = when (usage) {
                is K2MoveRenameUsageInfo.Light -> {
                    val referencedElement = usage.upToDateReferencedElement
                    if (referencedElement !is KtClassOrObject && operationDescriptor.outerInstanceParameterName != null) {
                        // We only have the facility to correct outer class usages if the moved declaration is a nested class.
                        conflicts.putValue(
                            element,
                            KotlinBundle.message("usages.of.nested.declarations.from.non.kotlin.code.won.t.be.processed", element.text)
                        )
                        true
                    } else {
                        false
                    }
                }

                is OuterInstanceReferenceUsageInfo -> {
                    val upToDateMember = usage.upToDateMember
                    if (upToDateMember != null && upToDateMember.willLoseOuterInstanceReference()) {
                        conflicts.putValue(
                            element,
                            KotlinBundle.message(
                                "usages.of.outer.class.instance.inside.declaration.0.won.t.be.processed",
                                upToDateMember.nameAsSafeName.asString()
                            )
                        )
                        true
                    } else {
                        usage.reportConflictIfAny(conflicts)
                    }
                }

                else -> false
            }
            if (isConflict) {
                usageIterator.remove()
            }
        }
    }

    override fun preprocessUsages(
        project: Project,
        moveSource: K2MoveSourceDescriptor<*>,
        usages: List<UsageInfo>
    ) {
        val outerInstanceParameterName = operationDescriptor.outerInstanceParameterName ?: return
        val psiFactory = KtPsiFactory(project)
        val newOuterInstanceRef = psiFactory.createExpression(quoteNameIfNeeded(outerInstanceParameterName))
        val declarationToMove = moveSource.elements.singleOrNull() as? KtNamedDeclaration

        for (usage in usages) {
            if (usage is MoveRenameUsageInfo) {
                val referencedNestedDeclaration = usage.referencedElement?.unwrapped as? KtNamedDeclaration
                if (declarationToMove != null && referencedNestedDeclaration == declarationToMove) {
                    val outerClass = referencedNestedDeclaration.containingClassOrObject
                    val lightOuterClass = outerClass?.toLightClass()
                    if (lightOuterClass != null) {
                        // While this is called the `MoveInnerClassUsagesHandler`, it will also correctly modify usages
                        // of inner methods from Kotlin code (but not from Java code!).
                        MoveInnerClassUsagesHandler.EP_NAME
                            .forLanguage(usage.element?.language ?: continue)
                            ?.correctInnerClassUsage(usage, lightOuterClass, outerInstanceParameterName)
                    }
                }
            }

            when (usage) {
                is OuterInstanceReferenceUsageInfo.ExplicitThis -> {
                    usage.expression.replace(newOuterInstanceRef)
                }

                is OuterInstanceReferenceUsageInfo.ImplicitReceiver -> {
                    usage.callElement.let {
                        it.replace(
                            psiFactory.createExpressionByPattern(
                                "$0.$1",
                                quoteNameIfNeeded(outerInstanceParameterName),
                                it
                            )
                        )
                    }
                }
            }
        }
    }

    private fun quoteNameIfNeeded(name: String): String {
        return if (name.isIdentifier()) {
            name
        } else {
            "`$name`"
        }
    }

    @OptIn(KaExperimentalApi::class)
    override fun preprocessDeclaration(
        moveDescriptor: K2MoveDescriptor,
        originalDeclaration: KtNamedDeclaration
    ) {
        val containingClass = originalDeclaration.containingClassOrObject
        val psiFactory = KtPsiFactory(originalDeclaration.project)
        val outerInstanceParameterName = operationDescriptor.outerInstanceParameterName
        with(originalDeclaration) {
            operationDescriptor.newClassName?.let { setName(it) }

            when (this) {
                is KtClass -> {
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
                            val possiblyQuotedName = quoteNameIfNeeded(outerInstanceParameterName)
                            val parameter = KtPsiFactory(project).createParameter("private val $possiblyQuotedName: $type")
                            createPrimaryConstructorParameterListIfAbsent().addParameter(parameter)
                        }
                    }
                }

                is KtNamedFunction -> {
                    if (outerInstanceParameterName != null) {
                        val outerInstanceType = analyze(originalDeclaration) {
                            val type = (containingClass?.symbol as? KaClassSymbol)?.defaultType ?: return@analyze null
                            type.render(KaTypeRendererForSource.WITH_QUALIFIED_NAMES, Variance.INVARIANT)
                        }
                        if (outerInstanceType == null) return
                        val possiblyQuotedName = quoteNameIfNeeded(outerInstanceParameterName)
                        valueParameterList?.addParameterBefore(
                            psiFactory.createParameter(
                                "${possiblyQuotedName}: $outerInstanceType"
                            ),
                            valueParameterList?.parameters?.firstOrNull()
                        )
                    }
                }

                is KtProperty -> {

                }
            }
        }
    }

    override fun postprocessDeclaration(
        moveTarget: K2MoveTargetDescriptor,
        originalDeclaration: PsiElement,
        newDeclaration: PsiElement
    ) {
        val outerInstanceParameterName = operationDescriptor.outerInstanceParameterName
        when (newDeclaration) {
            is KtClass -> {
                val primaryConstructor = newDeclaration.primaryConstructor ?: return
                val addedParameter = primaryConstructor.valueParameters.firstOrNull { it.name == outerInstanceParameterName } ?: return
                shortenReferences(addedParameter)
            }

            is KtNamedFunction -> {
                if (outerInstanceParameterName != null) {
                    val addedParameter = newDeclaration.valueParameterList?.parameters?.firstOrNull() ?: return
                    shortenReferences(addedParameter)
                }
                val moveTargetType = (moveTarget as? DeclarationTarget<*>)?.getTargetType()
                if (moveTargetType == DeclarationTargetType.COMPANION_OBJECT || moveTargetType == DeclarationTargetType.OBJECT) {
                    newDeclaration.removeModifier(KtTokens.OPEN_KEYWORD)
                    newDeclaration.removeModifier(KtTokens.FINAL_KEYWORD)
                    newDeclaration.reformatted(true)
                }
            }
        }
    }
}