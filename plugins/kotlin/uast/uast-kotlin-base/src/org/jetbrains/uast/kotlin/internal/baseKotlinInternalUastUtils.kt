// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.lang.Language
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.elements.FakeFileForLightClass
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.asJava.elements.KtLightMember
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.asJava.getAccessorLightMethods
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.ArrayFqNames
import org.jetbrains.kotlin.resolve.references.ReferenceAccess
import org.jetbrains.kotlin.type.MapPsiToAsmDesc
import org.jetbrains.kotlin.utils.addToStdlib.constant
import org.jetbrains.uast.*

@Suppress("NOTHING_TO_INLINE")
inline fun String?.orAnonymous(kind: String = ""): String = this ?: "<anonymous" + (if (kind.isNotBlank()) " $kind" else "") + ">"

fun <T> lz(initializer: () -> T) =
    lazy(LazyThreadSafetyMode.SYNCHRONIZED, initializer)

inline fun <reified T : UDeclaration, reified P : PsiElement> unwrap(element: P): P {
    val unwrapped = if (element is T) element.javaPsi else element
    assert(unwrapped !is UElement)
    return unwrapped as P
}

fun unwrapFakeFileForLightClass(file: PsiFile): PsiFile = (file as? FakeFileForLightClass)?.ktFile ?: file

fun getContainingLightClass(original: KtDeclaration): KtLightClass? =
    (original.containingClassOrObject?.toLightClass() ?: original.containingKtFile.findFacadeClass())

fun getKotlinMemberOrigin(element: PsiElement?): KtDeclaration? {
    (element as? KtLightMember<*>)?.lightMemberOrigin?.auxiliaryOriginalElement?.let { return it }
    (element as? KtLightElement<*, *>)?.kotlinOrigin?.let { return it as? KtDeclaration }
    return null
}

fun KtExpression.unwrapBlockOrParenthesis(): KtExpression {
    val innerExpression = KtPsiUtil.safeDeparenthesize(this)
    if (innerExpression is KtBlockExpression) {
        val statement = innerExpression.statements.singleOrNull() ?: return this
        return KtPsiUtil.safeDeparenthesize(statement)
    }
    return innerExpression
}

fun KtExpression.readWriteAccess(): ReferenceAccess {
    var expression = getQualifiedExpressionForSelectorOrThis()
    loop@ while (true) {
        when (val parent = expression.parent) {
            is KtParenthesizedExpression, is KtAnnotatedExpression, is KtLabeledExpression -> expression = parent as KtExpression
            else -> break@loop
        }
    }

    val assignment = expression.getAssignmentByLHS()
    if (assignment != null) {
        return when (assignment.operationToken) {
            KtTokens.EQ -> ReferenceAccess.WRITE
            else -> ReferenceAccess.READ_WRITE
        }
    }

    return if ((expression.parent as? KtUnaryExpression)?.operationToken in constant { setOf(KtTokens.PLUSPLUS, KtTokens.MINUSMINUS) })
        ReferenceAccess.READ_WRITE
    else
        ReferenceAccess.READ
}

fun KtElement.canAnalyze(): Boolean {
    if (!isValid) return false
    val containingFile = containingFile as? KtFile ?: return false // EA-114080, EA-113475, EA-134193
    if (containingFile.doNotAnalyze != null) return false // To prevent exceptions during analysis
    return true
}

val PsiClass.isEnumEntryLightClass: Boolean
    get() = (this as? KtLightClass)?.kotlinOrigin is KtEnumEntry

val KtTypeReference.nameElement: PsiElement?
    get() = this.typeElement?.let {
        (it as? KtUserType)?.referenceExpression?.getReferencedNameElement() ?: it.navigationElement
    }

internal fun KtClassOrObject.toPsiType(): PsiType {
    val lightClass = toLightClass() ?: return UastErrorType
    return PsiTypesUtil.getClassType(lightClass)
}

