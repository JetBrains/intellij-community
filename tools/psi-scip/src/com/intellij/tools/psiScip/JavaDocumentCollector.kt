// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.psiScip

import com.intellij.openapi.util.TextRange
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiEnumConstantInitializer
import com.intellij.psi.PsiField
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaResolveResult
import com.intellij.psi.PsiImportStatementBase
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiMethodReferenceExpression
import com.intellij.psi.PsiNameValuePair
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiRecordComponent
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiVariable
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.light.LightDefaultConstructor
import com.intellij.psi.impl.light.LightRecordMethod
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.javadoc.PsiDocTagValue
import com.intellij.psi.util.JavaPsiRecordUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil

/**
 * Collects the SCIP document of one Java file. Call [collect] in a read action in smart mode.
 *
 * - A definition has the name range and an `enclosing_range` from the first child after the doc comment to the end of the declaration.
 * - A reference has the range of the reference name. A reference with more than one resolve candidate is never written.
 * - A call has [ROLE_CALL] on the callee name. A `new` expression writes the class without the bit and the constructor with it.
 *   When the class has no explicit constructor, the class alone carries the bit. See [constructorCallee].
 *   An enum constant writes the constructor that it calls at the constant name, see [enumConstantCall].
 * - An occurrence inside a doc comment has [ROLE_DOC] and never [ROLE_CALL].
 * - A package reference is never written. [Counter.PACKAGE_REFS] counts it.
 * - A reference to the synthetic array class or to its members, such as `length`, is never written. [Counter.ARRAY_REFS] counts it.
 */
internal class JavaDocumentCollector(private val file: PsiJavaFile, private val relativePath: String) {
  val stats: ExportStats = ExportStats()

  private val lineStarts = lineStarts(file.viewProvider.contents)
  private val occurrences = ArrayList<ScipOccurrence>()
  private val symbols = HashMap<String, ScipSymbolInformation>()
  private val locals = HashMap<PsiElement, String>()
  private var docDepth = 0

  fun collect(): ScipDocument {
    file.accept(Visitor())
    val sorted = occurrences.sortedWith(OCCURRENCE_ORDER)
    val unique = ArrayList<ScipOccurrence>(sorted.size)
    for (occurrence in sorted) {
      val last = unique.lastOrNull()
      if (last != null && OCCURRENCE_ORDER.compare(last, occurrence) == 0) continue
      unique.add(occurrence)
    }
    return ScipDocument(relativePath, "java", unique, symbols.values.sortedWith(SYMBOL_ORDER))
  }

  private inner class Visitor : JavaRecursiveElementWalkingVisitor() {
    override fun visitDocComment(comment: PsiDocComment) {
      docDepth++
      super.visitDocComment(comment)
    }

    override fun elementFinished(element: PsiElement) {
      if (element is PsiDocComment) {
        docDepth--
      }
    }

    override fun visitClass(aClass: PsiClass) {
      if (aClass is PsiTypeParameter) {
        defineLocal(aClass, aClass.nameIdentifier)
      }
      else {
        defineClass(aClass)
      }
      super.visitClass(aClass)
    }

    override fun visitMethod(method: PsiMethod) {
      defineMethod(method)
      super.visitMethod(method)
    }

    override fun visitVariable(variable: PsiVariable) {
      when (variable) {
        is PsiField -> defineField(variable)
        is PsiRecordComponent -> defineRecordComponent(variable)
        is PsiLocalVariable, is PsiParameter -> defineLocal(variable, variable.nameIdentifier)
      }
      super.visitVariable(variable)
    }

    override fun visitEnumConstant(enumConstant: PsiEnumConstant) {
      enumConstantCall(enumConstant)
      super.visitEnumConstant(enumConstant)
    }

    override fun visitReferenceElement(reference: PsiJavaCodeReferenceElement) {
      codeReference(reference)
      super.visitReferenceElement(reference)
    }

    override fun visitDocTagValue(value: PsiDocTagValue) {
      if (value !is PsiJavaCodeReferenceElement) {
        value.reference?.let(::otherReference)
      }
      super.visitDocTagValue(value)
    }

    override fun visitNameValuePair(pair: PsiNameValuePair) {
      if (pair.nameIdentifier != null) {
        pair.reference?.let(::otherReference)
      }
      super.visitNameValuePair(pair)
    }
  }

  // ---------------------------------------------------------------- definitions

