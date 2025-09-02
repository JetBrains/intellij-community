// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.conflicts

import com.intellij.psi.*
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.descendantsOfType
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.base.KaContextReceiver
import org.jetbrains.kotlin.analysis.api.scopes.KaScope
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.asJava.accessorNameByPropertyName
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.rename.BasicUnresolvableCollisionUsageInfo
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.findPropertyByName
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * Checks for conflicts with members declared in the same scope if [declaration] would be named as [newName].
 */
fun checkRedeclarationConflicts(declaration: KtNamedDeclaration, newName: String, result: MutableList<UsageInfo>) {
    analyze(declaration) {
        val symbol = declaration.symbol
        checkDeclarationNewNameConflicts(declaration, Name.identifier(newName), result) {
            filterCandidates(symbol, it)
        }
    }
    checkRedeclarationConflictsInInheritors(declaration, newName, result)
}

context(KaSession)
fun KaScope.findSiblingsByName(
    symbol: KaDeclarationSymbol,
    newName: Name,
    containingSymbol: KaDeclarationSymbol? = symbol.containingDeclaration
): Sequence<KaDeclarationSymbol> {
    if (symbol is KaConstructorSymbol) {
        return constructors.filter { symbol != it }
    }
    val callables = callables(newName).filter { callable ->
        symbol != callable &&
                (callable.visibility != KaSymbolVisibility.PRIVATE || callable.containingDeclaration == containingSymbol)
    }

    val classifierSymbols = classifiers(newName)
    if (symbol is KaFunctionSymbol) {
        return (classifierSymbols.flatMap { (it as? KaClassSymbol)?.declaredMemberScope?.constructors ?: emptySequence() } + callables)
    }

    return (classifierSymbols + callables)
}

context(KaSession)
fun filterCandidates(symbol: KaDeclarationSymbol, candidateSymbol: KaDeclarationSymbol): Boolean {
    if (symbol == candidateSymbol) return false
    if (candidateSymbol is KaFunctionSymbol) {
        val skipCandidate = when (symbol) {
            is KaFunctionSymbol -> !areSameSignatures(candidateSymbol, symbol)
            is KaPropertySymbol -> !areSameSignatures(symbol, candidateSymbol)
            is KaClassSymbol -> symbol.declaredMemberScope.constructors.none { areSameSignatures(it, candidateSymbol) }
            else -> true
        }

        return !skipCandidate
    }

    if (candidateSymbol is KaPropertySymbol && symbol is KaFunctionSymbol && !areSameSignatures(candidateSymbol, symbol)) {
        return false
    }

    if (candidateSymbol is KaClassSymbol && symbol is KaFunctionSymbol) {
        if (candidateSymbol.declaredMemberScope.constructors.none { areSameSignatures(it, symbol) }) {
            return false
        }
    }

    return true
}

