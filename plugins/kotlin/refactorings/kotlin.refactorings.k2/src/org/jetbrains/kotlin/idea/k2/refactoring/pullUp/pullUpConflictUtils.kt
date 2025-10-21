// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.pullUp

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.renderer.declarations.bodies.KaRendererBodyMemberScopeProvider
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.KaDeclarationModifiersRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KaModifierListRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KaPropertyAccessorsRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KaValueParameterSymbolRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaDeclarationContainerSymbol
import org.jetbrains.kotlin.analysis.api.types.KaSubstitutor
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.useScope
import org.jetbrains.kotlin.idea.k2.refactoring.findCallableMemberBySignature
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.conflict.isVisibleTo
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.renderForConflict
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.memberInfo.getChildrenToAnalyze
import org.jetbrains.kotlin.idea.refactoring.pullUp.willBeMoved
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.search.declarationsSearch.HierarchySearchRequest
import org.jetbrains.kotlin.idea.search.declarationsSearch.searchInheritors
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.keysToMap

@Nls
@OptIn(KaExperimentalApi::class)
internal fun KaDeclarationSymbol.renderForConflicts(
    analysisSession: KaSession,
): String = with(analysisSession) {
    renderForConflict(CallableRenderer)
}

@KaExperimentalApi
private val NoModifierListRenderer = object : KaModifierListRenderer {
    override fun renderModifiers(
        analysisSession: KaSession,
        symbol: KaDeclarationSymbol,
        declarationModifiersRenderer: KaDeclarationModifiersRenderer,
        printer: PrettyPrinter,
    ): Unit = Unit
}

@OptIn(KaExperimentalApi::class)
private val CallableRenderer = KaDeclarationRendererForSource.WITH_SHORT_NAMES.with {
    valueParameterRenderer = KaValueParameterSymbolRenderer.TYPE_ONLY
    modifiersRenderer = modifiersRenderer.with {
        modifierListRenderer = NoModifierListRenderer
    }
    propertyAccessorsRenderer = KaPropertyAccessorsRenderer.NONE
    bodyMemberScopeProvider = KaRendererBodyMemberScopeProvider.NONE
}

@OptIn(KaExperimentalApi::class)
internal fun KaSession.getClashingMemberInTargetClass(
    data: K2PullUpData,
    callableSymbol: KaCallableSymbol,
): KaCallableSymbol? {
    val targetClassSymbol = data.getTargetClassSymbol(analysisSession = this) as? KaDeclarationContainerSymbol ?: return null
    val substitutor = data.getSourceToTargetClassSubstitutor(analysisSession = this)
    return targetClassSymbol.findCallableMemberBySignature(callableSymbol.substitute(substitutor))
}

@OptIn(KaExperimentalApi::class)
private fun KaSession.checkClashWithSuperDeclaration(
    data: K2PullUpData,
    member: KtNamedDeclaration,
    memberSymbol: KaDeclarationSymbol,
    conflicts: MultiMap<PsiElement, String>,
) {
    if (member is KtParameter) {
        handleParameterClash(member, memberSymbol, conflicts, data)
        return
    }
    if (memberSymbol !is KaCallableSymbol) return
    val clashingSuper = getClashingMemberInTargetClass(data, memberSymbol) ?: return
    if (clashingSuper.modality == KaSymbolModality.ABSTRACT) return
    val targetClassSymbol = data.getTargetClassSymbol(analysisSession = this)
    val conflictMessage = KotlinBundle.message(
        "text.class.0.already.contains.member.1",
        targetClassSymbol.renderForConflicts(analysisSession = this),
        memberSymbol.renderForConflicts(analysisSession = this)
    )
    conflicts.putValue(member, conflictMessage.capitalize())
}

