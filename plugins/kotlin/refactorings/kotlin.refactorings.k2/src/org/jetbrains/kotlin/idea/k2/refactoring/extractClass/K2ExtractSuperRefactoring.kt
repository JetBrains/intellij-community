// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.extractClass

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.memberPullUp.PullUpProcessor
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.actions.createKotlinFileFromTemplate
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.util.quoteIfNeeded
import org.jetbrains.kotlin.idea.codeinsight.utils.isInterface
import org.jetbrains.kotlin.idea.core.getFqNameWithImplicitPrefix
import org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.ExtractSuperInfo
import org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.KotlinExtractInterfaceHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.KotlinExtractSuperRefactoring
import org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.KotlinExtractSuperclassHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.insertDeclaration
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.memberInfo.getChildrenToAnalyze
import org.jetbrains.kotlin.idea.refactoring.memberInfo.toJavaMemberInfo
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.addIfNotNull

internal class K2ExtractSuperRefactoring : KotlinExtractSuperRefactoring {
    override fun performRefactoring(extractInfo: ExtractSuperInfo) {
        val project = extractInfo.originalClass.project
        val originalClass = extractInfo.originalClass

        val handler = if (extractInfo.isInterface) KotlinExtractInterfaceHandler else KotlinExtractSuperclassHandler
        handler.getErrorMessage(originalClass)?.let { throw CommonRefactoringUtil.RefactoringErrorHintException(it) }

        val createClassInfo = ActionUtil.underModalProgress(
            project,
            RefactoringBundle.message("refactoring.prepare.progress"),
        ) {
            analyze(originalClass) {
                computeCreateClassInfo(extractInfo)
            }
        }

        project.executeWriteCommand(KotlinExtractSuperclassHandler.REFACTORING_NAME) {
            val newClass = createClass(extractInfo, createClassInfo) ?: return@executeWriteCommand

            val subClass = extractInfo.originalClass.toLightClass() ?: return@executeWriteCommand
            val superClass = newClass.toLightClass() ?: return@executeWriteCommand

            PullUpProcessor(
                /* sourceClass = */ subClass,
                /* targetSuperClass = */ superClass,
                /* membersToMove = */ extractInfo.memberInfos.mapNotNull { it.toJavaMemberInfo() }.toTypedArray(),
                /* javaDocPolicy = */ extractInfo.docPolicy
            ).moveMembersToBase()
        }
    }
}

private data class CreateClassInfo(
    val typeParameters: Set<KtTypeParameter>,
    val superClassEntry: KtSuperTypeListEntry?,
    val qualifiedTypeRefText: String?,
)

@OptIn(KaExperimentalApi::class)
private fun KaSession.computeCreateClassInfo(extractInfo: ExtractSuperInfo): CreateClassInfo {
    val originalClass = extractInfo.originalClass

    val typeParameters = collectTypeParameters(extractInfo)
    val superClassEntry = if (!extractInfo.isInterface) {
        val superKtClassSymbol = originalClass
            .classSymbol
            ?.superTypes
            ?.firstOrNull { !it.isInterface() && !it.isAnyType }
            ?.expandedSymbol

        originalClass.superTypeListEntries.firstOrNull { entry ->
            entry.typeReference?.type?.expandedSymbol == superKtClassSymbol
        }
    } else null

    val qualifiedTypeRefText = superClassEntry
        ?.typeReference
        ?.type
        ?.render(position = Variance.INVARIANT)

    return CreateClassInfo(
        typeParameters,
        superClassEntry,
        qualifiedTypeRefText,
    )
}

private fun collectTypeParameters(extractInfo: ExtractSuperInfo): Set<KtTypeParameter> {
    val original = extractInfo.originalClass
    val typeParams = linkedSetOf<KtTypeParameter>()

    val visitor = object : KtTreeVisitorVoid() {
        override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
            val refTarget = expression.mainReference.resolve()
            collectTypeParameters(extractInfo, refTarget, typeParams)
        }
    }

    getElementsToMove(extractInfo.memberInfos, original, extractInfo.isInterface)
        .asSequence()
        .flatMap { (element, info) -> info?.getChildrenToAnalyze()?.asSequence() ?: sequenceOf(element) }
        .forEach { it.accept(visitor) }

    return typeParams
}

private fun collectTypeParameters(
    extractInfo: ExtractSuperInfo,
    refTarget: PsiElement?,
    into: MutableSet<KtTypeParameter>,
) {
    if (refTarget !is KtTypeParameter) return
    if (refTarget.getStrictParentOfType<KtTypeParameterListOwner>() != extractInfo.originalClass) return

    if (into.add(refTarget)) {
        refTarget.accept(object : KtTreeVisitorVoid() {
            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                into.addIfNotNull(expression.mainReference.resolve() as? KtTypeParameter)
            }
        })
    }
}