  /**
   * An anonymous class is defined at the name of its base class, and its `new` expression is the enclosing range.
   * The class of an enum constant with a body, such as `FieldNamingPolicy$1`, is defined at the constant name,
   * and the constant is the enclosing range: the base class reference of such a class is a light element without a name.
   */
  private fun defineClass(psiClass: PsiClass) {
    val symbol = JavaSymbols.classSymbol(psiClass) ?: return noSymbol(psiClass)
    val nameElement = when (psiClass) {
      is PsiEnumConstantInitializer -> psiClass.enumConstant.nameIdentifier
      is PsiAnonymousClass -> psiClass.baseClassReference.referenceNameElement
      else -> psiClass.nameIdentifier
    } ?: return noSymbol(psiClass)
    val enclosing = when (psiClass) {
      is PsiEnumConstantInitializer -> psiClass.enumConstant
      is PsiAnonymousClass -> psiClass.parent.takeIf { it is PsiNewExpression } ?: psiClass
      else -> psiClass
    }
    define(symbol, nameElement.textRange, enclosingRange(enclosing))
    val supers = when (psiClass) {
      is PsiAnonymousClass -> listOfNotNull(psiClass.baseClassType.resolve())
      else -> (psiClass.extendsListTypes.asSequence() + psiClass.implementsListTypes.asSequence()).mapNotNull { it.resolve() }.toList()
    }
    val name = if (psiClass is PsiAnonymousClass) symbol.substringAfterLast('/').removeSuffix("#") else psiClass.name ?: ""
    addSymbol(symbol, JavaSymbols.kindOf(psiClass), name, supers.mapNotNull(JavaSymbols::classSymbol))
    if (psiClass.isRecord) {
      defineImplicitCanonicalConstructor(psiClass)
    }
  }

  private fun defineMethod(method: PsiMethod) {
    val symbol = JavaSymbols.methodSymbol(method) ?: return noSymbol(method)
    val nameIdentifier = method.nameIdentifier ?: return
    define(symbol, nameIdentifier.textRange, enclosingRange(method))
    val supers = if (method.isConstructor) emptyList() else method.findSuperMethods().mapNotNull(JavaSymbols::methodSymbol)
    addSymbol(symbol, if (method.isConstructor) ScipKind.Constructor else ScipKind.Method, method.name, supers)
  }

  private fun defineField(field: PsiField) {
    val symbol = JavaSymbols.fieldSymbol(field) ?: return noSymbol(field)
    val nameIdentifier = field.nameIdentifier
    define(symbol, nameIdentifier.textRange, enclosingRange(field))
    addSymbol(symbol, if (field is PsiEnumConstant) ScipKind.EnumMember else ScipKind.Field, field.name, emptyList())
  }

  /**
   * A record component defines the field of the component and, without an explicit accessor, the implicit accessor.
   * Both definitions have the name of the component, and the component is the enclosing range.
   */
  private fun defineRecordComponent(component: PsiRecordComponent) {
    val record = component.containingClass ?: return
    val nameIdentifier = component.nameIdentifier ?: return
    val enclosing = enclosingRange(component)
    JavaPsiRecordUtil.getFieldForComponent(component)?.let { field ->
      JavaSymbols.fieldSymbol(field)?.let { symbol ->
        define(symbol, nameIdentifier.textRange, enclosing)
        addSymbol(symbol, ScipKind.Field, field.name, emptyList())
      }
    }
    val accessor = record.findMethodsByName(component.name, false).firstOrNull { it is LightRecordMethod } ?: return
    val symbol = JavaSymbols.methodSymbol(accessor) ?: return
    define(symbol, nameIdentifier.textRange, enclosing)
    addSymbol(symbol, ScipKind.Method, accessor.name, accessor.findSuperMethods().mapNotNull(JavaSymbols::methodSymbol))
  }

  /** An implicit canonical constructor of a record is defined at the record name, and the record header is its enclosing range. */
  private fun defineImplicitCanonicalConstructor(record: PsiClass) {
    val constructor = JavaPsiRecordUtil.findCanonicalConstructor(record) ?: return
    if (constructor.isPhysical) return
    val header = record.recordHeader ?: return
    val nameIdentifier = record.nameIdentifier ?: return
    val symbol = JavaSymbols.methodSymbol(constructor) ?: return
    define(symbol, nameIdentifier.textRange, range(header.textRange))
    addSymbol(symbol, ScipKind.Constructor, constructor.name, emptyList())
  }

  private fun defineLocal(element: PsiElement, nameIdentifier: PsiElement?) {
    if (nameIdentifier == null) return
    val symbol = localSymbol(element)
    occurrences.add(ScipOccurrence(range(nameIdentifier.textRange), symbol, ROLE_DEFINITION, null))
  }