fun PsiElement.getMaybeLightElement(sourcePsi: KtExpression? = null): PsiElement? {
    if (this is KtProperty && sourcePsi?.readWriteAccess()?.isWrite == true) {
        with(getAccessorLightMethods()) {
            (setter ?: backingField)?.let { return it } // backingField is for val property assignments in init blocks
        }
    }
    return when (this) {
        is KtDeclaration -> {
            val lightElement = toLightElements().firstOrNull()
            if (lightElement != null) return lightElement

            if (this is KtPrimaryConstructor) {
                // annotations don't have constructors (but in Kotlin they do), so resolving to the class here
                (this.parent as? KtClassOrObject)?.takeIf { it.isAnnotation() }?.toLightClass()?.let { return it }
            }

            when (val uElement = this.toUElement()) {
                is UDeclaration -> uElement.javaPsi
                is UDeclarationsExpression -> uElement.declarations.firstOrNull()?.javaPsi
                is ULambdaExpression -> (uElement.uastParent as? KotlinLocalFunctionUVariable)?.javaPsi
                else -> null
            }
        }
        is KtElement -> null
        else -> this
    }
}

val PsiMethod.desc: String
    get() = buildString {
        parameterList.parameters.joinTo(this, separator = "", prefix = "(", postfix = ")") { MapPsiToAsmDesc.typeDesc(it.type) }
        append(MapPsiToAsmDesc.typeDesc(returnType ?: PsiType.VOID))
    }

private val KtCallElement.isAnnotationArgument: Boolean
    // KtAnnotationEntry (or KtCallExpression when annotation is nested) -> KtValueArgumentList -> KtValueArgument -> arrayOf call
    get() = when (val elementAt2 = parents.elementAtOrNull(2)) {
        is KtAnnotationEntry -> true
        is KtCallExpression -> elementAt2.getParentOfType<KtAnnotationEntry>(true, KtDeclaration::class.java) != null
        else -> false
    }

fun isAnnotationArgumentArrayInitializer(ktCallElement: KtCallElement, fqNameOfCallee: FqName): Boolean {
    return ktCallElement.isAnnotationArgument && fqNameOfCallee in ArrayFqNames.ARRAY_CALL_FQ_NAMES
}

private val KtBlockExpression.isFunctionBody: Boolean
    get() {
        return (parent as? KtNamedFunction)?.bodyBlockExpression == this
    }

/**
 * Depending on type owner kind, type conversion to [PsiType] would vary. For example, we need to convert `Unit` to `void` only if the given
 * type is used as a return type of a function. Usually, the "context" of type conversion would be the owner of the type to be converted,
 * but it's not guaranteed. So, the caller/user of the type conversion should specify the kind of the type owner.
 */
enum class TypeOwnerKind {
    UNKNOWN,
    TYPE_REFERENCE,
    CALL_ELEMENT,
    DECLARATION,
    EXPRESSION,
}

val KtElement.typeOwnerKind: TypeOwnerKind
    get() = when (this) {
        is KtTypeReference -> TypeOwnerKind.TYPE_REFERENCE
        is KtCallElement -> TypeOwnerKind.CALL_ELEMENT
        is KtDeclaration -> TypeOwnerKind.DECLARATION
        is KtExpression -> TypeOwnerKind.EXPRESSION
        else -> TypeOwnerKind.UNKNOWN
    }

fun convertUnitToVoidIfNeeded(
    context: KtElement,
    typeOwnerKind: TypeOwnerKind,
    boxed: Boolean
): PsiType? {
    fun PsiPrimitiveType.orBoxed() = if (boxed) getBoxedType(context) else this
    return when {
        typeOwnerKind == TypeOwnerKind.DECLARATION && context is KtNamedFunction ->
            PsiType.VOID.orBoxed()
        typeOwnerKind == TypeOwnerKind.EXPRESSION && context is KtBlockExpression && context.isFunctionBody ->
            PsiType.VOID.orBoxed()
        else -> null
    }
}

/** Returns true if the given element is written in Kotlin. */
fun isKotlin(element: PsiElement?): Boolean {
    return element != null && isKotlin(element.language)
}

/** Returns true if the given language is Kotlin. */
fun isKotlin(language: Language?): Boolean {
    return language == KotlinLanguage.INSTANCE
}