context(KaSession)
fun checkDeclarationNewNameConflicts(
    declaration: KtNamedDeclaration,
    newName: Name,
    result: MutableList<UsageInfo>,
    filterCandidate: (KaDeclarationSymbol) -> Boolean
) {
    @OptIn(KaExperimentalApi::class)
    fun getPotentialConflictCandidates(symbol: KaDeclarationSymbol, declaration: KtNamedDeclaration, newName: Name): Sequence<KaDeclarationSymbol> {
        val containingSymbol = symbol.containingDeclaration ?: findPackage(declaration.containingKtFile.packageFqName)

        if (symbol is KaValueParameterSymbol || symbol is KaContextParameterSymbol) {
            val functionLikeSymbol = containingSymbol as KaFunctionSymbol
            val locals = functionLikeSymbol.psi?.descendantsOfType<KtVariableDeclaration>()?.filter { it.nameAsName == newName }
                ?.mapNotNull { it.symbol } ?: emptySequence()
            return functionLikeSymbol.valueParameters.filter { it.name == newName }.asSequence() + locals
        }

        if (symbol is KaTypeParameterSymbol) {
            @OptIn(KaExperimentalApi::class)
            val typeParameters = (containingSymbol as? KaDeclarationSymbol)?.typeParameters?.filter { it.name == newName }?.asSequence() ?: return emptySequence()

            val outerTypeParameters = generateSequence<KtClassOrObject>(declaration.getStrictParentOfType()) {
                if (it is KtClass && it.isInner()) it.getStrictParentOfType() else null
            }.flatMap { outerClass ->
                outerClass.typeParameters.filter { pName -> pName.nameAsName == newName }.map { p -> p.symbol }
            }

            val innerTypeParameters = (containingSymbol.psi as? KtElement)?.let { currentPsi ->
                PsiTreeUtil.findChildrenOfType(currentPsi, KtClass::class.java)
                    .filter { it.isInner() }
                    .flatMap { innerClass -> innerClass.typeParameters.mapNotNull { p -> if (p.nameAsName == newName) p.symbol else null } }
            }.orEmpty()

            return typeParameters + outerTypeParameters + innerTypeParameters
        }

        return when (containingSymbol) {
            is KaClassSymbol -> {
                if (symbol is KaClassifierSymbol) {
                    //allow shadowing classes in super
                    containingSymbol.combinedDeclaredMemberScope
                } else {
                    containingSymbol.combinedMemberScope
                }.findSiblingsByName(symbol, newName)
            }

            is KaPackageSymbol -> {
                fun KaDeclarationSymbol.isTopLevelPrivate(): Boolean = this.visibility == KaSymbolVisibility.PRIVATE && this.location == KaSymbolLocation.TOP_LEVEL

                fun isInSameFile(s1: KaDeclarationSymbol, s2: KaDeclarationSymbol): Boolean = s1.psi?.containingFile == s2.psi?.containingFile

                containingSymbol.packageScope.findSiblingsByName(symbol, newName).filter {
                    !symbol.isTopLevelPrivate() && !it.isTopLevelPrivate() || isInSameFile(symbol, it)
                }
            }
            else -> {
                val block = declaration.parent as? KtBlockExpression ?: return emptySequence()
                val functionParameters = (declaration.getParentOfType<KtFunction>(true)?.symbol as? KaFunctionSymbol)?.valueParameters?.filter { it.name == newName } ?: emptyList()
                (block.statements.mapNotNull {
                    if (it.name != newName.asString()) return@mapNotNull null
                    val isAccepted = when (symbol) {
                        is KaClassSymbol -> it is KtClassOrObject
                        is KaPropertySymbol, is KaJavaFieldSymbol, is KaLocalVariableSymbol -> it is KtProperty
                        is KaFunctionSymbol -> it is KtNamedFunction
                        else -> false
                    }
                    if (!isAccepted) return@mapNotNull null
                    (it as? KtDeclaration)?.symbol
                } + functionParameters).asSequence()
            }
        }
    }

    fun getPotentialConflictCandidates(declaration: KtNamedDeclaration, newName: Name): Sequence<KaDeclarationSymbol> {
        val declarationSymbol = declaration.symbol
        val symbol = declarationSymbol.let {
            (it as? KaValueParameterSymbol?)?.generatedPrimaryConstructorProperty ?: it
        }

        var potentialCandidates = getPotentialConflictCandidates(symbol, declaration, newName)
        if (declarationSymbol is KaValueParameterSymbol && symbol is KaPropertySymbol) {
            val functionLikeSymbol = declarationSymbol.containingDeclaration as? KaFunctionSymbol
            val conflictingParameters = functionLikeSymbol?.valueParameters?.filter { it.name == newName && it != declarationSymbol }?.takeIf { it.isNotEmpty() }
            if (conflictingParameters != null) {
                potentialCandidates = potentialCandidates + conflictingParameters
            }
        }
        return potentialCandidates
    }

    var potentialCandidates = getPotentialConflictCandidates(declaration, newName).filter { filterCandidate(it) }
    for (candidateSymbol in potentialCandidates) {
        registerAlreadyDeclaredConflict(candidateSymbol, result)
    }
}

