// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.psi.*
import com.intellij.psi.util.PsiMethodUtil
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.j2k.ast.*
import org.jetbrains.kotlin.types.expressions.OperatorConventions

@K1Deprecation
fun quoteKeywords(packageName: String): String = packageName.split('.').joinToString(".") { Identifier.toKotlin(it) }

@K1Deprecation
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

@K1Deprecation
fun shouldGenerateDefaultInitializer(searcher: ReferenceSearcher, field: PsiField)
        = field.initializer == null && (field.isVar(searcher) || !field.hasWriteAccesses(searcher, field.containingClass))

@K1Deprecation
fun PsiReferenceExpression.isQualifierEmptyOrThis(): Boolean {
    val qualifier = qualifierExpression
    return qualifier == null || (qualifier is PsiThisExpression && qualifier.qualifier == null)
}

@K1Deprecation
fun PsiReferenceExpression.isQualifierEmptyOrClass(psiClass: PsiClass): Boolean {
    val qualifier = qualifierExpression
    return qualifier == null || (qualifier is PsiReferenceExpression && qualifier.isReferenceTo(psiClass))
}

@K1Deprecation
@Deprecated("This declaration will be removed in a future release along with the whole Old J2K module")
fun PsiElement.getContainingMethod(): PsiMethod? {
    var context = context
    while (context != null) {
        @Suppress("LocalVariableName") val _context = context
        if (_context is PsiMethod) return _context
        context = _context.context
    }
    return null
}

@K1Deprecation
@Suppress("unused", "DuplicatedCode") // used from a 3rd-party plugin
@Deprecated("This declaration will be removed in a future release along with the whole Old J2K module")
fun PsiElement.getContainingClass(): PsiClass? {
    var context = context
    while (context != null) {
        @Suppress("LocalVariableName") val _context = context
        if (_context is PsiClass) return _context
        if (_context is PsiMember) return _context.containingClass
        context = _context.context
    }
    return null
}

@K1Deprecation
fun PsiElement.getContainingConstructor(): PsiMethod? {
    @Suppress("DEPRECATION") val method = getContainingMethod()
    return if (method?.isConstructor == true) method else null
}

@K1Deprecation
fun PsiMember.isConstructor(): Boolean = this is PsiMethod && this.isConstructor

@K1Deprecation
fun PsiModifierListOwner.accessModifier(): String = when {
    hasModifierProperty(PsiModifier.PUBLIC) -> PsiModifier.PUBLIC
    hasModifierProperty(PsiModifier.PRIVATE) -> PsiModifier.PRIVATE
    hasModifierProperty(PsiModifier.PROTECTED) -> PsiModifier.PROTECTED
    else -> PsiModifier.PACKAGE_LOCAL
}

@K1Deprecation
fun PsiMethod.isMainMethod(): Boolean = PsiMethodUtil.isMainMethod(this)

@K1Deprecation
fun PsiReferenceExpression.dot(): PsiElement? = node.findChildByType(JavaTokenType.DOT)?.psi
@K1Deprecation
fun PsiExpressionList.lPar(): PsiElement? = node.findChildByType(JavaTokenType.LPARENTH)?.psi
@K1Deprecation
fun PsiExpressionList.rPar(): PsiElement? = node.findChildByType(JavaTokenType.RPARENTH)?.psi

@K1Deprecation
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
@K1Deprecation
fun isFacadeClassFromLibrary(element: PsiElement?) = element is KtLightClass && element.kotlinOrigin == null

@K1Deprecation
@Suppress("UnusedReceiverParameter")
fun Converter.convertToKotlinAnalog(classQualifiedName: String?, mutability: Mutability): String? {
    if (classQualifiedName == null) return null
    return (if (mutability.isMutable()) toKotlinMutableTypesMap[classQualifiedName] else null)
           ?: toKotlinTypesMap[classQualifiedName]
}

@K1Deprecation
fun Converter.convertToKotlinAnalogIdentifier(classQualifiedName: String?, mutability: Mutability): Identifier? {
    val kotlinClassName = convertToKotlinAnalog(classQualifiedName, mutability) ?: return null
    return Identifier.withNoPrototype(kotlinClassName.substringAfterLast('.'))
}

@K1Deprecation
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