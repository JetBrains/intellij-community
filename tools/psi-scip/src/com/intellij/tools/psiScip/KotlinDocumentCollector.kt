// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.psiScip

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtLabelReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtTypeParameter

/**
 * Collects the SCIP document of one Kotlin file by the rules of [JavaDocumentCollector]. Call [collect] in a read action in smart mode.
 *
 * - A definition has the name range and an `enclosing_range` from the first child after the KDoc to the end of the declaration;
 *   a primary constructor is defined at the class name, a secondary one at `constructor`, an object literal at `object`.
 *   The symbols are [KotlinSymbols]'; a local variable or function, a non-property parameter and a type parameter are locals.
 * - A reference has the range of the reference name. A reference with more than one resolve candidate is never written.
 * - A call has [ROLE_CALL] on the callee name: `foo()`, `a.foo()`, `Foo()`, and the name of an infix call (`a eq b`).
 *   A constructor call writes the class without the bit and the constructor with it; a class without an explicit
 *   constructor alone carries the bit. An operator (`a + b`, `a[i]`, a delegate) has no name and writes nothing.
 * - A callable reference, such as `bar` of `Foo::bar`, has [ROLE_METHOD_REF] and never [ROLE_CALL].
 * - An occurrence inside KDoc has [ROLE_DOC] and never [ROLE_CALL] or [ROLE_METHOD_REF].
 * - A package reference, a label and a reference without a symbol (a local of another file) are never written; the counters of
 *   [JavaDocumentCollector] count them.
 */
internal class KotlinDocumentCollector(private val file: KtFile, private val relativePath: String) {
  val stats: ExportStats = ExportStats()

  private val lines = LineMap(file.viewProvider.contents)
  private val occurrences = ArrayList<ScipOccurrence>()
  private val symbols = HashMap<String, ScipSymbolInformation>()
  private val locals = HashMap<PsiElement, String>()

  fun collect(): ScipDocument {
    file.accept(Visitor())
    val sorted = occurrences.sortedWith(OCCURRENCE_ORDER)
    val unique = ArrayList<ScipOccurrence>(sorted.size)
    for (occurrence in sorted) {
      val last = unique.lastOrNull()
      if (last != null && OCCURRENCE_ORDER.compare(last, occurrence) == 0) continue
      unique.add(occurrence)
    }
    return ScipDocument(relativePath, "kotlin", unique, symbols.values.sortedWith(SYMBOL_ORDER))
  }

