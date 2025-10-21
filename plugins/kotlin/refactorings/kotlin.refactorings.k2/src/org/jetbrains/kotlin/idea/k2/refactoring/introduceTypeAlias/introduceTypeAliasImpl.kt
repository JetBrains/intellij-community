// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.introduceTypeAlias

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.compositeScope
import org.jetbrains.kotlin.analysis.api.components.isMarkedNullable
import org.jetbrains.kotlin.analysis.api.components.scopeContext
import org.jetbrains.kotlin.analysis.api.components.type
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiRange
import org.jetbrains.kotlin.idea.base.psi.unifier.toRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.CollectingNameValidator
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.K2SemanticMatcher
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.K2SemanticMatcher.matchRanges
import org.jetbrains.kotlin.idea.refactoring.introduce.insertDeclaration
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

sealed class IntroduceTypeAliasAnalysisResult {
    class Error(@NlsContexts.DialogMessage val message: String) : IntroduceTypeAliasAnalysisResult()
    class Success(val descriptor: IntroduceTypeAliasDescriptor) : IntroduceTypeAliasAnalysisResult()
}

fun analyzeResult(aliasData: IntroduceTypeAliasData): IntroduceTypeAliasAnalysisResult {
    val psiFactory = KtPsiFactory(aliasData.originalTypeElement.project)

    val dummyVar =
        (psiFactory.createBlockCodeFragment("val a: Int", aliasData.targetSibling.parent).getContentElement().children[0] as KtProperty).apply {
            WriteCommandAction.writeCommandAction(project).run<Throwable> {
                typeReference!!.replace(
                    aliasData.originalTypeElement.parent as? KtTypeReference
                        ?: if (aliasData.originalTypeElement is KtTypeElement) psiFactory.createType(
                            aliasData.originalTypeElement
                        ) else psiFactory.createType(aliasData.originalTypeElement.text)
                )
            }
        }

    val newTypeReference = dummyVar.typeReference!!
    return analyzeInModalWindow(newTypeReference, KotlinBundle.message("fix.change.signature.prepare")) {

        val referencesToReplaceWithTypeParameters = findReferencesToReplaceWithTypeParameters(aliasData, newTypeReference)

        val typeParameterNameValidator = CollectingNameValidator()
        val brokenReferences =
            referencesToReplaceWithTypeParameters.keySet().filter { referencesToReplaceWithTypeParameters[it].isNotEmpty() }
        val typeParameterNames = KotlinNameSuggester.suggestNamesForTypeParameters(brokenReferences.size, typeParameterNameValidator)
        val typeParameters =
            (typeParameterNames zip brokenReferences).map { TypeParameter(it.first, referencesToReplaceWithTypeParameters[it.second]) }

        if (typeParameters.any { it.typeReferenceInfos.any { info -> info.typeElement == aliasData.originalTypeElement } }) {
            return@analyzeInModalWindow IntroduceTypeAliasAnalysisResult.Error(KotlinBundle.message("text.type.alias.cannot.refer.to.types.which.aren.t.accessible.in.the.scope.where.it.s.defined"))
        }

        val descriptor = IntroduceTypeAliasDescriptor(aliasData, "Dummy", null, typeParameters)

        val initialName =
            KotlinNameSuggester.suggestTypeAliasNameByPsi(descriptor.generateTypeAlias(true).getTypeReference()!!.typeElement!!) {
                findClassifierByName(aliasData.targetSibling, descriptor) == null
            }

        return@analyzeInModalWindow IntroduceTypeAliasAnalysisResult.Success(descriptor.copy(name = initialName))
    }
}

context(_: KaSession)
private fun findReferencesToReplaceWithTypeParameters(
    aliasData: IntroduceTypeAliasData,
    newTypeReference: KtTypeReference,
): MultiMap<KtTypeReference, KtTypeReference> {
    val newReferences = newTypeReference.collectDescendantsOfType<KtTypeReference> { it.originalReference != null }

    val referencesToReplaceWithTypeParameters = MultiMap.createLinked<KtTypeReference, KtTypeReference>()

    val forcedCandidates = if (aliasData.extractTypeConstructor) newTypeReference.typeElement!!.typeArgumentsAsTypes else emptyList()

    for (newReference in newReferences) {
        val originalReference = newReference.originalReference!!

        if (newReference !in forcedCandidates) { //skip for resolved references
            val originalSymbol = (originalReference.type as? KaClassType)?.symbol
            val newSymbol = (newReference.type as? KaClassType)?.symbol
            if (originalSymbol == newSymbol) continue
        }

        with(K2SemanticMatcher) {
            val firstEquivalentReference =
                referencesToReplaceWithTypeParameters.keySet().firstOrNull { it.isSemanticMatch(originalReference) }
            if (firstEquivalentReference != null) {
                referencesToReplaceWithTypeParameters.putValue(firstEquivalentReference, originalReference)
            } else {
                referencesToReplaceWithTypeParameters.putValue(originalReference, originalReference)
            }
        }

        // skip references under other references
        val referencesToExtractIterator = referencesToReplaceWithTypeParameters.values().iterator()
        while (referencesToExtractIterator.hasNext()) {
            val referenceToExtract = referencesToExtractIterator.next()
            if (originalReference.isAncestor(referenceToExtract, true)) {
                referencesToExtractIterator.remove()
            }
        }
    }
    return referencesToReplaceWithTypeParameters
}

