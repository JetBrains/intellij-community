// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.j2k

import com.intellij.psi.*
import com.intellij.psi.util.PsiMethodUtil
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.j2k.ast.*
import org.jetbrains.kotlin.types.expressions.OperatorConventions

fun quoteKeywords(packageName: String): String = packageName.split('.').joinToString(".") { Identifier.toKotlin(it) }

fun getDefaultInitializer(property: Property): Expression? {
    val t = property.type
    val result = when {
        t.isNullable -> LiteralExpression("null")
        t is PrimitiveType -> when (t.name.name) {
            "Boolean" -> LiteralExpression("false")
            "Char" -> LiteralExpression("' '")
            "Double" -> MethodCallExpression.buildNonNull(LiteralExpression("0").assignNoPrototype(), OperatorConventions.DOUBLE.toString())
            "Float" -> MethodCallExpression.buildNonNull(LiteralExpression("0").assignNoPrototype(), OperatorConventions.FLOAT.toString())
            else -> LiteralExpression("0")
        }
        else -> null
    }
    return result?.assignNoPrototype()
}

fun shouldGenerateDefaultInitializer(searcher: ReferenceSearcher, field: PsiField)
        = field.initializer == null && (field.isVar(searcher) || !field.hasWriteAccesses(searcher, field.containingClass))

fun PsiReferenceExpression.isQualifierEmptyOrThis(): Boolean {
    val qualifier = qualifierExpression
    return qualifier == null || (qualifier is PsiThisExpression && qualifier.qualifier == null)
}

fun PsiReferenceExpression.isQualifierEmptyOrClass(psiClass: PsiClass): Boolean {
    val qualifier = qualifierExpression
    return qualifier == null || (qualifier is PsiReferenceExpression && qualifier.isReferenceTo(psiClass))
}

fun PsiElement.isInSingleLine(): Boolean {
    if (this is PsiWhiteSpace) {
        val text = text!!
        return text.indexOf('\n') < 0 && text.indexOf('\r') < 0
    }

    var child = firstChild
    while (child != null) {
        if (!child.isInSingleLine()) return false
        child = child.nextSibling
    }
    return true
}

@Deprecated("This declaration will be removed in a future release along with the whole Old J2K module")
fun PsiElement.getContainingMethod(): PsiMethod? {
    var context = context
    while (context != null) {
        val _context = context
        if (_context is PsiMethod) return _context
        context = _context.context
    }
    return null
}

@Suppress("unused") // used from a 3rd-party plugin
@Deprecated("This declaration will be removed in a future release along with the whole Old J2K module")
fun PsiElement.getContainingClass(): PsiClass? {
    var context = context
    while (context != null) {
        val _context = context
        if (_context is PsiClass) return _context
        if (_context is PsiMember) return _context.containingClass
        context = _context.context
    }
    return null
}

fun PsiElement.getContainingConstructor(): PsiMethod? {
    val method = getContainingMethod()
    return if (method?.isConstructor == true) method else null
}

fun PsiMember.isConstructor(): Boolean = this is PsiMethod && this.isConstructor

fun PsiModifierListOwner.accessModifier(): String = when {
    hasModifierProperty(PsiModifier.PUBLIC) -> PsiModifier.PUBLIC
    hasModifierProperty(PsiModifier.PRIVATE) -> PsiModifier.PRIVATE
    hasModifierProperty(PsiModifier.PROTECTED) -> PsiModifier.PROTECTED
    else -> PsiModifier.PACKAGE_LOCAL
}

fun PsiMethod.isMainMethod(): Boolean = PsiMethodUtil.isMainMethod(this)

fun PsiReferenceExpression.dot(): PsiElement? = node.findChildByType(JavaTokenType.DOT)?.psi
fun PsiExpressionList.lPar(): PsiElement? = node.findChildByType(JavaTokenType.LPARENTH)?.psi
fun PsiExpressionList.rPar(): PsiElement? = node.findChildByType(JavaTokenType.RPARENTH)?.psi

fun PsiMember.isImported(file: PsiJavaFile): Boolean {
    return if (this is PsiClass) {
        val fqName = qualifiedName
        val index = fqName?.lastIndexOf('.') ?: -1
        val parentName = if (index >= 0) fqName!!.substring(0, index) else null
        file.importList?.allImportStatements?.any {
            it.importReference?.qualifiedName == (if (it.isOnDemand) parentName else fqName)
        } ?: false
    }
    else {
        containingClass != null && file.importList?.importStaticStatements?.any {
            it.resolveTargetClass() == containingClass && (it.isOnDemand || it.referenceName == name)
        } ?: false
    }
}

// TODO: set origin for facade classes in library
fun isFacadeClassFromLibrary(element: PsiElement?) = element is KtLightClass && element.kotlinOrigin == null

fun Converter.convertToKotlinAnalog(classQualifiedName: String?, mutability: Mutability): String? {
    if (classQualifiedName == null) return null
    return (if (mutability.isMutable()) toKotlinMutableTypesMap[classQualifiedName] else null)
           ?: toKotlinTypesMap[classQualifiedName]
}

fun Converter.convertToKotlinAnalogIdentifier(classQualifiedName: String?, mutability: Mutability): Identifier? {
    val kotlinClassName = convertToKotlinAnalog(classQualifiedName, mutability) ?: return null
    return Identifier.withNoPrototype(kotlinClassName.substringAfterLast('.'))
}

val toKotlinTypesMap: Map<String, String> = mapOf(
    CommonClassNames.JAVA_LANG_OBJECT to StandardNames.FqNames.any.asString(),
    CommonClassNames.JAVA_LANG_BYTE to StandardNames.FqNames._byte.asString(),
    CommonClassNames.JAVA_LANG_CHARACTER to StandardNames.FqNames._char.asString(),
    CommonClassNames.JAVA_LANG_DOUBLE to StandardNames.FqNames._double.asString(),
    CommonClassNames.JAVA_LANG_FLOAT to StandardNames.FqNames._float.asString(),
    CommonClassNames.JAVA_LANG_INTEGER to StandardNames.FqNames._int.asString(),
    CommonClassNames.JAVA_LANG_LONG to StandardNames.FqNames._long.asString(),
    CommonClassNames.JAVA_LANG_SHORT to StandardNames.FqNames._short.asString(),
    CommonClassNames.JAVA_LANG_BOOLEAN to StandardNames.FqNames._boolean.asString(),
    CommonClassNames.JAVA_LANG_ITERABLE to StandardNames.FqNames.iterable.asString(),
    CommonClassNames.JAVA_UTIL_ITERATOR to StandardNames.FqNames.iterator.asString(),
    CommonClassNames.JAVA_UTIL_LIST to StandardNames.FqNames.list.asString(),
    CommonClassNames.JAVA_UTIL_COLLECTION to StandardNames.FqNames.collection.asString(),
    CommonClassNames.JAVA_UTIL_SET to StandardNames.FqNames.set.asString(),
    CommonClassNames.JAVA_UTIL_MAP to StandardNames.FqNames.map.asString(),
    CommonClassNames.JAVA_UTIL_MAP_ENTRY to StandardNames.FqNames.mapEntry.asString(),
    java.util.ListIterator::class.java.canonicalName to StandardNames.FqNames.listIterator.asString()
)