fun checkNewPropertyConflicts(
    containingClass: KtClassOrObject,
    newName: String,
    result: MutableList<UsageInfo>,
) {
    analyze(containingClass) {
        val containingSymbol = containingClass.namedClassSymbol ?: return
        var potentialCandidates = containingSymbol
            .combinedMemberScope
            .findSiblingsByName(containingSymbol, Name.identifier(newName), containingSymbol)
            .filter { candidateSymbol -> candidateSymbol !is KaFunctionSymbol }
        for (candidateSymbol in potentialCandidates) {
            registerAlreadyDeclaredConflict(candidateSymbol, result)
        }
    }
}

fun registerAlreadyDeclaredConflict(candidateSymbol: KaDeclarationSymbol, result: MutableList<UsageInfo>) {
    val candidate = candidateSymbol.psi as? PsiNamedElement ?: return

    val what = candidate.renderDescription()
    val where = candidate.representativeContainer()?.renderDescription() ?: return
    val message = KotlinBundle.message("text.0.already.declared.in.1", what, where).capitalize()
    result += BasicUnresolvableCollisionUsageInfo(candidate, candidate, message)
}

context(KaSession)
@OptIn(KaExperimentalApi::class)
private fun areSameSignatures(candidateSymbol: KaFunctionSymbol, symbol: KaFunctionSymbol) : Boolean {
    return areSameSignatures(candidateSymbol.receiverType, symbol.receiverType, candidateSymbol.valueParameters.map { it.returnType }, symbol.valueParameters.map { it.returnType }, candidateSymbol.contextReceivers, symbol.contextReceivers)
}

context(KaSession)
@OptIn(KaExperimentalApi::class)
private fun areSameSignatures(candidateSymbol: KaPropertySymbol, symbol: KaFunctionSymbol) : Boolean {
    val type = candidateSymbol.returnType
    if (type is KaFunctionType &&
        areSameSignatures(type.receiverType, symbol.receiverType, type.parameterTypes, symbol.valueParameters.map { it.returnType }, candidateSymbol.contextReceivers, symbol.contextReceivers)) {
        return true
    }
    return false
}

context(KaSession)
@KaExperimentalApi
fun areSameSignatures(
    receiverType1: KaType?,
    receiverType2: KaType?,
    parameterTypes1: List<KaType>,
    parameterTypes2: List<KaType>,
    c1: List<KaContextReceiver>,
    c2: List<KaContextReceiver>,
): Boolean {
  return areTypesTheSame(receiverType1, receiverType2) &&
          parameterTypes1.size == parameterTypes2.size && parameterTypes1.zip(parameterTypes2).all { (p1, p2) -> areTypesTheSame(p1, p2) } &&
          c1.size == c2.size && c1.zip(c2).all { (c1, c2) -> c1.type.semanticallyEquals(c2.type) }
}

context(KaSession)
private fun areTypesTheSame(t1: KaType?, t2: KaType?): Boolean {
  if (t1 === t2) return true
  if (t2 == null) return false
  return t1?.semanticallyEquals(t2) == true
}

fun PsiElement.representativeContainer(): PsiNamedElement? = when (this) {
  is KtDeclaration -> {
      containingClassOrObject
          ?: getStrictParentOfType<KtNamedDeclaration>()
          ?: JavaPsiFacade.getInstance(project).findPackage(containingKtFile.packageFqName.asString())
  }
  is PsiMember -> {
      containingClass
          ?: (containingFile as? PsiJavaFile)?.packageName?.let { JavaPsiFacade.getInstance(project).findPackage(it) }
  }
  else -> {
      null
  }
}

fun PsiNamedElement.renderDescription(): String {
  val type = UsageViewUtil.getType(this)
  if (name == null || name!!.startsWith("<")) return type
  return "$type '$name'".trim()
}

