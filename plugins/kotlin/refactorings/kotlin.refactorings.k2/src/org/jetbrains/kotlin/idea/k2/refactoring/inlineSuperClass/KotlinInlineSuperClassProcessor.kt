// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inlineSuperClass

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.renderer.render
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.function
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulSymbol
import org.jetbrains.kotlin.analysis.api.resolution.single
import org.jetbrains.kotlin.analysis.api.resolution.tryResolveCall
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.signatures.substitute
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.classSymbol
import org.jetbrains.kotlin.analysis.api.symbols.containingSymbol
import org.jetbrains.kotlin.analysis.api.types.KaSubstitutor
import org.jetbrains.kotlin.analysis.api.types.createInheritanceTypeSubstitutor
import org.jetbrains.kotlin.analysis.api.types.emptySubstitutor
import org.jetbrains.kotlin.analysis.api.types.type
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.analysis.api.utils.allowAnalysisFromWriteActionInEdt
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameValidatorProvider
import org.jetbrains.kotlin.idea.base.psi.addMemberDeclarationBefore
import org.jetbrains.kotlin.idea.base.psi.addSuperType
import org.jetbrains.kotlin.idea.base.psi.getOrCreateClassBody
import org.jetbrains.kotlin.idea.base.psi.removeSuperType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.core.util.runSynchronouslyWithProgress
import org.jetbrains.kotlin.idea.k2.refactoring.findCallableMemberBySignature
import org.jetbrains.kotlin.idea.k2.refactoring.inline.createUsageReplacementStrategyForFunction
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.KotlinNameSuggester
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.usages.K2MoveRenameUsageInfo
import org.jetbrains.kotlin.idea.k2.refactoring.pullUp.applyMarking
import org.jetbrains.kotlin.idea.k2.refactoring.pullUp.clearMarking
import org.jetbrains.kotlin.idea.k2.refactoring.pullUp.markElements
import org.jetbrains.kotlin.idea.k2.refactoring.pushDown.K2PushDownContext
import org.jetbrains.kotlin.idea.k2.refactoring.pushDown.K2PushDownProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.pushDown.analyzePushDownConflicts
import org.jetbrains.kotlin.idea.k2.refactoring.pushDown.sourceDeclarationForPushDown
import org.jetbrains.kotlin.idea.refactoring.getContainer
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.UsageReplacementStrategy
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.replaceUsages
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.memberInfo.resolveSuperTypeDeclaration
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.searching.inheritors.DirectKotlinClassInheritorsSearch
import org.jetbrains.kotlin.idea.util.resolveSuccessfulExpressionSymbol
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnonymousInitializer
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDelegatedSuperTypeEntry
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtValueArgumentName
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny
import org.jetbrains.kotlin.types.Variance

