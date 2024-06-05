// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.conflicts

import com.intellij.psi.*
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.descendantsOfType
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewUtil
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.scopes.KtScope
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolKind
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithKind
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithTypeParameters
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaSymbolWithVisibility
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.asJava.accessorNameByPropertyName
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.rename.BasicUnresolvableCollisionUsageInfo
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.findPropertyByName
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * Checks for conflicts with members declared in the same scope if [declaration] would be named as [newName].
 */
fun checkRedeclarationConflicts(declaration: KtNamedDeclaration, newName: String, result: MutableList<UsageInfo>) {
    analyze(declaration) {
        val symbol = declaration.getSymbol()
        checkDeclarationNewNameConflicts(declaration, Name.identifier(newName), result) {
            filterCandidates(symbol, it)
        }
    }
    checkRedeclarationConflictsInInheritors(declaration, newName, result)
}

context(KaSession)
fun KtScope.findSiblingsByName(
    symbol: KaDeclarationSymbol,
    newName: Name,
    containingSymbol: KaDeclarationSymbol? = symbol.getContainingSymbol()
): Sequence<KaDeclarationSymbol> {
    if (symbol is KaConstructorSymbol) {
        return getConstructors().filter { symbol != it }
    }
    val callables = getCallableSymbols(newName).filter { callable ->
        symbol != callable &&
                ((callable as? KaSymbolWithVisibility)?.visibility != Visibilities.Private || callable.getContainingSymbol() == containingSymbol)
    }

    val classifierSymbols = getClassifierSymbols(newName)
    if (symbol is KaFunctionLikeSymbol) {
        return (classifierSymbols.flatMap { (it as? KaClassOrObjectSymbol)?.getDeclaredMemberScope()?.getConstructors() ?: emptySequence() } + callables)
    }

    return (classifierSymbols + callables)
}

