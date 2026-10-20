// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.conflicts

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiNameHelper
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.descendantsOfType
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.base.KaContextReceiver
import org.jetbrains.kotlin.analysis.api.components.compositeScope
import org.jetbrains.kotlin.analysis.api.components.importingScopeContext
import org.jetbrains.kotlin.analysis.api.javaInterop.asPsiClass
import org.jetbrains.kotlin.analysis.api.scopes.KaScope
import org.jetbrains.kotlin.analysis.api.scopes.combinedDeclaredMemberScope
import org.jetbrains.kotlin.analysis.api.scopes.combinedMemberScope
import org.jetbrains.kotlin.analysis.api.scopes.declaredMemberScope
import org.jetbrains.kotlin.analysis.api.scopes.memberScope
import org.jetbrains.kotlin.analysis.api.scopes.packageScope
import org.jetbrains.kotlin.analysis.api.scopes.staticDeclaredMemberScope
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaContextParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaJavaFieldSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaLocalVariableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPackageSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolLocation
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.classSymbol
import org.jetbrains.kotlin.analysis.api.symbols.containingDeclaration
import org.jetbrains.kotlin.analysis.api.symbols.findPackage
import org.jetbrains.kotlin.analysis.api.symbols.namedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.symbols.typeParameters
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.allSupertypes
import org.jetbrains.kotlin.analysis.api.types.defaultType
import org.jetbrains.kotlin.analysis.api.types.expandedSymbol
import org.jetbrains.kotlin.analysis.api.types.semanticallyEquals
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.asJava.accessorNameByPropertyName
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.rename.BasicUnresolvableCollisionUsageInfo
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVariableDeclaration
import org.jetbrains.kotlin.psi.KtWhenExpression
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
        checkDeclarationNewNameConflicts(
            declaration,
            Name.identifier(newName),
            result,
            filterCandidate = { filterCandidates(symbol, it, forRedeclaration = true) },
        )
        checkRedeclarationConflictsInInheritors(declaration, newName, result)
    }
}

context(_: KaSession)
private fun areSameCallSignatures(property: KaPropertySymbol, function: KaFunctionSymbol, forRedeclaration: Boolean): Boolean {
    val functionType = property.returnType as? KaFunctionType ?: return false
    val sameReceivers = if (forRedeclaration) haveSameCallReceivers(property, function, functionType.receiverType)
                        else areTypesTheSame(functionType.receiverType, function.receiverType)
    return sameReceivers && areSameSignatures(
        null, null,
        functionType.parameterTypes,
        function.valueParameters.map { parameter -> parameter.returnType },
        property.contextReceivers,
        function.contextReceivers,
    )
}

context(_: KaSession)
private fun haveSameCallReceivers(
    first: KaCallableSymbol,
    second: KaCallableSymbol,
    firstAdditionalReceiver: KaType? = null,
): Boolean {
    val includeClassReceiver = first.containingDeclaration !is KaClassSymbol || second.containingDeclaration !is KaClassSymbol

    fun KaCallableSymbol.receivers(additionalReceiver: KaType?): List<KaType> = buildList {
        if (includeClassReceiver) {
            (containingDeclaration as? KaClassSymbol)?.defaultType?.let(::add)
        }
        receiverType?.let(::add)
        additionalReceiver?.let(::add)
    }

    val firstReceivers = first.receivers(firstAdditionalReceiver)
    val secondReceivers = second.receivers(null)
    return firstReceivers.size == secondReceivers.size &&
           firstReceivers.zip(secondReceivers).all { (firstType, secondType) -> areTypesTheSame(firstType, secondType) }
}

