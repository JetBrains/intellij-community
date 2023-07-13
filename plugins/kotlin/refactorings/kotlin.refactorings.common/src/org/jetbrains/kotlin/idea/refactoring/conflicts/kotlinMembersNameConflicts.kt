// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.conflicts

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiNamedElement
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewUtil
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.scopes.KtScope
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolKind
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithKind
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithTypeParameters
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.rename.BasicUnresolvableCollisionUsageInfo
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.nonStaticOuterClasses

/**
 * Checks for conflicts with members declared in the same scope if [declaration] would be named as [newName].
 */
fun checkRedeclarationConflicts(declaration: KtNamedDeclaration, newName: String, result: MutableList<UsageInfo>) {
    analyze(declaration) {
        checkDeclarationNewNameConflicts(declaration, Name.identifier(newName), result)
    }
}

private fun KtAnalysisSession.checkDeclarationNewNameConflicts(declaration: KtNamedDeclaration, newName: Name, result: MutableList<UsageInfo>) {

  val symbol = declaration.getSymbol().let {
    (it as? KtValueParameterSymbol?)?.generatedPrimaryConstructorProperty ?: it
  }

  fun getPotentialConflictCandidates(): Sequence<KtDeclarationSymbol> {
    val containingSymbol = symbol.getContainingSymbol() ?: getPackageSymbolIfPackageExists(declaration.containingKtFile.packageFqName)

    if (symbol is KtValueParameterSymbol) {
      return (containingSymbol as KtFunctionLikeSymbol).valueParameters.filter { it.name == newName }.asSequence()
    }

    if (symbol is KtTypeParameterSymbol) {
      val typeParameters = (containingSymbol as? KtSymbolWithTypeParameters)?.typeParameters ?: return emptySequence()

      val outerTypeParameters = (containingSymbol.psi as? KtElement)?.nonStaticOuterClasses()?.flatMap { outerClass -> outerClass.typeParameters.filter { pName -> pName.nameAsName == newName }.map { p -> p.getSymbol() } }.orEmpty()

      return typeParameters.filter { it.name == newName }.asSequence() + outerTypeParameters
    }

    fun KtScope.findSiblingsByName(): Sequence<KtDeclarationSymbol> {
      return when (symbol) {
        is KtClassLikeSymbol -> getClassifierSymbols(newName)
        is KtCallableSymbol -> getCallableSymbols(
          newName).filter { (symbol is KtVariableSymbol) == (it is KtVariableSymbol) && symbol != it }
        else -> return emptySequence()
      }
    }

    return when (containingSymbol) {
      is KtClassOrObjectSymbol -> {
        containingSymbol.getDeclaredMemberScope().findSiblingsByName()
      }

      is KtPackageSymbol -> {
        fun KtDeclarationSymbol.isTopLevelPrivate(): Boolean = (this as? KtSymbolWithVisibility)?.visibility == Visibilities.Private && (this as? KtSymbolWithKind)?.symbolKind == KtSymbolKind.TOP_LEVEL

        fun isInSameFile(s1: KtDeclarationSymbol, s2: KtDeclarationSymbol): Boolean = s1.psi?.containingFile == s2.psi?.containingFile

        containingSymbol.getPackageScope().findSiblingsByName().filter {
          !symbol.isTopLevelPrivate() && !it.isTopLevelPrivate() || isInSameFile(symbol, it)
        }
      }
      else -> {
        val block = declaration.parent as? KtBlockExpression ?: return emptySequence()
        block.statements.mapNotNull {
          if (it.name != newName.asString()) return@mapNotNull null
          val isAccepted = when (symbol) {
            is KtClassOrObjectSymbol -> it is KtClassOrObject
            is KtVariableSymbol -> it is KtProperty
            is KtFunctionLikeSymbol -> it is KtNamedFunction
            else -> false
          }
          if (!isAccepted) return@mapNotNull null
          (it as? KtDeclaration)?.getSymbol()
        }.asSequence()
      }
    }
  }

  for (candidateSymbol in getPotentialConflictCandidates()) {
    val candidate = candidateSymbol.psi as? KtNamedDeclaration ?: continue

    if (candidateSymbol is KtFunctionLikeSymbol && symbol is KtFunctionLikeSymbol && !areSameSignatures(candidateSymbol, symbol)) {
      continue
    }

    val what = candidate.renderDescription()
    val where = candidate.representativeContainer()?.renderDescription() ?: continue
    val message = KotlinBundle.message("text.0.already.declared.in.1", what, where).capitalize()
    result += BasicUnresolvableCollisionUsageInfo(candidate, candidate, message)
  }
}

private fun KtAnalysisSession.areSameSignatures(s1: KtFunctionLikeSymbol, s2: KtFunctionLikeSymbol): Boolean {
  return areTypesTheSame(s1.receiverType, s2.receiverType) && areTypesTheSame(s1.returnType,
                                                                              s2.returnType) && s1.valueParameters.size == s2.valueParameters.size && s1.valueParameters.zip(
    s2.valueParameters).all { (p1, p2) ->
    areTypesTheSame(p1.returnType, p2.returnType) && areTypesTheSame(p1.receiverType, p2.receiverType)
  } && s1.contextReceivers.size == s2.contextReceivers.size && s1.contextReceivers.zip(
    s2.contextReceivers).all { (c1, c2) -> c1.type.isEqualTo(c2.type) }
}

private fun KtAnalysisSession.areTypesTheSame(t1: KtType?, t2: KtType?): Boolean {
  if (t1 === t2) return true
  if (t2 == null) return false
  return t1?.isEqualTo(t2) ?: false
}

fun PsiElement.representativeContainer(): PsiNamedElement? = when (this) {
  is KtDeclaration -> containingClassOrObject ?: getStrictParentOfType<KtNamedDeclaration>() ?: JavaPsiFacade.getInstance(
    project).findPackage(containingKtFile.packageFqName.asString())
  is PsiMember -> containingClass
  else -> null
}

fun PsiNamedElement.renderDescription(): String {
  val type = UsageViewUtil.getType(this)
  if (name == null || name!!.startsWith("<")) return type
  return "$type '$name'".trim()
}