context(KaSession)
fun filterCandidates(symbol: KaDeclarationSymbol, candidateSymbol: KaDeclarationSymbol): Boolean {
    if (candidateSymbol is KaFunctionLikeSymbol) {
        val skipCandidate = when (symbol) {
            is KaFunctionLikeSymbol -> !areSameSignatures(candidateSymbol, symbol)
            is KtPropertySymbol -> !areSameSignatures(symbol, candidateSymbol)
            is KaClassOrObjectSymbol -> symbol.getDeclaredMemberScope().getConstructors().none { areSameSignatures(it, candidateSymbol) }
            else -> false
        }

        return !skipCandidate
    }

    if (candidateSymbol is KtPropertySymbol && symbol is KaFunctionLikeSymbol && !areSameSignatures(candidateSymbol, symbol)) {
        return false
    }

    if (candidateSymbol is KaClassOrObjectSymbol && symbol is KaFunctionLikeSymbol) {
        if (candidateSymbol.getDeclaredMemberScope().getConstructors().none { areSameSignatures(it, symbol) }) {
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
    fun getPotentialConflictCandidates(symbol: KaDeclarationSymbol, declaration: KtNamedDeclaration, newName: Name): Sequence<KaDeclarationSymbol> {
        val containingSymbol = symbol.getContainingSymbol() ?: getPackageSymbolIfPackageExists(declaration.containingKtFile.packageFqName)

        if (symbol is KtValueParameterSymbol) {
            val functionLikeSymbol = containingSymbol as KaFunctionLikeSymbol
            val locals = functionLikeSymbol.psi?.descendantsOfType<KtVariableDeclaration>()?.filter { it.nameAsName == newName }
                ?.mapNotNull { it.getSymbol() }?.asSequence() ?: emptySequence()
            return functionLikeSymbol.valueParameters.filter { it.name == newName }.asSequence() + locals
        }

        if (symbol is KaTypeParameterSymbol) {
            val typeParameters = (containingSymbol as? KtSymbolWithTypeParameters)?.typeParameters?.filter { it.name == newName }?.asSequence() ?: return emptySequence()

            val outerTypeParameters = generateSequence<KtClassOrObject>(declaration.getStrictParentOfType()) {
                if (it is KtClass && it.isInner()) it.getStrictParentOfType() else null
            }.flatMap { outerClass ->
                outerClass.typeParameters.filter { pName -> pName.nameAsName == newName }.map { p -> p.getSymbol() }
            }

            val innerTypeParameters = (containingSymbol.psi as? KtElement)?.let { currentPsi ->
                PsiTreeUtil.findChildrenOfType(currentPsi, KtClass::class.java)
                    .filter { it.isInner() }
                    .flatMap { innerClass -> innerClass.typeParameters.mapNotNull { p -> if (p.nameAsName == newName) p.getSymbol() else null } }
            }.orEmpty()

            return typeParameters + outerTypeParameters + innerTypeParameters
        }

        return when (containingSymbol) {
            is KaClassOrObjectSymbol -> {
                if (symbol is KaClassifierSymbol) {
                    //allow shadowing classes in super
                    containingSymbol.getCombinedDeclaredMemberScope()
                } else {
                    containingSymbol.getCombinedMemberScope()
                }.findSiblingsByName(symbol, newName)
            }

            is KtPackageSymbol -> {
                fun KaDeclarationSymbol.isTopLevelPrivate(): Boolean = (this as? KaSymbolWithVisibility)?.visibility == Visibilities.Private && (this as? KtSymbolWithKind)?.symbolKind == KtSymbolKind.TOP_LEVEL

                fun isInSameFile(s1: KaDeclarationSymbol, s2: KaDeclarationSymbol): Boolean = s1.psi?.containingFile == s2.psi?.containingFile

                containingSymbol.getPackageScope().findSiblingsByName(symbol, newName).filter {
                    !symbol.isTopLevelPrivate() && !it.isTopLevelPrivate() || isInSameFile(symbol, it)
                }
            }
            else -> {
                val block = declaration.parent as? KtBlockExpression ?: return emptySequence()
                val functionParameters = (declaration.getParentOfType<KtFunction>(true)?.getSymbol() as? KaFunctionLikeSymbol)?.valueParameters?.filter { it.name == newName } ?: emptyList()
                (block.statements.mapNotNull {
                    if (it.name != newName.asString()) return@mapNotNull null
                    val isAccepted = when (symbol) {
                        is KaClassOrObjectSymbol -> it is KtClassOrObject
                        is KtVariableSymbol -> it is KtProperty
                        is KaFunctionLikeSymbol -> it is KtNamedFunction
                        else -> false
                    }
                    if (!isAccepted) return@mapNotNull null
                    (it as? KtDeclaration)?.getSymbol()
                } + functionParameters).asSequence()
            }
        }
    }

    fun getPotentialConflictCandidates(declaration: KtNamedDeclaration, newName: Name): Sequence<KaDeclarationSymbol> {
        val declarationSymbol = declaration.getSymbol()
        val symbol = declarationSymbol.let {
            (it as? KtValueParameterSymbol?)?.generatedPrimaryConstructorProperty ?: it
        }

        var potentialCandidates = getPotentialConflictCandidates(symbol, declaration, newName)
        if (declarationSymbol is KtValueParameterSymbol && symbol is KtPropertySymbol) {
            val functionLikeSymbol = declarationSymbol.getContainingSymbol() as? KaFunctionLikeSymbol
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
        val containingSymbol = containingClass.getNamedClassOrObjectSymbol() ?: return
        var potentialCandidates = containingSymbol
            .getCombinedMemberScope()
            .findSiblingsByName(containingSymbol, Name.identifier(newName), containingSymbol)
            .filter { candidateSymbol -> candidateSymbol !is KaFunctionLikeSymbol }
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
private fun areSameSignatures(candidateSymbol: KaFunctionLikeSymbol, symbol: KaFunctionLikeSymbol) : Boolean {
    return areSameSignatures(candidateSymbol.receiverType, symbol.receiverType, candidateSymbol.valueParameters.map { it.returnType }, symbol.valueParameters.map { it.returnType }, candidateSymbol.contextReceivers, symbol.contextReceivers)
}

context(KaSession)
private fun areSameSignatures(candidateSymbol: KtPropertySymbol, symbol: KaFunctionLikeSymbol) : Boolean {
    val type = candidateSymbol.returnType
    if (type is KtFunctionalType &&
        areSameSignatures(type.receiverType, symbol.receiverType, type.parameterTypes, symbol.valueParameters.map { it.returnType }, candidateSymbol.contextReceivers, symbol.contextReceivers)) {
        return true
    }
    return false
}

context(KaSession)
fun areSameSignatures(
    receiverType1: KtType?,
    receiverType2: KtType?,
    parameterTypes1: List<KtType>,
    parameterTypes2: List<KtType>,
    c1: List<org.jetbrains.kotlin.analysis.api.base.KtContextReceiver>,
    c2: List<org.jetbrains.kotlin.analysis.api.base.KtContextReceiver>,
): Boolean {
  return areTypesTheSame(receiverType1, receiverType2) &&
          parameterTypes1.size == parameterTypes2.size && parameterTypes1.zip(parameterTypes2).all { (p1, p2) -> areTypesTheSame(p1, p2) } &&
          c1.size == c2.size && c1.zip(c2).all { (c1, c2) -> c1.type.isEqualTo(c2.type) }
}

context(KaSession)
private fun areTypesTheSame(t1: KtType?, t2: KtType?): Boolean {
  if (t1 === t2) return true
  if (t2 == null) return false
  return t1?.isEqualTo(t2) == true
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
            ClassInheritorsSearch.search(initialPsiClass).forEach { current ->
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
        val declarationSymbol = declaration.getSymbol()

        val nameAsName = Name.identifier(name)
        fun KtScope.processScope(containingSymbol: KaDeclarationSymbol?) {
            findSiblingsByName(declarationSymbol, nameAsName, containingSymbol).filter { filterCandidate(it) }.forEach(retargetJob)
        }

        var classOrObjectSymbol = declarationSymbol.getContainingSymbol()
        val block = declaration.parent as? KtBlockExpression
        if (block != null) {
            classOrObjectSymbol = declaration.getParentOfType<KtFunction>(true)?.getSymbol() as? KaFunctionLikeSymbol
            classOrObjectSymbol?.valueParameters?.filter { it.name.asString() == name }?.filter { filterCandidate(it) }?.forEach(retargetJob)
            block.statements.mapNotNull {
                if (it.name != name) return@mapNotNull null
                val isAccepted = when (declarationSymbol) {
                    is KaClassOrObjectSymbol -> it is KtClassOrObject
                    is KtVariableSymbol -> it is KtProperty
                    is KaFunctionLikeSymbol -> it is KtNamedFunction
                    else -> false
                }
                if (!isAccepted) return@mapNotNull null
                (it as? KtDeclaration)?.getSymbol()?.takeIf { filterCandidate(it) }
            }.forEach(retargetJob)
        }

        while (classOrObjectSymbol != null) {
            (classOrObjectSymbol as? KaClassOrObjectSymbol)?.getMemberScope()?.processScope(classOrObjectSymbol)

            val companionObject = (classOrObjectSymbol as? KaNamedClassOrObjectSymbol)?.companionObject
            companionObject?.getMemberScope()?.processScope(companionObject)

            classOrObjectSymbol = classOrObjectSymbol.getContainingSymbol()
        }

        val file = declaration.containingKtFile
        getPackageSymbolIfPackageExists(file.packageFqName)?.getPackageScope()?.processScope(null)
        file.getImportingScopeContext().getCompositeScope().processScope(null)
    }
}