@OptIn(KaExperimentalApi::class)
private fun KaSession.handleParameterClash(
    parameter: KtParameter,
    parameterSymbol: KaDeclarationSymbol,
    conflicts: MultiMap<PsiElement, String>,
    data: K2PullUpData,
) {
    parameterSymbol as KaValueParameterSymbol
    val targetClass = data.targetClass as? KtClass ?: return
    val clashingParameter = targetClass.primaryConstructorParameters.find { it.name == parameter.name }
    if (clashingParameter != null) {
        val renderedClashingParameter = renderParameter(clashingParameter)
        val clashMessage = KotlinBundle.message(
            "text.property.would.conflict.with.superclass.primary.constructor.parameter.0",
            parameterSymbol.render(analysisSession = this),
            renderedClashingParameter,
        )
        conflicts.putValue(parameter, clashMessage.capitalize())
    }
}

private fun renderParameter(parameter: KtParameter): String = buildString {
    if (parameter.hasValOrVar()) {
        append(parameter.valOrVarKeyword?.text)
        append(' ')
    }
    append(parameter.name)
    append(": ")
    append(parameter.typeReference?.text)
}

@OptIn(KaExperimentalApi::class)
private fun KaValueParameterSymbol.render(
    analysisSession: KaSession,
): String = with(analysisSession) {
    val keyword = if (isVal) "val" else "var"
    val name = name.asString()
    val renderedType = returnType.render(
        renderer = KaTypeRendererForSource.WITH_SHORT_NAMES,
        position = Variance.INVARIANT,
    )

    return "$keyword $name: $renderedType"
}

private fun PsiClass.isSourceOrTarget(data: K2PullUpData): Boolean {
    var element = unwrapped
    if (element is KtObjectDeclaration && element.isCompanion()) element = element.containingClassOrObject

    return element == data.sourceClass || element == data.targetClass
}

@OptIn(KaExperimentalApi::class)
private fun KaSession.checkAccidentalOverrides(
    data: K2PullUpData,
    member: KtNamedDeclaration,
    memberSymbol: KaDeclarationSymbol,
    conflicts: MultiMap<PsiElement, String>,
) {
    if (memberSymbol is KaCallableSymbol && !member.hasModifier(KtTokens.PRIVATE_KEYWORD)) {
        val memberSymbolInTargetClass = memberSymbol.substitute(data.getSourceToTargetClassSubstitutor(analysisSession = this))
        val sequence =
            HierarchySearchRequest<PsiElement>(data.targetClass, data.targetClass.useScope()).searchInheritors().asIterable().asSequence()
                .filterNot { it.isSourceOrTarget(data) }.mapNotNull { it.unwrapped }.filterIsInstance<KtClassOrObject>()

        for (it in sequence) {
            val subClassSymbol = it.symbol as KaClassSymbol
            val substitutor =
                createInheritanceTypeSubstitutor(subClassSymbol, data.getTargetClassSymbol(analysisSession = this)) ?: KaSubstitutor.Empty(
                    token
                )

            val memberSymbolInSubClass = memberSymbolInTargetClass.substitute(substitutor)
            val clashingMemberSymbol = memberSymbolInSubClass.let {
                subClassSymbol.findCallableMemberBySignature(it)
            } ?: continue

            val clashingMember = clashingMemberSymbol.sourcePsi<PsiElement>() ?: continue
            val message = KotlinBundle.message(
                "text.member.0.in.super.class.will.clash.with.existing.member.of.1",
                memberSymbol.renderForConflicts(analysisSession = this),
                it.symbol.renderForConflicts(analysisSession = this)
            )

            conflicts.putValue(clashingMember, message.capitalize())
        }
    }
}

private fun KaSession.checkInnerClassToInterface(
    data: K2PullUpData, member: KtNamedDeclaration, memberSymbol: KaDeclarationSymbol, conflicts: MultiMap<PsiElement, String>
) {
    if (data.isInterfaceTarget && memberSymbol is KaNamedClassSymbol && memberSymbol.isInner) {
        val message = KotlinBundle.message(
            "text.inner.class.0.cannot.be.moved.to.interface",
            memberSymbol.renderForConflicts(analysisSession = this),
        )
        conflicts.putValue(member, message.capitalize())
    }
}