fun IntroduceTypeAliasData.getApplicableVisibilities(): List<KtModifierKeywordToken> = when (targetSibling.parent) {
    is KtClassBody -> listOf(PRIVATE_KEYWORD, PUBLIC_KEYWORD, INTERNAL_KEYWORD, PROTECTED_KEYWORD)
    is KtFile -> listOf(PRIVATE_KEYWORD, PUBLIC_KEYWORD, INTERNAL_KEYWORD)
    else -> emptyList()
}

fun IntroduceTypeAliasDescriptor.validate(): IntroduceTypeAliasDescriptorWithConflicts {
    val conflicts = MultiMap<PsiElement, String>()

    val originalType = originalData.originalTypeElement
    when {
        name.isEmpty() -> conflicts.putValue(originalType, KotlinBundle.message("text.no.name.provided.for.type.alias"))

        !name.isIdentifier() -> conflicts.putValue(
            originalType,
            KotlinBundle.message("text.type.alias.name.must.be.a.valid.identifier.0", name)
        )

        analyzeInModalWindow(originalData.targetSibling, KotlinBundle.message("progress.title.check.for.conflicts")) {
            findClassifierByName(originalData.targetSibling, this@validate) != null
        } -> conflicts.putValue(originalType, KotlinBundle.message("text.type.already.exists.in.the.target.scope", name))
    }

    if (typeParameters.distinctBy { it.name }.size != typeParameters.size) {
        conflicts.putValue(originalType, KotlinBundle.message("text.type.parameter.names.must.be.distinct"))
    }

    if (visibility != null && visibility !in originalData.getApplicableVisibilities()) {
        conflicts.putValue(originalType, KotlinBundle.message("text.0.is.not.allowed.in.the.target.context", visibility))
    }

    return IntroduceTypeAliasDescriptorWithConflicts(this, conflicts)
}

context(_: KaSession)
private fun findClassifierByName(
    targetSibling: KtElement, descriptor: IntroduceTypeAliasDescriptor
): KaClassifierSymbol? {
    val ktFile = targetSibling.containingKtFile
    return ktFile.scopeContext(targetSibling).compositeScope().classifiers { it.asString() == descriptor.name }.firstOrNull()
}

