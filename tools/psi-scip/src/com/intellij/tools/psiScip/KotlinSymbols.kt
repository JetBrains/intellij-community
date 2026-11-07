// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.psiScip

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.fileClasses.javaFileFacadeFqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

/** The symbol of a declaration that a reference resolves to, in Java or in Kotlin. */
internal object PsiSymbols {
  /**
   * A Kotlin declaration, as a reference resolves to it in Kotlin or to its light element in Java, has the symbol of [KotlinSymbols];
   * a Kotlin class without an explicit constructor stands for its implicit constructor, as in [JavaDocumentCollector].
   * Any other class, method or field has the symbol of [JavaSymbols].
   */
  fun of(target: PsiElement): String? {
    val origin = (target as? KtLightElement<*, *>)?.kotlinOrigin ?: target
    if (origin is KtDeclaration) {
      return KotlinSymbols.of(origin)
    }
    return when (target) {
      is PsiClass -> JavaSymbols.classSymbol(target)
      is PsiMethod -> JavaSymbols.methodSymbol(target)
      is PsiField -> JavaSymbols.fieldSymbol(target)
      else -> null
    }
  }
}

/**
 * Builds SCIP symbols for Kotlin declarations in the shape of [JavaSymbols], so that a Java and a Kotlin use of one declaration agree.
 *
 * - A class is the symbol of its light class: `Outer#Inner#`, a companion `Outer#Companion#`; a class without one (a library's,
 *   decompiled) is named by its qualified name in the same shape.
 * - A function is `name(<hash>).` in its class, and a top-level function is `name(<hash>).` in its package, as scip-kotlin writes it,
 *   never in its file facade (`AliasKt`). The hash is [JavaSymbols.methodDisambiguator] of its light method, whose JVM descriptor
 *   holds the extension receiver as the first parameter: `<T : Table> T.alias(String)` and `<T : AbstractQuery<*>> T.alias(String)` differ.
 *   Overloads of one erasure that `@JvmName` tells apart (`eq` of `T` and of `EntityID<T>`) hash the JVM name with the descriptor;
 *   a function without a light method hashes the text of its receiver and parameters.
 * - A local or anonymous class (an object literal) is a type named by its JVM binary name without the package, as Kotlin compiles it
 *   and as [JavaSymbols] names a Java one: `Owner$function$1#`, `FileKt$function$Local#`; its members are members of it.
 * - A property accessor, as Java calls it (`getExtensionList()`), has the symbol of its property.
 * - A constructor is the symbol of its light method: `` Owner#`<init>`(<hash>). ``; without one, the hash is of its parameters' text.
 * - A property, and a `val`/`var` parameter of a primary constructor, is a term: `Owner#name.`, top-level `package/name.`.
 *   Its accessors, as Java calls them (`getName()`), have the symbol of the property.
 * - An enum entry is a term of its enum: `Color#RED.`.
 * - A local variable or function and a type alias have no symbol.
 */
internal object KotlinSymbols {
  fun of(declaration: KtDeclaration): String? {
    return when (declaration) {
      is KtEnumEntry -> declaration.ownerClass?.let(::of)?.let { it + JavaSymbols.escape(declaration.name ?: return null) + "." }
      is KtClassOrObject -> if (declaration.isLocal) localClass(declaration)?.let { SYMBOL_PREFIX + JavaSymbols.packageDescriptor(declaration.containingKtFile.packageFqName.asString()) + JavaSymbols.escape(it) + "#" }
        else declaration.toLightClass()?.let(JavaSymbols::classSymbol) ?: byQualifiedName(declaration)
      is KtPropertyAccessor -> of(declaration.property)
      is KtConstructor<*> -> declaration.toLightMethods().firstOrNull()?.let(JavaSymbols::methodSymbol)
        ?: of(declaration.getContainingClassOrObject())?.let { it + "`<init>`(" + JavaSymbols.disambiguator(declaration.valueParameterList?.text ?: "()") + ")." }
      is KtNamedFunction -> function(declaration)
      is KtProperty -> if (declaration.isLocal) null else member(declaration, declaration.name, ".")
      is KtParameter -> if (declaration.hasValOrVar() && declaration.ownerFunction is KtConstructor<*>) member(declaration, declaration.name, ".") else null
      else -> null
    }
  }