  private fun define(symbol: String, nameRange: TextRange, enclosing: ScipRange) {
    stats.add(Counter.DEFINITIONS)
    occurrences.add(ScipOccurrence(range(nameRange), symbol, ROLE_DEFINITION, enclosing))
  }

  private fun addSymbol(symbol: String, kind: ScipKind, displayName: String, supers: List<String>) {
    val relationships = supers.distinct().sorted().map { ScipRelationship(it, isReference = true, isImplementation = true) }
    // a second definition in this file is reported with the other duplicates by ExportSummary.findDuplicateSymbols
    symbols.put(symbol, ScipSymbolInformation(symbol, kind, displayName, relationships))
  }

  /**
   * The range from the first child after the doc comment, and after the whitespace and comments that follow it, to the end of [declaration].
   * An empty child, such as an empty modifier list, does not start the range.
   */
  private fun enclosingRange(declaration: PsiElement): ScipRange {
    val end = declaration.textRange.endOffset
    var child = declaration.firstChild
    while (child != null && (child is PsiComment || child is PsiWhiteSpace || child.textLength == 0)) {
      child = child.nextSibling
    }
    val start = child?.textRange?.startOffset ?: declaration.textRange.startOffset
    return range(TextRange(start, end))
  }

  // ---------------------------------------------------------------- references

  private fun codeReference(reference: PsiJavaCodeReferenceElement) {
    val nameElement = reference.referenceNameElement ?: return
    val newExpression = constructorCallOf(reference)
    stats.add(Counter.REFS)
    val result = reference.advancedResolve(false)
    val target = result.element
    if (target == null) {
      unresolved(reference, reference.multiResolve(false).size)
      return
    }
    if (!result.isValidResult && target !is PsiPackage) {
      stats.site(Counter.INVALID_SINGLE, site(reference))
    }
    var roles = 0
    if (docDepth > 0) {
      roles = roles or ROLE_DOC
    }
    else if (reference is PsiMethodReferenceExpression || isCallee(reference)) {
      roles = roles or ROLE_CALL
    }
    if (PsiTreeUtil.getParentOfType(reference, PsiImportStatementBase::class.java, true, PsiClass::class.java) != null) {
      roles = roles or ROLE_IMPORT
    }
    if (reference is PsiReferenceExpression && target is PsiVariable) {
      roles = roles or if (PsiUtil.isAccessedForWriting(reference)) ROLE_WRITE else ROLE_READ
    }
    if (newExpression == null) {
      emitReference(reference, target, nameElement.textRange, roles)
      return
    }
    val callee = constructorCallee(newExpression, newExpression.multiResolve(false), target as? PsiClass)
    if (callee == target) {
      emitReference(reference, target, nameElement.textRange, roles or ROLE_CALL)
    }
    else {
      emitReference(reference, target, nameElement.textRange, roles)
      callee?.let { emitReference(reference, it, nameElement.textRange, roles or ROLE_CALL) }
    }
  }

  /**
   * The callee of a constructor call of [psiClass] with the resolve [results] of the call.
   *
   * - Exactly one result is its constructor; an invalid one is counted as `invalid_single`.
   * - The implicit default constructor ([LightDefaultConstructor]) has no definition, so [psiClass] stands for it.
   * - No result for a class without explicit constructors, such as the interface of an anonymous class, is [psiClass].
   *
   * Otherwise the call is counted as `ambiguous` or `unresolved`, and the result is `null`.
   * `resolveConstructor()` cannot tell these cases apart: it returns `null` for an ambiguous call too.
   */
  private fun constructorCallee(site: PsiElement, results: Array<out JavaResolveResult>, psiClass: PsiClass?): PsiElement? {
    if (results.size == 1) {
      val result = results[0]
      when (val constructor = result.element) {
        is LightDefaultConstructor -> return psiClass ?: constructor.containingClass
        null -> {}
        else -> {
          if (!result.isValidResult) {
            stats.site(Counter.INVALID_SINGLE, site(site))
          }
          return constructor
        }
      }
    }
    else if (results.isEmpty() && psiClass != null && psiClass.constructors.isEmpty()) {
      return psiClass
    }
    unresolved(site, results.size)
    return null
  }