internal class KotlinInlineSuperClassProcessor(
    project: Project,
    private val sourceClass: KtClass,
) : BaseRefactoringProcessor(project) {
    private var targets: List<PsiElement> = emptyList()
    private val sourceDeclarations = sourceClass.declarations
    private val members = sourceDeclarations.filterIsInstance<KtNamedDeclaration>().map {
        KotlinMemberInfo.Declaration(it).apply { isChecked = true }
    }

    override fun getCommandName(): String = KotlinBundle.message("inline.super.class.title")

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor = KotlinInlineSuperClassUsageViewDescriptor(sourceClass)

    override fun findUsages(): Array<UsageInfo> {
        targets = DirectKotlinClassInheritorsSearch.search(sourceClass).findAll().mapNotNull { it.unwrapped }.distinct()
        val usages = targets.mapTo(ArrayList()) { UsageInfo(it) }
        usages += K2MoveRenameUsageInfo.findExternalUsages(sourceClass)
        for (member in sourceClass.declarations.filterIsInstance<KtClassOrObject>()) {
            usages += K2MoveRenameUsageInfo.findExternalUsages(member)
                .filterNot { usage -> usage.element?.let { PsiTreeUtil.isAncestor(sourceClass, it, false) } == true }
        }
        val pushDownProcessor = K2PushDownProcessor(myProject, sourceClass, members, inlineSuperclass = true)
        usages += pushDownProcessor.classMemberUsages()
        return usages.toTypedArray()
    }

    override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        val usages = refUsages.get()
        val (unsupported, conflicts) = myProject.runSynchronouslyWithProgress(KotlinBundle.message("inline.super.class.detecting.conflicts"), true) {
            runReadActionBlocking {
                markConstructorParameterReferences()
                val unsupported = unsupportedReason(usages)
                if (unsupported != null) return@runReadActionBlocking unsupported to null
                analyze(sourceClass) {
                    val constructorParameters = sourceClass.primaryConstructorParameters.map {
                        KotlinMemberInfo.Declaration(it).apply { isChecked = true }
                    }
                    val conflicts = analyzePushDownConflicts(
                        K2PushDownContext(sourceClass, members + constructorParameters), targetUsages(), inlineSuperclass = true,
                    )
                    checkExternalUsageModuleConflicts(usages, conflicts)
                    if (constructorParameters.isNotEmpty() && sourceClass.superTypeListEntries.any { it is KtSuperTypeCallEntry }) {
                        for (target in targets.filterIsInstance<KtClassOrObject>()) {
                            analyze(target) {
                                val entry = target.superTypeListEntries.single { it.resolveSuperTypeDeclaration() == sourceClass }
                                val constructor = prepareConstructor(target, entry, substitutor(target.classSymbol, sourceClass.classSymbol))
                                if (constructor.needsArgumentStorage) {
                                    conflicts.putValue(target, KotlinBundle.message("inline.super.class.unsupported.constructor"))
                                }
                            }
                        }
                    }
                    null to conflicts
                }
            }
        } ?: return false
        if (unsupported != null) {
            CommonRefactoringUtil.showErrorHint(myProject, null, unsupported, commandName, "Inline_Super_Class")
            return false
        }
        return showConflicts(conflicts ?: return false, usages)
    }

    private fun unsupportedReason(usages: Array<UsageInfo>): @NlsContexts.DialogMessage String? {
        if (targets.isEmpty()) return KotlinBundle.message("inline.super.class.no.inheritors")
        if (sourceClass.isInner() || sourceClass.isLocal || sourceClass.hasModifier(KtTokens.EXPECT_KEYWORD) ||
            sourceClass.hasModifier(KtTokens.ACTUAL_KEYWORD)
        ) {
            return KotlinBundle.message("inline.super.class.unsupported.class")
        }
        if (sourceClass.superTypeListEntries.any { it is KtDelegatedSuperTypeEntry }) {
            return KotlinBundle.message("inline.super.class.unsupported.delegation")
        }
        if (sourceClass.primaryConstructorParameters.any { it.isVarArg }) {
            return KotlinBundle.message("inline.super.class.unsupported.constructor")
        }
        if (sourceClass.declarations.any {
                it !is KtNamedFunction && it !is KtProperty && it !is KtClassOrObject && it !is KtAnonymousInitializer
            }) {
            return KotlinBundle.message("inline.super.class.unsupported.member")
        }
        for (companion in sourceClass.companionObjects) {
            companionUnsupportedReason(sourceClass, companion, targets)?.let { return it }
        }
        for (target in targets) {
            if (target !is KtClassOrObject || target.fqName == null || PsiTreeUtil.isAncestor(sourceClass, target, false)) {
                return KotlinBundle.message("inline.super.class.unsupported.inheritor")
            }
            if (target.superTypeListEntries.any { it is KtDelegatedSuperTypeEntry && it.resolveSuperTypeDeclaration() == sourceClass }) {
                return KotlinBundle.message("inline.super.class.unsupported.delegation")
            }
            if (!sourceClass.isInterface() && target.superTypeListEntries.any {
                    it.resolveSuperTypeDeclaration() == sourceClass && it !is KtSuperTypeCallEntry
                }) {
                return KotlinBundle.message("inline.super.class.unsupported.constructor")
            }
        }
        for (usage in usages.filterIsInstance<K2MoveRenameUsageInfo>()) {
            val element = usage.element ?: continue
            if (isInheritanceReference(element)) continue
            if (element.getStrictParentOfType<KtImportDirective>() != null) continue
            if (element !is KtSimpleNameExpression) return KotlinBundle.message("inline.super.class.unsupported.usage")
            if (targets.size != 1) return KotlinBundle.message("inline.super.class.ambiguous.usage")
            if (sourceClass.typeParameters.isNotEmpty() || (targets.single() as? KtClass)?.typeParameters?.isNotEmpty() == true) {
                return KotlinBundle.message("inline.super.class.generic.usage")
            }
            val call = element.parent as? KtCallExpression
            if (call?.calleeExpression == element && usage.referencedElement == sourceClass) {
                val target = targets.single()
                if (sourceClass.primaryConstructorParameters.isNotEmpty() || target !is KtClass || target.hasModifier(KtTokens.ABSTRACT_KEYWORD) ||
                    target.primaryConstructorParameters.any { !it.hasDefaultValue() && !it.isVarArg }
                ) {
                    return KotlinBundle.message("inline.super.class.constructor.usage")
                }
            }
        }
        return null
    }

    private fun isInheritanceReference(element: PsiElement): Boolean {
        val entry = element.getStrictParentOfType<KtSuperTypeListEntry>() ?: return false
        return entry.parent.parent in targets && entry.typeReference?.let { PsiTreeUtil.isAncestor(it, element, false) } == true
    }

    private fun targetUsages(): Array<UsageInfo> = targets.map(::UsageInfo).toTypedArray()

    private fun checkExternalUsageModuleConflicts(usages: Array<UsageInfo>, conflicts: MultiMap<PsiElement, String>) {
        val target = targets.firstOrNull() as? KtClassOrObject ?: return
        val targetFile = target.containingFile.virtualFile ?: return
        for (usage in usages.filterIsInstance<K2MoveRenameUsageInfo>()) {
            if (usage.referencedElement != sourceClass) continue
            val element = usage.element ?: continue
            if (isInheritanceReference(element) || element.resolveScope.contains(targetFile)) continue
            val module = element.module ?: continue
            val message = RefactoringBundle.message(
                "0.referenced.in.1.will.not.be.accessible.from.module.2",
                RefactoringUIUtil.getDescription(target, true),
                RefactoringUIUtil.getDescription(element.getContainer(), true),
                CommonRefactoringUtil.htmlEmphasize(module.name),
            )
            conflicts.putValue(element, message.replaceFirstChar { it.uppercaseChar() })
        }
    }

    private class TargetPlan(
        val target: KtClassOrObject,
        val oldEntry: KtSuperTypeListEntry,
        val superTypes: List<Pair<KtSuperTypeListEntry, KtSuperTypeListEntry>>,
        val originalDeclarations: Set<KtDeclaration>,
        val constructor: ConstructorPlan,
        val replacements: MutableMap<PsiElement, PsiElement> = LinkedHashMap(),
    )

    private class ConstructorPlan(
        val arguments: Map<KtParameter, KtExpression>,
        val properties: Map<KtParameter, KtProperty>,
        val needsArgumentStorage: Boolean = false,
    )

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        val marked = ArrayList<KtElement>()
        val sourceThisExpressions = sourceClass.collectDescendantsOfType<KtThisExpression> {
                it.getLabelName() != null && it.instanceReference.mainReference.resolve() == sourceClass
            }
        sourceThisExpressions.forEach { it.putCopyableUserData(SOURCE_THIS, true) }
        val failedToInline = ArrayList<KtElement>() // todo collect references to failed to inline super members
        try {
            K2MoveRenameUsageInfo.markInternalUsages(sourceClass)
            val plans = prepareTargets(marked)
            inlineSuperCalls(plans, failedToInline)
            val pushDownProcessor = K2PushDownProcessor(myProject, sourceClass, members, inlineSuperclass = true)
            pushDownProcessor.pushDownToClasses(
                usages, removeOriginalMembers = true,
            ) { original, added, target ->
                retargetSourceThis(added, target)
                val plan = plans.single { it.target == target }
                substituteConstructorParameters(added, plan.constructor.arguments)
                plan.replacements[original] = added
                added.collectDescendantsOfType<KtNamedDeclaration>().forEach { declaration ->
                    declaration.sourceDeclarationForPushDown()?.let { plan.replacements[it] = declaration }
                }
            }
            for (plan in plans) {
                copyInitializers(plan)
                restoreDeclarationOrder(plan)
                for ((parameter, property) in plan.constructor.properties.entries.reversed()) {
                    val firstDeclaration = plan.target.declarations.firstOrNull()
                    val added = plan.target.addMemberDeclarationBefore(property, null)
                    if (firstDeclaration is KtNamedFunction) {
                        plan.target.getOrCreateClassBody().addAfter(KtPsiFactory(myProject).createNewLine(2), added)
                    }
                    plan.replacements[parameter] = added
                }
                for ((original, replacement) in plan.superTypes) {
                    substituteConstructorParameters(replacement, plan.constructor.arguments)
                    plan.replacements[original] = plan.target.addSuperType(replacement)
                }
                plan.target.removeSuperType(plan.oldEntry)
            }

            val externalUsages = usages.filterIsInstance<K2MoveRenameUsageInfo>()

            // for multiple targets we retarget external usages to the first target
            plans.firstOrNull()?.let { plan ->
                plan.replacements[sourceClass] = plan.target
                K2MoveRenameUsageInfo.retargetUsages(externalUsages, plan.replacements, fromCopy = true)
                shortenReferences(plan.target)
            }
            sourceClass.delete()
        } finally {
            sourceThisExpressions.forEach { it.putCopyableUserData(SOURCE_THIS, null) }
            clearMarking(marked)
        }
    }

    private fun prepareTargets(marked: MutableList<KtElement>): List<TargetPlan> {
        val factory = KtPsiFactory(myProject)
        return targets.filterIsInstance<KtClassOrObject>().map { target ->
            allowAnalysisFromWriteActionInEdt(target) {
                val substitutor = substitutor(target.classSymbol, sourceClass.classSymbol)
                val oldEntry = target.superTypeListEntries.single { it.resolveSuperTypeDeclaration() == sourceClass }
                val superTypes = sourceClass.superTypeListEntries.mapNotNull { entry ->
                    if (target.superTypeListEntries.any { it.resolveSuperTypeDeclaration() == entry.resolveSuperTypeDeclaration() }) {
                        return@mapNotNull null
                    }
                    val type = entry.typeReference?.type ?: return@mapNotNull null
                    val replacement = entry.copy() as KtSuperTypeListEntry
                    replacement.typeReference!!.replace(factory.createType(substitutor.substitute(type).render(position = Variance.INVARIANT)))
                    entry to replacement
                }
                for (initializer in sourceClass.getAnonymousInitializers()) {
                    marked += markElements(initializer, sourceClass, target, substitutor)
                }
                TargetPlan(target, oldEntry, superTypes, target.declarations.toSet(), prepareConstructor(target, oldEntry, substitutor))
            }
        }
    }

    private fun markConstructorParameterReferences() {
        allowAnalysisFromWriteActionInEdt(sourceClass) {
            sourceClass.forEachDescendantOfType<KtSimpleNameExpression> { expression ->
                val parameter = expression.resolveSuccessfulSymbol()?.psi as? KtParameter
                if (parameter in sourceClass.primaryConstructorParameters) {
                    expression.putCopyableUserData(CONSTRUCTOR_PARAMETER, parameter)
                }
            }
        }
    }

    context(_: KaSession)
    private fun prepareConstructor(
        target: KtClassOrObject,
        entry: KtSuperTypeListEntry,
        substitutor: KaSubstitutor,
    ): ConstructorPlan {
        val parameters = sourceClass.primaryConstructorParameters
        if (parameters.isEmpty()) return ConstructorPlan(emptyMap(), emptyMap())
        val call = (entry as KtSuperTypeCallEntry).tryResolveCall()?.single?.function
        val factory = KtPsiFactory(myProject)
        val arguments = LinkedHashMap<KtParameter, KtExpression>()
        val properties = LinkedHashMap<KtParameter, KtProperty>()
        var needsArgumentStorage = false

        val targetParameters = target.primaryConstructor?.valueParameters.orEmpty()
        for ((superParameter, argument) in retrieveSuperArgumentsFromCall(call, parameters)) {
            val value = substituteConstructorParameters(argument.copy() as KtExpression, arguments) as KtExpression
            val parameterType = substitutor.substitute(superParameter.typeReference!!.type)
            val isForwardedParameter = argument is KtSimpleNameExpression &&
                    argument.resolveSuccessfulSymbol()?.psi in targetParameters
            if (value is KtConstantExpression || value is KtStringTemplateExpression && !value.hasInterpolation() || isForwardedParameter) {
                val keepProperty = superParameter.hasValOrVar() && (
                        isForwardedParameter || superParameter.isMutable || superParameter.modifierList != null ||
                                ReferencesSearch.search(superParameter).any {
                                    val element = it.element
                                    val parent = element.parent
                                    !PsiTreeUtil.isAncestor(sourceClass, element, false) && parent !is KtValueArgumentName ||
                                            parent is KtCallableReferenceExpression ||
                                            parent is KtQualifiedExpression && parent.selectorExpression == element &&
                                            parent.receiverExpression !is KtThisExpression
                                }
                        )
                if (!keepProperty) {
                    arguments[superParameter] = value
                    continue
                }
            } else {
                needsArgumentStorage = true
            }
            val validator = KotlinNameValidatorProvider.getInstance()
                .createNameValidator(
                    container = target.body ?: target,
                    target = KotlinNameSuggestionProvider.ValidatorTarget.PROPERTY,
                    target.declarations.firstOrNull()
                )
            val originalName = superParameter.name!!
            val name = if (superParameter.hasValOrVar()) originalName else KotlinNameSuggester.suggestNameByName(originalName, validator)

            val modifiers = if (superParameter.hasValOrVar()) superParameter.modifierList?.text.orEmpty() else "private"
            val keyword = if (superParameter.isMutable) "var" else "val"
            val type = parameterType.render(position = Variance.INVARIANT)
            val property = factory.createProperty("$modifiers $keyword ${name.quoteIfNeeded()}: $type = 0")
            property.initializer!!.replace(value)
            properties[superParameter] = property
            if (!superParameter.hasValOrVar()) arguments[superParameter] = factory.createExpression(name.quoteIfNeeded())
        }
        return ConstructorPlan(arguments, properties, needsArgumentStorage)
    }

    private fun retrieveSuperArgumentsFromCall(
        call: KaFunctionCall<*>?,
        parameters: List<KtParameter>
    ): LinkedHashMap<KtParameter, KtExpression> {
        val arguments = LinkedHashMap<KtParameter, KtExpression>()
        for ((argument, parameter) in call?.valueArgumentMapping.orEmpty()) {
            val declaration = parameter.symbol.psi as? KtParameter ?: continue
            arguments[declaration] = argument
        }
        for (parameter in parameters) {
            if (parameter !in arguments) {
                parameter.defaultValue?.let { arguments[parameter] = it }
            }
        }
        return arguments
    }

    private fun substituteConstructorParameters(element: KtElement, arguments: Map<KtParameter, KtExpression>): KtElement {
        if (element is KtSimpleNameExpression) {
            arguments[element.getCopyableUserData(CONSTRUCTOR_PARAMETER)]?.let { return it.copy() as KtExpression }
        }
        for (reference in element.collectDescendantsOfType<KtSimpleNameExpression>()) {
            val parameter = reference.getCopyableUserData(CONSTRUCTOR_PARAMETER) ?: continue
            val value = arguments[parameter] ?: continue
            val qualified = reference.parent as? KtQualifiedExpression
            val usage = if (qualified?.selectorExpression == reference && qualified.receiverExpression is KtThisExpression) {
                qualified
            } else {
                reference
            }
            usage.replace(value)
        }
        return element
    }

    context(session: KaSession)
    private fun substitutor(targetSymbol: KaClassSymbol?, sourceSymbol: KaClassSymbol?): KaSubstitutor =
        (if (targetSymbol != null && sourceSymbol != null) createInheritanceTypeSubstitutor(targetSymbol, sourceSymbol) else null) ?: emptySubstitutor

    private fun inlineSuperCalls(plans: List<TargetPlan>, failedToInline: ArrayList<KtElement>) {
        for (plan in plans) {
            val calls = LinkedHashMap<UsageReplacementStrategy, MutableList<KtSimpleNameExpression>>()
            val target = plan.target
            val qualifiers = ArrayList<KtQualifiedExpression>()
            allowAnalysisFromWriteActionInEdt(target) {
                val sourceSymbol = sourceClass.classSymbol
                val targetSymbol = target.classSymbol
                val substitutor = substitutor(targetSymbol, sourceSymbol)
                for (expression in target.collectDescendantsOfType<KtSuperExpression>()) {
                    if (expression.getStrictParentOfType<KtClassOrObject>() != target) continue
                    val qualified = expression.getQualifiedExpressionForReceiver() ?: continue
                    val reference = qualified.selectorExpression?.getCalleeExpressionIfAny() as? KtSimpleNameExpression ?: continue
                    val symbol = qualified.resolveSuccessfulExpressionSymbol() as? KaCallableSymbol ?: continue
                    val member = symbol.psi ?: continue
                    if (symbol.containingSymbol != sourceSymbol && member !in sourceClass.primaryConstructorParameters) continue

                    val override = targetSymbol?.findCallableMemberBySignature(symbol.substitute(substitutor), ignoreReturnType = true)
                    if (override != null && member is KtNamedFunction && member.hasBody()) {
                        val strategy = createUsageReplacementStrategyForFunction(member, null)
                        if (strategy != null) {
                            calls.getOrPut(strategy) { ArrayList() }.add(reference)
                        } else {
                            failedToInline.add(member)
                            qualifiers += qualified
                        }
                    } else {
                        qualifiers += qualified
                    }
                }
            }
            for ((strategy, references) in calls) {
                strategy.replaceUsages(references, unwrapSpecialUsages = false) { null }
            }
            for (qualified in qualifiers) {
                if (qualified.isValid) qualified.receiverExpression.replace(KtPsiFactory(myProject).createExpression("this"))
            }
        }
    }

    private fun copyInitializers(plan: TargetPlan) {
        for (initializer in sourceClass.getAnonymousInitializers()) {
            val added = plan.target.addMemberDeclarationBefore(initializer, null)
            retargetSourceThis(added, plan.target)
            applyMarking(added, plan.target)
            substituteConstructorParameters(added, plan.constructor.arguments)
            plan.replacements[initializer] = added
        }
    }

    private fun restoreDeclarationOrder(plan: TargetPlan) {
        for (original in sourceDeclarations.asReversed()) {
            val added = plan.replacements[original] as? KtDeclaration ?: continue
            if (added == plan.target || added in plan.originalDeclarations) continue
            val reordered = plan.target.addMemberDeclarationBefore(added, null)
            added.delete()
            plan.replacements[original] = reordered
            // the members of `added` are gone as well, so the replacements pointing at them have to follow the copy
            reordered.collectDescendantsOfType<KtNamedDeclaration>().forEach { declaration ->
                declaration.sourceDeclarationForPushDown()?.let { plan.replacements[it] = declaration }
            }
        }
    }

    override fun getBeforeData(): RefactoringEventData = RefactoringEventData().apply { addElement(sourceClass) }
    override fun getAfterData(usages: Array<out UsageInfo>): RefactoringEventData = RefactoringEventData().apply { addElements(targets) }
    override fun getRefactoringId(): String = "refactoring.inline.class"

    private fun retargetSourceThis(element: KtElement, target: KtClassOrObject) {
        val factory = KtPsiFactory(myProject)
        for (expression in element.collectDescendantsOfType<KtThisExpression> { it.getCopyableUserData(SOURCE_THIS) == true }) {
            val name = target.name
            expression.replace(factory.createExpression(name?.let { "this@${name.quoteIfNeeded()}" } ?: "this"))
        }
    }

    companion object {
        private val SOURCE_THIS = Key.create<Boolean>("kotlin.inline.super.class.this")
        private val CONSTRUCTOR_PARAMETER = Key.create<KtParameter>("kotlin.inline.super.class.constructor.parameter")
    }
}