  private fun function(function: KtNamedFunction): String? {
    if (function.isLocal) return null
    val method = function.toLightMethods().firstOrNull()
    val hash = when {
      method == null -> JavaSymbols.disambiguator((function.receiverTypeReference?.text ?: "") + (function.valueParameterList?.text ?: "()"))
      method.name != function.name -> JavaSymbols.disambiguator(method.name + JavaSymbols.jvmDescriptor(method))
      else -> JavaSymbols.methodDisambiguator(method)
    }
    return member(function, function.name, "($hash).")
  }

  /**
   * The JVM binary name without the package of a local or anonymous class: the binary name of the class around it, or the file facade,
   * then `$` and the outermost function or property between them, then `$` and its name, or for an object literal its 1-based position
   * among the object literals of the functions of that name in the source order: the overloads of a function share one counter.
   */
  private fun localClass(cls: KtClassOrObject): String? {
    val (owner, function) = localContext(cls)
    val outer = when {
      owner == null -> cls.containingKtFile.javaFileFacadeFqName.shortName().asString()
      owner.isLocal -> localClass(owner) ?: return null
      else -> nestedName(owner) ?: return null
    }
    val prefix = if (function?.name != null) outer + "$" + function.name else outer
    if (cls !is KtObjectDeclaration || !cls.isObjectLiteral()) {
      return prefix + "$" + (cls.name ?: return null)
    }
    val root: PsiElement = owner ?: cls.containingKtFile
    val siblings = PsiTreeUtil.collectElementsOfType(root, KtObjectDeclaration::class.java)
      .filter { it.isObjectLiteral() && localContext(it).let { (o, f) -> o == owner && f?.name == function?.name } }
      .sortedBy { it.textRange.startOffset }
    return prefix + "$" + (siblings.indexOf(cls) + 1)
  }

  /** The class around a local class (None: the file) and the outermost function or property between them. */
  private fun localContext(cls: KtClassOrObject): Pair<KtClassOrObject?, KtDeclaration?> {
    var element = cls.parent
    var function: KtDeclaration? = null
    while (element != null && element !is KtFile) {
      if (element is KtClassOrObject) {
        return element to function
      }
      if (element is KtNamedFunction || element is KtProperty) function = element as KtDeclaration
      element = element.parent
    }
    return null to function
  }

  /** `Outer$Inner` of a class that is not local. */
  private fun nestedName(cls: KtClassOrObject): String? {
    val qualified = cls.fqName?.asString() ?: return null
    val packageName = cls.containingKtFile.packageFqName.asString()
    return (if (packageName.isEmpty()) qualified else qualified.removePrefix("$packageName.")).replace('.', '$')
  }

  /** `package/Outer#Inner#` from the qualified name of a class without a light class. */
  private fun byQualifiedName(classOrObject: KtClassOrObject): String? {
    val qualified = classOrObject.fqName?.asString() ?: return null
    val packageName = classOrObject.containingKtFile.packageFqName.asString()
    val relative = if (packageName.isEmpty()) qualified else qualified.removePrefix("$packageName.")
    return SYMBOL_PREFIX + JavaSymbols.packageDescriptor(packageName) + relative.split('.').joinToString(separator = "") { JavaSymbols.escape(it) + "#" }
  }

  /** [name] and [suffix] in the class of [declaration], or in its package when it is top-level. */
  private fun member(declaration: KtDeclaration, name: String?, suffix: String): String? {
    if (name == null) return null
    val owner = when (val parent = declaration.parent) {
      is KtFile -> SYMBOL_PREFIX + JavaSymbols.packageDescriptor(parent.packageFqName.asString())
      else -> declaration.ownerClass?.let(::of) ?: return null
    }
    return owner + JavaSymbols.escape(name) + suffix
  }

  /** The class of a member, and of a `val`/`var` parameter of a primary constructor. */
  private val KtDeclaration.ownerClass: KtClassOrObject?
    get() = if (this is KtParameter) (ownerFunction as? KtConstructor<*>)?.getContainingClassOrObject() else containingClassOrObject
}