@OptIn(KaExperimentalApi::class)
private fun KaSession.checkVisibility(
    data: K2PullUpData, memberInfo: KotlinMemberInfo, memberSymbol: KaDeclarationSymbol, conflicts: MultiMap<PsiElement, String>
) {
    fun reportConflictIfAny(targetSymbol: KaDeclarationSymbol) {
        if (targetSymbol.visibility == KaSymbolVisibility.PUBLIC || targetSymbol.visibility == KaSymbolVisibility.LOCAL) return
        if (targetSymbol.psi in data.membersToMove.keysToMap { it.symbol.psi }) return
        val target = targetSymbol.sourcePsi<PsiElement>() ?: return

        val targetElement = target as? PsiNamedElement ?: return
        if (!targetElement.isVisibleTo(data.targetClass)) {
            val message = RefactoringBundle.message(
                "0.uses.1.which.is.not.accessible.from.the.superclass",
                memberSymbol.renderForConflicts(analysisSession = this),
                targetSymbol.renderForConflicts(analysisSession = this),
            )
            conflicts.putValue(target, message.capitalize())
        }
    }

    val member = memberInfo.member
    val childrenToCheck = memberInfo.getChildrenToAnalyze()
    if (memberInfo.isToAbstract && member is KtCallableDeclaration) {
        if (member.typeReference == null) {
            (memberSymbol as KaCallableSymbol).returnType.let { returnType ->
                val typeInTargetClass = data.getSourceToTargetClassSubstitutor(analysisSession = this).substitute(returnType)
                val symbolToCheck = typeInTargetClass.expandedSymbol
                if (symbolToCheck != null) {
                    reportConflictIfAny(symbolToCheck)
                }
            }
        }
    }

    childrenToCheck.forEach { children ->
        children.accept(object : KtTreeVisitorVoid() {
            override fun visitReferenceExpression(expression: KtReferenceExpression) {
                super.visitReferenceExpression(expression)

                expression.references.flatMap { (it as? KtReference)?.resolveToSymbols() ?: emptyList() }
                    .filterIsInstance<KaDeclarationSymbol>().forEach { reportConflictIfAny(it) }
            }
        })
    }
}

internal fun KaSession.collectConflicts(
    sourceClass: KtClassOrObject,
    targetClass: PsiNamedElement,
    memberInfos: List<KotlinMemberInfo>,
    conflicts: MultiMap<PsiElement, String>,
) {
    val pullUpData = K2PullUpData(
        sourceClass, targetClass, memberInfos.mapNotNull { it.member })

    with(pullUpData) {
        for (memberInfo in memberInfos) {
            val member = memberInfo.member
            val memberSymbol = member.symbol

            checkClashWithSuperDeclaration(pullUpData, member, memberSymbol, conflicts)
            checkAccidentalOverrides(pullUpData, member, memberSymbol, conflicts)
            checkInnerClassToInterface(pullUpData, member, memberSymbol, conflicts)
            checkVisibility(pullUpData, memberInfo, memberSymbol, conflicts)
        }
    }
    checkVisibilityInAbstractedMembers(memberInfos, conflicts)
}

@ApiStatus.Internal
internal fun KaSession.checkVisibilityInAbstractedMembers(
    memberInfos: List<KotlinMemberInfo>,
    conflicts: MultiMap<PsiElement, String>,
) {
    val membersToMove = ArrayList<KtNamedDeclaration>()
    val membersToAbstract = ArrayList<KtNamedDeclaration>()

    for (memberInfo in memberInfos) {
        val member = memberInfo.member ?: continue
        (if (memberInfo.isToAbstract) membersToAbstract else membersToMove).add(member)
    }

    for (member in membersToAbstract) {
        val memberSymbol = member.symbol
        member.forEachDescendantOfType<KtSimpleNameExpression> {
            val target = it.mainReference.resolve() as? KtNamedDeclaration ?: return@forEachDescendantOfType
            if (!target.willBeMoved(membersToMove)) return@forEachDescendantOfType
            if (target.hasModifier(KtTokens.PRIVATE_KEYWORD)) {
                val targetSymbol = target.symbol
                val memberText = memberSymbol.renderForConflicts(analysisSession = this)
                val targetText = targetSymbol.renderForConflicts(analysisSession = this)
                val message = KotlinBundle.message("text.0.uses.1.which.will.not.be.accessible.from.subclass", memberText, targetText)
                conflicts.putValue(target, message.capitalize())
            }
        }
    }
}