  /**
   * An enum constant, such as `RED("red", 1)`, calls a constructor of its enum: the constructor gets [ROLE_CALL] at the constant name.
   * An enum without explicit constructors has only the implicit one, which has no definition.
   * Unlike `new`, the constant name is no reference to the enum, so such a constant writes nothing.
   */
  private fun enumConstantCall(constant: PsiEnumConstant) {
    val enumClass = constant.containingClass ?: return
    if (enumClass.constructors.isEmpty()) return
    stats.add(Counter.REFS)
    val argumentList = constant.argumentList
    val results = if (argumentList == null) {
      constant.resolveMethodGenerics().takeIf { it.element != null }?.let { arrayOf(it) } ?: JavaResolveResult.EMPTY_ARRAY
    }
    else {
      // resolveMethodGenerics() is empty for an ambiguous call as well, and the candidates tell it from an unresolved one
      val facade = JavaPsiFacade.getInstance(constant.project)
      facade.resolveHelper.multiResolveConstructor(facade.elementFactory.createType(enumClass), argumentList, constant)
    }
    val callee = constructorCallee(constant, results, enumClass) as? PsiMethod ?: return
    emitReference(constant, callee, constant.nameIdentifier.textRange, ROLE_CALL)
  }

  /** A doc reference such as `#method` of `{@link Foo#method}`, or the attribute name of an annotation. */
  private fun otherReference(reference: PsiReference) {
    stats.add(Counter.REFS)
    val target = if (reference is PsiPolyVariantReference) {
      val results = reference.multiResolve(false)
      if (results.size != 1) {
        unresolved(reference.element, results.size)
        return
      }
      results[0].element
    }
    else {
      reference.resolve()
    }
    if (target == null) {
      unresolved(reference.element, 0)
      return
    }
    val range = reference.element.textRange.cutOut(reference.rangeInElement)
    emitReference(reference.element, target, range, if (docDepth > 0) ROLE_DOC else 0)
  }

  private fun emitReference(site: PsiElement, target: PsiElement, range: TextRange, roles: Int) {
    val symbol = targetSymbol(site, target) ?: return
    stats.add(Counter.RESOLVED)
    if (roles and ROLE_CALL != 0) stats.add(Counter.CALLS)
    if (roles and ROLE_DOC != 0) stats.add(Counter.DOC_REFS)
    occurrences.add(ScipOccurrence(range(range), symbol, roles, null))
  }

  private fun targetSymbol(site: PsiElement, target: PsiElement): String? {
    if (target is PsiMember && JavaSymbols.isArrayMember(target)) {
      stats.add(Counter.ARRAY_REFS)
      return null
    }
    val symbol = when (target) {
      is PsiPackage -> {
        stats.add(Counter.PACKAGE_REFS)
        return null
      }
      is PsiTypeParameter, is PsiLocalVariable, is PsiParameter -> {
        if (target.containingFile?.originalFile != file && target.isPhysical) {
          stats.site(Counter.FOREIGN_LOCAL, site(site))
          return null
        }
        localSymbol(target)
      }
      is PsiClass -> JavaSymbols.classSymbol(target)
      is PsiMethod -> JavaSymbols.methodSymbol(target)
      is PsiField -> JavaSymbols.fieldSymbol(target)
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
    val range = range(element.textRange)
    return "$relativePath:${range.startLine + 1}:${range.startColumn + 1} ${element.text.take(80).replace('\n', ' ')}"
  }

  // ---------------------------------------------------------------- positions

  private fun range(textRange: TextRange): ScipRange {
    val startLine = lineOf(textRange.startOffset)
    val endLine = lineOf(textRange.endOffset)
    return ScipRange(startLine, textRange.startOffset - lineStarts[startLine], endLine, textRange.endOffset - lineStarts[endLine])
  }

  private fun lineOf(offset: Int): Int {
    val index = lineStarts.binarySearch(offset)
    return if (index >= 0) index else -index - 2
  }
}

/** The `new` expression whose class reference is [reference], unless it creates an array. */
private fun constructorCallOf(reference: PsiJavaCodeReferenceElement): PsiNewExpression? {
  val parent = reference.parent
  val newExpression = when {
    parent is PsiNewExpression && parent.classReference == reference -> parent
    parent is PsiAnonymousClass && parent.baseClassReference == reference -> parent.parent as? PsiNewExpression
    else -> null
  }
  return newExpression?.takeUnless { it.isArrayCreation }
}

private fun isCallee(reference: PsiJavaCodeReferenceElement): Boolean {
  val parent = reference.parent
  return parent is PsiMethodCallExpression && parent.methodExpression == reference
}

private fun lineStarts(text: CharSequence): IntArray {
  val starts = ArrayList<Int>()
  starts.add(0)
  for (i in text.indices) {
    if (text[i] == '\n') {
      starts.add(i + 1)
    }
  }
  return starts.toIntArray()
}