private fun getElementsToMove(
    memberInfos: Collection<KotlinMemberInfo>,
    originalClass: KtClassOrObject,
    isExtractInterface: Boolean,
): Map<KtElement, KotlinMemberInfo?> {
    val elementsToMove = linkedMapOf<KtElement, KotlinMemberInfo?>()

    val superInterfacesToMove = buildSet {
        for (memberInfo in memberInfos) {
            val member = memberInfo.member ?: continue
            if (memberInfo.isSuperClass) add(member)
            else elementsToMove[member] = memberInfo
        }
    }

    analyze(originalClass) {
        for (superTypeListEntry in originalClass.superTypeListEntries) {
            val superType = superTypeListEntry.typeReference?.type ?: continue
            val superSymbol = superType.expandedSymbol ?: continue
            val superClass = superSymbol.psi as? KtClass ?: continue

            if ((!isExtractInterface && !superClass.isInterface()) || superClass in superInterfacesToMove) {
                elementsToMove[superTypeListEntry] = null
            }
        }
    }

    return elementsToMove
}

@OptIn(KaExperimentalApi::class)
private fun createClass(
    extractInfo: ExtractSuperInfo,
    info: CreateClassInfo,
): KtClass? {
    val (typeParameters, superClassEntry, qualifiedTypeRefText) = info
    val project = extractInfo.originalClass.project
    val targetParent = extractInfo.targetParent
    val newClassName = extractInfo.newClassName.quoteIfNeeded()
    val originalClass = extractInfo.originalClass
    val psiFactory = KtPsiFactory(project)

    val kind = if (extractInfo.isInterface) KtTokens.INTERFACE_KEYWORD else KtTokens.CLASS_KEYWORD
    val prototype = psiFactory.createClass("${kind.value} $newClassName")
    val newClass = if (targetParent is PsiDirectory) {
        val file = targetParent.findFile(extractInfo.targetFileName) ?: run {
            val template = FileTemplateManager.getInstance(project).getInternalTemplate("Kotlin File")
            createKotlinFileFromTemplate(extractInfo.targetFileName, template, targetParent) ?: return null
        }
        file.add(prototype) as KtClass
    } else {
        val targetSibling = originalClass.parentsWithSelf.first { it.parent == targetParent }
        insertDeclaration(prototype, targetSibling)
    }

    val shouldBeAbstract = extractInfo.memberInfos.any { it.isToAbstract }
    if (!extractInfo.isInterface) {
        newClass.addModifier(if (shouldBeAbstract) KtTokens.ABSTRACT_KEYWORD else KtTokens.OPEN_KEYWORD)
    }

    if (typeParameters.isNotEmpty()) {
        val typeParameterListText = typeParameters.sortedBy { it.startOffset }.joinToString(prefix = "<", postfix = ">") { it.text }
        newClass.addAfter(psiFactory.createTypeParameterList(typeParameterListText), newClass.nameIdentifier)
    }

    val targetPackageFqName = (targetParent as? PsiDirectory)
        ?.getFqNameWithImplicitPrefix()
        ?.quoteIfNeeded()
        ?.asString()

    val superTypeText = buildString {
        if (!targetPackageFqName.isNullOrEmpty()) append(targetPackageFqName).append('.')
        append(newClassName)
        if (typeParameters.isNotEmpty()) {
            append(typeParameters.sortedBy { it.startOffset }.mapNotNull { it.name }.joinToString(prefix = "<", postfix = ">"))
        }
    }

    val needSuperCall = !extractInfo.isInterface
            && (superClassEntry is KtSuperTypeCallEntry
            || originalClass.hasPrimaryConstructor()
            || originalClass.secondaryConstructors.isEmpty())

    val newSuperTypeListEntry = if (needSuperCall) {
        psiFactory.createSuperTypeCallEntry("$superTypeText()")
    } else {
        psiFactory.createSuperTypeEntry(superTypeText)
    }

    if (superClassEntry != null) {
        val entryToAdd = if (qualifiedTypeRefText != null) {
            superClassEntry.copied().apply {
                typeReference?.replace(psiFactory.createType(qualifiedTypeRefText))
            }
        } else {
            superClassEntry
        }
        newClass.addSuperTypeListEntry(entryToAdd)
        shortenReferences(superClassEntry.replaced(newSuperTypeListEntry))
    } else {
        shortenReferences(originalClass.addSuperTypeListEntry(newSuperTypeListEntry))
    }

    shortenReferences(newClass)
    return newClass
}