context(_: KaSession)
private fun haveSameCallSignatures(first: KaCallableSymbol, second: KaCallableSymbol, forRedeclaration: Boolean): Boolean = when (first) {
    is KaPropertySymbol -> when (second) {
        is KaPropertySymbol, is KaEnumEntrySymbol -> !forRedeclaration || haveSameCallReceivers(first, second)

        is KaFunctionSymbol -> areSameCallSignatures(first, second, forRedeclaration)
        else -> false
    }
    is KaFunctionSymbol -> when (second) {
        is KaPropertySymbol -> areSameCallSignatures(second, first, forRedeclaration)
        is KaFunctionSymbol -> {
            val sameReceivers = if (forRedeclaration) haveSameCallReceivers(first, second)
                                else areTypesTheSame(first.receiverType, second.receiverType)
            sameReceivers && areSameSignatures(
                null, null,
                first.valueParameters.map { it.returnType },
                second.valueParameters.map { it.returnType },
                first.contextReceivers,
                second.contextReceivers,
            )
        }
        else -> false
    }
    is KaEnumEntrySymbol -> when (second) {
        is KaEnumEntrySymbol,
             is KaPropertySymbol -> !forRedeclaration || haveSameCallReceivers(first, second)
        else -> false
    }
    else -> false
}

context(_: KaSession)
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

context(_: KaSession)
fun filterCandidates(symbol: KaDeclarationSymbol, candidateSymbol: KaDeclarationSymbol, forRedeclaration: Boolean = false): Boolean {
    if (symbol == candidateSymbol) return false
    if (forRedeclaration && symbol.containingDeclaration is KaClassSymbol && candidateSymbol.location == KaSymbolLocation.TOP_LEVEL &&
        (candidateSymbol !is KaCallableSymbol || candidateSymbol.receiverType == null)) {
        return false
    }
    if (symbol is KaVariableSymbol && candidateSymbol is KaVariableSymbol &&
        (symbol !is KaPropertySymbol || candidateSymbol !is KaPropertySymbol))  {
        return true
    }

    if (candidateSymbol is KaCallableSymbol && symbol is KaCallableSymbol) {
        if (forRedeclaration && symbol !is KaEnumEntrySymbol && candidateSymbol !is KaEnumEntrySymbol &&
            symbol.isCompanion != candidateSymbol.isCompanion) {
            return false
        }
        if (!haveSameCallSignatures(symbol, candidateSymbol, forRedeclaration)) {
            return false
        }
    }

    if (symbol is KaClassSymbol && candidateSymbol is KaFunctionSymbol &&
        symbol.declaredMemberScope.constructors.none { areSameSignatures(it, candidateSymbol) }) {
        return false
    }

    if (candidateSymbol is KaClassSymbol && symbol is KaFunctionSymbol &&
        candidateSymbol.declaredMemberScope.constructors.none { areSameSignatures(it, symbol) }) {
        return false
    }

    return true
}