private fun checkRedeclarationConflictsInInheritors(declaration: KtNamedDeclaration, newName: String, result: MutableList<UsageInfo>) {
    if (!declaration.hasModifier(KtTokens.PRIVATE_KEYWORD)) {

        if (declaration.name == newName || !PsiNameHelper.getInstance(declaration.project).isIdentifier(newName)) return

        val initialPsiClass = declaration.containingClassOrObject?.toLightClass()
        if (initialPsiClass != null) {
            val elementFactory = JavaPsiFacade.getInstance(declaration.project).elementFactory
            val methods = declaration.toLightMethods().map {
                it as KtLightMethod

                val methodName = accessorNameByPropertyName(newName, it) ?: newName

                val parametersListText =
                    it.parameterList.parameters.joinToString(", ") { p -> p.type.canonicalText + " " + p.name }
                elementFactory.createMethodFromText("void $methodName($parametersListText){}", declaration)
            }

            fun reportAccidentalOverride(candidate: PsiNamedElement) {
                val what = UsageViewUtil.getType(declaration).capitalize()
                val withWhat = candidate.renderDescription()
                val where = candidate.representativeContainer()?.renderDescription() ?: return
                val message = KotlinBundle.message("text.0.will.clash.with.existing.1.in.2", what, withWhat, where)
                result += BasicUnresolvableCollisionUsageInfo(candidate, candidate, message)
            }

            val propertyName = if (declaration is KtProperty || declaration is KtParameter && declaration.hasValOrVar()) newName else null
            ClassInheritorsSearch.search(initialPsiClass).asIterable().forEach { current ->
                methods.mapNotNull { current.findMethodBySignature(it, false)?.unwrapped as? PsiNamedElement }.forEach(::reportAccidentalOverride)
                if (propertyName != null) {
                    (current.unwrapped as? KtClassOrObject)?.findPropertyByName(propertyName)?.let { reportAccidentalOverride(it) }
                }
            }
        }
    }
}

fun registerRetargetJobOnPotentialCandidates(
    declaration: KtNamedDeclaration,
    name: String,
    filterCandidate: (KaDeclarationSymbol) -> Boolean,
    retargetJob: (KaDeclarationSymbol) -> Unit
) {
    analyze(declaration) {
        val declarationSymbol = declaration.symbol

        val nameAsName = Name.identifier(name)
        fun KaScope.processScope(containingSymbol: KaDeclarationSymbol?) {
            findSiblingsByName(declarationSymbol, nameAsName, containingSymbol).filter { filterCandidate(it) }.forEach(retargetJob)
        }

        var classOrObjectSymbol = declarationSymbol.containingDeclaration
        val block = declaration.parent as? KtBlockExpression
        if (block != null) {
            classOrObjectSymbol = declaration.getParentOfType<KtFunction>(true)?.symbol as? KaFunctionSymbol
            classOrObjectSymbol?.valueParameters?.filter { it.name.asString() == name }?.filter { filterCandidate(it) }?.forEach(retargetJob)
            block.statements.mapNotNull {
                if (it == declaration || it.name != name) return@mapNotNull null
                val isAccepted = when (declarationSymbol) {
                    is KaClassSymbol -> it is KtClassOrObject
                    is KaPropertySymbol, is KaJavaFieldSymbol, is KaLocalVariableSymbol -> it is KtProperty
                    is KaFunctionSymbol -> it is KtNamedFunction
                    else -> false
                }
                if (!isAccepted) return@mapNotNull null
                (it as? KtDeclaration)?.symbol?.takeIf { filterCandidate(it) }
            }.forEach(retargetJob)
        }

        while (classOrObjectSymbol != null) {
            (classOrObjectSymbol as? KaClassSymbol)?.memberScope?.processScope(classOrObjectSymbol)

            val companionObject = (classOrObjectSymbol as? KaNamedClassSymbol)?.companionObject
            companionObject?.memberScope?.processScope(companionObject)

            classOrObjectSymbol = classOrObjectSymbol.containingDeclaration
        }

        val file = declaration.containingKtFile
        findPackage(file.packageFqName)?.packageScope?.processScope(null)
        file.importingScopeContext.compositeScope().processScope(null)
    }
}