context(_: KaSession)
fun findDuplicates(typeAlias: KtTypeAlias): Map<KotlinPsiRange, () -> Unit> {
    val aliasName = typeAlias.name?.quoteIfNeeded() ?: return emptyMap()
    val aliasRange = typeAlias.textRange
    val typeAliasSymbol = typeAlias.symbol

    val psiFactory = KtPsiFactory(typeAlias.project)

    fun replaceTypeReference(occurrence: KtTypeReference, typeArgumentsText: String) {
        occurrence.replace(psiFactory.createType("$aliasName$typeArgumentsText"))
    }

    fun replaceOccurrence(occurrence: PsiElement, arguments: List<KtTypeElement>) {
        val typeArgumentsText = if (arguments.isNotEmpty()) "<${arguments.joinToString { it.text }}>" else ""
        when (occurrence) {
            is KtTypeReference -> {
                replaceTypeReference(occurrence, typeArgumentsText)
            }

            is KtSuperTypeCallEntry -> {
                occurrence.calleeExpression.typeReference?.let { replaceTypeReference(it, typeArgumentsText) }
            }

            is KtCallElement -> {
                val qualifiedExpression = occurrence.parent as? KtQualifiedExpression
                val callExpression = if (qualifiedExpression != null && qualifiedExpression.selectorExpression == occurrence) {
                    qualifiedExpression.replaced(occurrence)
                } else occurrence
                val typeArgumentList = callExpression.typeArgumentList
                if (arguments.isNotEmpty()) {
                    val newTypeArgumentList = psiFactory.createTypeArguments(typeArgumentsText)
                    typeArgumentList?.replace(newTypeArgumentList) ?: callExpression.addAfter(
                        newTypeArgumentList, callExpression.calleeExpression
                    )
                } else {
                    typeArgumentList?.delete()
                }
                callExpression.calleeExpression?.replace(psiFactory.createExpression(aliasName))
            }

            is KtExpression -> occurrence.replace(psiFactory.createExpression(aliasName))
        }
    }

    val rangesWithReplacers = ArrayList<Pair<KotlinPsiRange, () -> Unit>>()

    val originalTypePsi = (typeAliasSymbol.expandedType as? KaClassType)?.symbol?.psi
    if (originalTypePsi != null) {
        for (reference in ReferencesSearch.search(originalTypePsi, LocalSearchScope(typeAlias.parent)).asIterable()) {
            val element = reference.element as? KtSimpleNameExpression ?: continue
            if ((element.textRange.intersects(aliasRange))) continue

            val arguments: List<KtTypeElement>
            val occurrence: KtElement

            val callElement = element.getParentOfTypeAndBranch<KtCallElement> { calleeExpression }
            if (callElement != null) {
                occurrence = callElement
                arguments = callElement.typeArguments.mapNotNull { it.typeReference?.typeElement }
            } else {
                val userType = element.getParentOfTypeAndBranch<KtUserType> { referenceExpression }
                if (userType != null) {
                    occurrence = userType
                    arguments = userType.typeArgumentsAsTypes.mapNotNull { it.typeElement }
                } else continue
            }
            if (arguments.size != typeAliasSymbol.typeParameters.size) continue

            if ((typeAliasSymbol.expandedType as? KaClassType)?.isMarkedNullable == true && occurrence is KtUserType && occurrence.parent !is KtNullableType) continue
            rangesWithReplacers += occurrence.toRange() to { replaceOccurrence(occurrence, arguments) }
        }
    }

    typeAlias.getTypeReference().toRange()
        .match(typeAlias.parent) { targetRange, patternRange -> matchRanges(targetRange, patternRange, typeAlias.typeParameters) }
        .asSequence()
        .filter { !(it.range.textRange.intersects(aliasRange)) }
        .mapNotNullTo(rangesWithReplacers) { match ->
            val occurrence = match.range.elements.singleOrNull() as? KtTypeReference ?: return@mapNotNullTo null
            val arguments = typeAlias.typeParameters.mapNotNull { (match.substitution[it] as? KtTypeReference)?.typeElement }
            if (arguments.size != typeAlias.typeParameters.size) return@mapNotNullTo null
            match.range to { replaceOccurrence(occurrence, arguments) }
        }
    return rangesWithReplacers.toMap()
}

private var KtTypeReference.typeParameterInfo: TypeParameter? by CopyablePsiUserDataProperty(Key.create("TYPE_PARAMETER_INFO"))

fun IntroduceTypeAliasDescriptor.generateTypeAlias(previewOnly: Boolean = false): KtTypeAlias {
    val originalElement = originalData.originalTypeElement
    val psiFactory = KtPsiFactory(originalElement.project)

    for (typeParameter in typeParameters) for (it in typeParameter.typeReferenceInfos) {
        it.typeParameterInfo = typeParameter
    }

    val typeParameterNames = typeParameters.map { it.name }
    val typeAlias = if (originalElement is KtTypeElement) {
        psiFactory.createTypeAlias(name, typeParameterNames, originalElement)
    } else {
        psiFactory.createTypeAlias(name, typeParameterNames, originalElement.text)
    }
    if (visibility != null && visibility != DEFAULT_VISIBILITY_KEYWORD) {
        typeAlias.addModifier(visibility)
    }

    for (typeParameter in typeParameters) for (it in typeParameter.typeReferenceInfos) {
        it.typeParameterInfo = null
    }

    fun replaceUsage() {
        val aliasInstanceText = if (typeParameters.isNotEmpty()) {
            "$name<${typeParameters.joinToString { it.typeReferenceInfos.first().text }}>"
        } else {
            name
        }
        when (originalElement) {
            is KtTypeElement -> originalElement.replace(psiFactory.createType(aliasInstanceText).typeElement!!)
            is KtExpression -> originalElement.replace(psiFactory.createExpression(aliasInstanceText))
        }
    }

    fun introduceTypeParameters() {
        typeAlias.getTypeReference()!!.forEachDescendantOfType<KtTypeReference> {
            val typeParameter = it.typeParameterInfo ?: return@forEachDescendantOfType
            val typeParameterReference = psiFactory.createType(typeParameter.name)
            it.replace(typeParameterReference)
        }
    }

    return if (previewOnly) {
        introduceTypeParameters()
        typeAlias
    } else {
        replaceUsage()
        introduceTypeParameters()
        insertDeclaration(typeAlias, originalData.targetSibling)
    }
}