context(_: KaSession)
fun checkDeclarationNewNameConflicts(
    declaration: KtNamedDeclaration,
    newName: Name,
    result: MutableList<UsageInfo>,
    filterCandidate: (KaDeclarationSymbol) -> Boolean,
) {
    fun getPotentialConflictCandidates(symbol: KaDeclarationSymbol, declaration: KtNamedDeclaration, newName: Name): Sequence<KaDeclarationSymbol> {
        val containingSymbol = symbol.containingDeclaration ?: findPackage(declaration.containingKtFile.packageFqName)

        if (symbol is KaValueParameterSymbol || symbol is KaContextParameterSymbol || symbol is KaLocalVariableSymbol) {
            val functionLikeSymbol = containingSymbol as? KaFunctionSymbol ?: return emptySequence()
            val functionPsi = functionLikeSymbol.psi

            val block = when {
                declaration is KtParameter -> {
                    val parent = declaration.parent
                    parent as? KtForExpression ?: functionPsi
                }
                else -> {
                    val parent = ((declaration.parent as? KtDestructuringDeclaration) ?: declaration).parent
                    parent as? KtBlockExpression ?: parent as? KtWhenExpression
                }
            }

            val locals = block?.descendantsOfType<KtVariableDeclaration>()
                ?.filter { it.nameAsName == newName && (block !is KtBlockExpression || it.parent == block) }
                ?.map { it.symbol } ?: emptySequence()
            val destructuringEntries = block?.descendantsOfType<KtDestructuringDeclarationEntry>()
                ?.filter { it.nameAsName == newName && (block !is KtBlockExpression || it.parent?.parent == block) }
                ?.map { it.symbol } ?: emptySequence()
            val parameters =
                if (declaration is KtParameter || block?.parent == functionPsi) functionLikeSymbol.valueParameters.filter { it.name == newName }.asSequence()
                else emptySequence()
            val enclosingVariables = generateSequence(declaration.parent) { it.parent }
                .takeWhile { it != functionPsi }
                .mapNotNull {
                    when (it) {
                        is KtWhenExpression -> it.subjectVariable
                        is KtForExpression -> it.loopParameter
                        else -> null
                    }
                }
                .filter { it.nameAsName == newName }
                .map { it.symbol }
            return parameters + locals + destructuringEntries + enclosingVariables
        }

        if (symbol is KaTypeParameterSymbol) {
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

        fun KaDeclarationSymbol.isTopLevelPrivate(): Boolean = this.visibility == KaSymbolVisibility.PRIVATE && this.location == KaSymbolLocation.TOP_LEVEL

        fun isInSameFile(s1: KaDeclarationSymbol, s2: KaDeclarationSymbol): Boolean = s1.psi?.containingFile == s2.psi?.containingFile

        return when (containingSymbol) {
            is KaClassSymbol -> {
                if (symbol is KaClassifierSymbol) {
                    //allow shadowing classes in super
                    containingSymbol.combinedDeclaredMemberScope
                } else {
                    containingSymbol.combinedMemberScope
                }.findSiblingsByName(symbol, newName) +
                        findPackage(declaration.containingKtFile.packageFqName)?.packageScope?.findSiblingsByName(symbol, newName)?.filter {
                            !symbol.isTopLevelPrivate() && !it.isTopLevelPrivate() || isInSameFile(symbol, it)
                        }.orEmpty()
            }

            is KaPackageSymbol -> {

                ((symbol as? KaCallableSymbol)?.receiverType?.symbol as? KaClassSymbol)?.combinedMemberScope?.findSiblingsByName(symbol, newName).orEmpty() + containingSymbol.packageScope.findSiblingsByName(symbol, newName).filter {
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
                        is KaPropertySymbol, is KaJavaFieldSymbol -> it is KtProperty || it is KtDestructuringDeclarationEntry
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

    val candidates = mutableListOf<Pair<KaDeclarationSymbol, PsiNamedElement?>>()
    getPotentialConflictCandidates(declaration, newName)
        .filter(filterCandidate)
        .forEach { candidates += it to null }
    for ((candidate, container) in candidates) {
        if (container != null) registerAlreadyDeclaredConflict(candidate, container, result)
        else registerAlreadyDeclaredConflict(candidate, result)
    }
}

fun checkNewPropertyConflicts(
    containingClass: KtClassOrObject,
    newName: String,
    result: MutableList<UsageInfo>,
) {
    analyze(containingClass) {
        val containingSymbol = containingClass.namedClassSymbol ?: return
        val potentialCandidates = containingSymbol
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
    registerAlreadyDeclaredConflict(candidateSymbol, candidate.representativeContainer() ?: return, result)
}

fun registerAlreadyDeclaredConflict(candidateSymbol: KaDeclarationSymbol, container: PsiNamedElement, result: MutableList<UsageInfo>) {
    val candidate = candidateSymbol.psi as? PsiNamedElement ?: return

    val what = candidate.renderDescription()
    val where = container.renderDescription()
    @Suppress("HardCodedStringLiteral")
    val message = KotlinBundle.message("text.0.already.declared.in.1", what, where).capitalize()
    result += BasicUnresolvableCollisionUsageInfo(candidate, candidate, message)
}

context(_: KaSession)
private fun areSameSignatures(candidateSymbol: KaFunctionSymbol, symbol: KaFunctionSymbol) : Boolean {
    return areSameSignatures(candidateSymbol.receiverType, symbol.receiverType, candidateSymbol.valueParameters.map { it.returnType }, symbol.valueParameters.map { it.returnType }, candidateSymbol.contextReceivers, symbol.contextReceivers)
}

@KaExperimentalApi
context(_: KaSession)
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

context(_: KaSession)
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

context(_: KaSession)
private fun checkRedeclarationConflictsInInheritors(declaration: KtNamedDeclaration, newName: String, result: MutableList<UsageInfo>) {
    if (!declaration.hasModifier(KtTokens.PRIVATE_KEYWORD)) {

        if (declaration.name == newName || !PsiNameHelper.getInstance(declaration.project).isIdentifier(newName)) return

        val initialPsiClass = declaration.containingClassOrObject?.classSymbol?.asPsiClass()
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

context(_: KaSession)
fun registerRetargetJobOnPotentialCandidates(
    declaration: KtNamedDeclaration,
    name: String,
    filterCandidate: (KaDeclarationSymbol) -> Boolean,
    retargetJob: (KaDeclarationSymbol) -> Unit
) {
    val declarationSymbol = declaration.symbol

    val nameAsName = Name.identifier(name)
    fun KaScope.processScope(containingSymbol: KaDeclarationSymbol?) {
        findSiblingsByName(declarationSymbol, nameAsName, containingSymbol).filter { filterCandidate(it) }.forEach(retargetJob)
    }

    var classOrObjectSymbol = declarationSymbol.containingDeclaration
    val processedInheritedStaticMethods = mutableSetOf<PsiElement>()
    val block = declaration.parent as? KtBlockExpression
    if (block != null) {
        classOrObjectSymbol = declaration.getParentOfType<KtFunction>(true)?.symbol as? KaFunctionSymbol
        classOrObjectSymbol?.valueParameters?.filter { it.name.asString() == name }?.filter { filterCandidate(it) }?.forEach(retargetJob)
        block.statements.mapNotNull {
            if (it == declaration || it.name != name) return@mapNotNull null
            val isAccepted = when (declarationSymbol) {
                is KaClassSymbol -> it is KtClassOrObject
                is KaPropertySymbol, is KaJavaFieldSymbol, is KaLocalVariableSymbol -> it is KtProperty || it is KtDestructuringDeclarationEntry
                is KaFunctionSymbol -> it is KtNamedFunction
                else -> false
            }
            if (!isAccepted) return@mapNotNull null
            (it as? KtDeclaration)?.symbol?.takeIf { filterCandidate(it) }
        }.forEach(retargetJob)
    }

    while (classOrObjectSymbol != null) {
        val classSymbol = classOrObjectSymbol as? KaClassSymbol
        classSymbol?.combinedMemberScope?.processScope(classOrObjectSymbol)
        if (classSymbol != null && declarationSymbol is KaFunctionSymbol) {
            classSymbol.defaultType.allSupertypes
                .mapNotNull { it.expandedSymbol }
                .flatMap { it.staticDeclaredMemberScope.callables(nameAsName) }
                .filter { filterCandidate(it) }
                .forEach { candidate ->
                    if (candidate.psi?.let(processedInheritedStaticMethods::add) == true) retargetJob(candidate)
                }
        }

        val companionObject = (classOrObjectSymbol as? KaNamedClassSymbol)?.companionObject
        companionObject?.memberScope?.processScope(companionObject)

        classOrObjectSymbol = classOrObjectSymbol.containingDeclaration
    }

    val file = declaration.containingKtFile
    findPackage(file.packageFqName)?.packageScope?.processScope(null)
    file.importingScopeContext.compositeScope().processScope(null)
}
