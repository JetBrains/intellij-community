// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.psiScip

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiTypes
import com.intellij.psi.util.ClassUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.TypeConversionUtil
import java.security.MessageDigest

/** The scheme and the empty package of every global symbol: `psi`, then `.` for the manager, the name and the version. */
internal const val SYMBOL_PREFIX: String = "psi . . . "

/**
 * Builds SCIP symbols for Java PSI elements in the descriptor shape of scip-java.
 *
 * - A package is a namespace per segment: `com/google/gson/`.
 * - A named class is a type, nested in its outer class: `Outer#Inner#`.
 * - A local or anonymous class is a type named by its JVM binary name without the package: `Outer$1#`.
 * - A field is a term: `Gson#serializeNulls.`.
 * - A method is `name(<hash>).`, and a constructor is `` `<init>`(<hash>). ``.
 * - The synthetic array class of `int[].length`, `array.clone()` and `Foo[]::new` has no symbol, and neither have its members.
 *
 * The method disambiguator is [methodDisambiguator], a hash of the JVM descriptor.
 * An overload that is added to a class does not rename the other overloads.
 */
internal object JavaSymbols {
  fun classSymbol(psiClass: PsiClass): String? = classDescriptor(psiClass)?.let { SYMBOL_PREFIX + it }

  fun methodSymbol(method: PsiMethod): String? {
    val owner = method.containingClass?.let(::classDescriptor) ?: return null
    val name = if (method.isConstructor) "<init>" else method.name
    return SYMBOL_PREFIX + owner + escape(name) + "(" + methodDisambiguator(method) + ")."
  }

  fun fieldSymbol(field: PsiField): String? {
    val owner = field.containingClass?.let(::classDescriptor) ?: return null
    val name = field.name
    return SYMBOL_PREFIX + owner + escape(name) + "."
  }

  /** The first 6 hex digits of the SHA-256 of [jvmDescriptor]. */
  fun methodDisambiguator(method: PsiMethod): String = disambiguator(jvmDescriptor(method))

  /** The first 6 hex digits of the SHA-256 of [text]. */
  fun disambiguator(text: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    val result = StringBuilder(6)
    for (i in 0 until 3) {
      val b = digest[i].toInt() and 0xff
      result.append(HEX[b shr 4]).append(HEX[b and 0xf])
    }
    return result.toString()
  }

  /**
   * The JVM descriptor of the source parameters and the return type, such as `(Ljava/lang/String;I)V`.
   * A constructor returns `V`. An unresolved class type keeps its erased text, so the descriptor never depends on the resolve order.
   * The descriptor leaves out synthetic parameters, such as the outer instance of an inner class constructor.
   */
  fun jvmDescriptor(method: PsiMethod): String {
    val result = StringBuilder("(")
    for (parameter in method.parameterList.parameters) {
      appendType(result, parameter.type)
    }
    result.append(')')
    appendType(result, if (method.isConstructor) PsiTypes.voidType() else method.returnType ?: PsiTypes.voidType())
    return result.toString()
  }

  private fun appendType(result: StringBuilder, type: PsiType) {
    when (val erased = TypeConversionUtil.erasure(type) ?: type) {
      is PsiPrimitiveType -> result.append(erased.kind.binaryName)
      is PsiArrayType -> {
        result.append('[')
        appendType(result, erased.componentType)
      }
      is PsiClassType -> {
        val psiClass = erased.resolve()
        val name = psiClass?.let(ClassUtil::getJVMClassName)?.replace('.', '/') ?: erased.rawType().canonicalText
        result.append('L').append(name).append(';')
      }
      else -> result.append('L').append(erased.canonicalText).append(';')
    }
  }

  private fun classDescriptor(psiClass: PsiClass): String? {
    if (psiClass is PsiTypeParameter || isArrayClass(psiClass)) {
      return null
    }
    if (PsiUtil.isLocalOrAnonymousClass(psiClass)) {
      val binaryName = ClassUtil.getBinaryClassName(psiClass) ?: return null
      val packageName = packageName(psiClass) ?: return null
      val simpleBinaryName = if (packageName.isEmpty()) binaryName else binaryName.removePrefix("$packageName.")
      return packageDescriptor(packageName) + escape(simpleBinaryName) + "#"
    }
    val name = psiClass.name ?: return null
    val outer = psiClass.containingClass
    if (outer != null) {
      return classDescriptor(outer)?.let { it + escape(name) + "#" }
    }
    // a top-level class: a class outside a class owner file, or without a qualified name, has no symbol
    val packageName = packageName(psiClass) ?: return null
    if (psiClass.qualifiedName == null) {
      return null
    }
    return packageDescriptor(packageName) + escape(name) + "#"
  }

  /** The synthetic array class, whose outer class is `_Dummy_` of a non-physical file, or one of its members. */
  fun isArrayMember(member: PsiMember): Boolean = isArrayClass(member as? PsiClass ?: member.containingClass ?: return false)

  private fun isArrayClass(psiClass: PsiClass): Boolean = JavaPsiFacade.getElementFactory(psiClass.project).isArrayClass(psiClass)

  private fun packageName(psiClass: PsiClass): String? = (psiClass.containingFile as? PsiClassOwner)?.packageName

  fun packageDescriptor(packageName: String): String {
    if (packageName.isEmpty()) {
      return ""
    }
    return packageName.split('.').joinToString(separator = "") { escape(it) + "/" }
  }

  /** Wraps [name] in backticks unless it is a SCIP simple identifier. */
  fun escape(name: String): String {
    if (name.isNotEmpty() && name.all { it == '_' || it == '+' || it == '-' || it == '$' || it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }) {
      return name
    }
    return "`" + name.replace("`", "``") + "`"
  }

  fun kindOf(psiClass: PsiClass): ScipKind {
    return when {
      psiClass.isEnum -> ScipKind.Enum
      psiClass.isInterface -> ScipKind.Interface
      else -> ScipKind.Class
    }
  }
}

private const val HEX = "0123456789abcdef"