  private inner class Visitor : KtTreeVisitorVoid() {
    override fun visitElement(element: PsiElement) {
      if (element is KDocName) {
        docReference(element)
      }
      super.visitElement(element)
    }

    override fun visitClassOrObject(classOrObject: KtClassOrObject) {
      if (classOrObject is KtEnumEntry) {
        defineMember(classOrObject, classOrObject.nameIdentifier, ScipKind.EnumMember, emptyList())
      }
      else {
        defineClass(classOrObject)
      }
      super.visitClassOrObject(classOrObject)
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
      if (function.isLocal) {
        defineLocal(function, function.nameIdentifier)
      }
      else {
        val supers = function.toLightMethods().firstOrNull()?.findSuperMethods()?.mapNotNull(PsiSymbols::of) ?: emptyList()
        defineMember(function, function.nameIdentifier, ScipKind.Method, supers)
      }
      super.visitNamedFunction(function)
    }

    override fun visitPrimaryConstructor(constructor: KtPrimaryConstructor) {
      defineMember(constructor, constructor.getConstructorKeyword() ?: constructor.getContainingClassOrObject().nameIdentifier, ScipKind.Constructor, emptyList())
      super.visitPrimaryConstructor(constructor)
    }

    override fun visitSecondaryConstructor(constructor: KtSecondaryConstructor) {
      defineMember(constructor, constructor.getConstructorKeyword(), ScipKind.Constructor, emptyList())
      super.visitSecondaryConstructor(constructor)
    }

    override fun visitProperty(property: KtProperty) {
      if (property.isLocal) {
        defineLocal(property, property.nameIdentifier)
      }
      else {
        defineMember(property, property.nameIdentifier, ScipKind.Field, emptyList())
      }
      super.visitProperty(property)
    }

    override fun visitParameter(parameter: KtParameter) {
      if (parameter.hasValOrVar() && parameter.ownerFunction is KtConstructor<*>) {
        defineMember(parameter, parameter.nameIdentifier, ScipKind.Field, emptyList())
      }
      else {
        defineLocal(parameter, parameter.nameIdentifier)
      }
      super.visitParameter(parameter)
    }

    override fun visitTypeParameter(parameter: KtTypeParameter) {
      defineLocal(parameter, parameter.nameIdentifier)
      super.visitTypeParameter(parameter)
    }

    override fun visitDestructuringDeclarationEntry(entry: KtDestructuringDeclarationEntry) {
      defineLocal(entry, entry.nameIdentifier)
      super.visitDestructuringDeclarationEntry(entry)
    }

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
      codeReference(expression)
      super.visitSimpleNameExpression(expression)
    }
  }

  // ---------------------------------------------------------------- definitions

  private fun defineClass(classOrObject: KtClassOrObject) {
    val objectLiteral = classOrObject is KtObjectDeclaration && classOrObject.isObjectLiteral()
    val nameElement = (if (objectLiteral) (classOrObject as KtObjectDeclaration).getObjectKeyword() else classOrObject.nameIdentifier) ?: return noSymbol(classOrObject)
    if (isLocal(classOrObject)) return defineLocal(classOrObject, nameElement)
    val symbol = KotlinSymbols.of(classOrObject) ?: return noSymbol(classOrObject)
    define(symbol, nameElement.textRange, lines.enclosingRange(classOrObject))
    val light = classOrObject.toLightClass()
    val supers = light?.let { (it.extendsListTypes.asSequence() + it.implementsListTypes.asSequence()).mapNotNull { type -> type.resolve() }.mapNotNull(PsiSymbols::of).toList() } ?: emptyList()
    val kind = when {
      classOrObject is KtClass && classOrObject.isEnum() -> ScipKind.Enum
      classOrObject is KtClass && classOrObject.isInterface() -> ScipKind.Interface
      else -> ScipKind.Class
    }
    addSymbol(symbol, kind, classOrObject.name ?: symbol.substringAfterLast('/').removeSuffix("#"), supers)
  }

  private fun defineMember(declaration: KtDeclaration, nameElement: PsiElement?, kind: ScipKind, supers: List<String>) {
    if (nameElement == null) return
    if (isLocal(declaration)) return defineLocal(declaration, nameElement)
    val symbol = KotlinSymbols.of(declaration) ?: return noSymbol(declaration)
    define(symbol, nameElement.textRange, lines.enclosingRange(declaration))
    addSymbol(symbol, kind, declaration.name ?: nameElement.text, supers)
  }

  private fun defineLocal(element: PsiElement, nameIdentifier: PsiElement?) {
    if (nameIdentifier == null) return
    occurrences.add(ScipOccurrence(lines.range(nameIdentifier.textRange), localSymbol(element), ROLE_DEFINITION, null))
  }

  private fun define(symbol: String, nameRange: TextRange, enclosing: ScipRange) {
    stats.add(Counter.DEFINITIONS)
    occurrences.add(ScipOccurrence(lines.range(nameRange), symbol, ROLE_DEFINITION, enclosing))
  }

  private fun addSymbol(symbol: String, kind: ScipKind, displayName: String, supers: List<String>) {
    val relationships = supers.distinct().sorted().map { ScipRelationship(it, isReference = true, isImplementation = true) }
    // a second definition in this file is reported with the other duplicates by ExportSummary.findDuplicateSymbols
    symbols.put(symbol, ScipSymbolInformation(symbol, kind, displayName, relationships))
  }

  // ---------------------------------------------------------------- references

  private fun codeReference(expression: KtSimpleNameExpression) {
    if (expression is KtLabelReferenceExpression) return
    // an operator (`+`, `in`, `..`) is no name; an infix call (`a eq b`) is
    if (expression is KtOperationReferenceExpression && expression.getReferencedNameElementType() != KtTokens.IDENTIFIER) return
    if (PsiTreeUtil.getParentOfType(expression, KtPackageDirective::class.java) != null) return
    val reference = expression.references.firstOrNull { it is PsiPolyVariantReference } as PsiPolyVariantReference? ?: return
    stats.add(Counter.REFS)
    val targets = reference.multiResolve(false).mapNotNull { it.element }.distinct()
    val target = targets.singleOrNull()
    if (target == null) {
      unresolved(expression, targets.size)
      return
    }
    var roles = 0
    val parent = expression.parent
    if (parent is KtCallableReferenceExpression && parent.callableReference == expression) {
      roles = roles or ROLE_METHOD_REF
    }
    else if (expression is KtOperationReferenceExpression || parent is KtCallExpression && parent.calleeExpression == expression) {
      roles = roles or ROLE_CALL
    }
    else if (target is PsiMethod && target !is KtLightElement<*, *> && !target.isConstructor) {
      // property syntax of a Java accessor (`file.name` runs `getName()`) is a call of it
      roles = roles or ROLE_CALL
    }
    if (PsiTreeUtil.getParentOfType(expression, KtImportDirective::class.java) != null) {
      roles = ROLE_IMPORT
    }
    val range = expression.getReferencedNameElement().textRange
    val owner = constructedClass(target)
    if (roles and ROLE_CALL != 0 && owner != null) {
      emitReference(expression, owner, range, roles and ROLE_CALL.inv())
    }
    emitReference(expression, target, range, roles)
  }

  /** A KDoc link, such as `Foo.bar` of `[Foo.bar]`: each segment is a reference of its own. */
  private fun docReference(name: KDocName) {
    if (PsiTreeUtil.getParentOfType(name, KDoc::class.java) == null) return
    val reference = name.references.firstOrNull() ?: return
    stats.add(Counter.REFS)
    val targets = (reference as? PsiPolyVariantReference)?.multiResolve(false)?.mapNotNull { it.element }?.distinct() ?: listOfNotNull(reference.resolve())
    val target = targets.singleOrNull()
    if (target == null) {
      unresolved(name, targets.size)
      return
    }
    emitReference(name, target, name.textRange.cutOut(reference.rangeInElement), ROLE_DOC)
  }

  private fun emitReference(site: PsiElement, target: PsiElement, range: TextRange, roles: Int) {
    val symbol = targetSymbol(site, target) ?: return
    stats.add(Counter.RESOLVED)
    if (roles and ROLE_CALL != 0) stats.add(Counter.CALLS)
    if (roles and ROLE_DOC != 0) stats.add(Counter.DOC_REFS)
    occurrences.add(ScipOccurrence(lines.range(range), symbol, roles, null))
  }

  private fun targetSymbol(site: PsiElement, target: PsiElement): String? {
    if (target is PsiMember && JavaSymbols.isArrayMember(target)) {
      stats.add(Counter.ARRAY_REFS)
      return null
    }
    val origin = (target as? KtLightElement<*, *>)?.kotlinOrigin ?: target
    val symbol = when {
      target is PsiPackage -> {
        stats.add(Counter.PACKAGE_REFS)
        return null
      }
      isLocal(origin) -> {
        if (origin.containingFile?.originalFile != file && origin.isPhysical) {
          stats.site(Counter.FOREIGN_LOCAL, site(site))
          return null
        }
        localSymbol(origin)
      }
      origin is KtDeclaration || target is PsiClass || target is PsiMethod || target is PsiField -> PsiSymbols.of(target)
      else -> null
    }
    if (symbol == null) {
      stats.site(Counter.NO_SYMBOL, site(site) + " -> " + target.javaClass.simpleName)
    }
    return symbol
  }

  private fun unresolved(site: PsiElement, candidates: Int) {
    stats.site(if (candidates > 1) Counter.AMBIGUOUS else Counter.UNRESOLVED, site(site))
  }

  private fun localSymbol(element: PsiElement): String = locals.getOrPut(element) { "local ${locals.size}" }

  private fun noSymbol(element: PsiElement) {
    stats.site(Counter.NO_SYMBOL, site(element) + " " + element.javaClass.simpleName)
  }

  private fun site(element: PsiElement): String {
    val range = lines.range(element.textRange)
    return "$relativePath:${range.startLine + 1}:${range.startColumn + 1} ${element.text.take(80).replace('\n', ' ')}"
  }
}

/** The class a constructor call of [target] creates, when [target] is a constructor; a class itself stands for its implicit constructor. */
private fun constructedClass(target: PsiElement): PsiElement? {
  return when (target) {
    is KtConstructor<*> -> target.getContainingClassOrObject()
    is PsiMethod -> if (target.isConstructor) target.containingClass else null
    else -> null
  }
}

/**
 * A declaration that has a local symbol: a parameter that is no property, a local variable or function, a type parameter.
 * A local class and an object literal have a symbol of their binary name ([KotlinSymbols]), and so have their members.
 */
private fun isLocal(element: PsiElement): Boolean {
  return when (element) {
    is KtParameter -> !(element.hasValOrVar() && element.ownerFunction is KtConstructor<*>)
    is KtProperty -> element.isLocal
    is KtNamedFunction -> element.isLocal
    // a lambda stands for its implicit parameter `it`
    is KtFunctionLiteral, is KtDestructuringDeclarationEntry, is KtTypeParameter, is PsiTypeParameter, is PsiLocalVariable, is PsiParameter -> true
    else -> false
  }
}
