// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k


import com.intellij.codeInsight.generation.GenerateEqualsHelper.getEqualsSignature
import com.intellij.lang.jvm.JvmClassKind
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.MethodSignatureUtil
import com.intellij.psi.util.PsiUtil
import org.jetbrains.kotlin.j2k.ReferenceSearcher
import org.jetbrains.kotlin.j2k.isNullLiteral
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

@Suppress("DuplicatedCode") // copied from old J2K
fun canKeepEqEq(left: PsiExpression, right: PsiExpression?): Boolean {
    if (left.isNullLiteral() || (right?.isNullLiteral() == true)) return true
    when (val type = left.type) {
        is PsiPrimitiveType, is PsiArrayType -> return true

        is PsiClassType -> {
            if (right?.type is PsiPrimitiveType) return true

            val psiClass = type.resolve() ?: return false
            if (!psiClass.hasModifierProperty(PsiModifier.FINAL)) return false
            if (psiClass.isEnum) return true

            val equalsSignature = getEqualsSignature(left.project, GlobalSearchScope.allScope(left.project))
            val equalsMethod = MethodSignatureUtil.findMethodBySignature(psiClass, equalsSignature, true)
            return equalsMethod == null || equalsMethod.containingClass?.qualifiedName == CommonClassNames.JAVA_LANG_OBJECT
        }

        else -> return false
    }
}

fun PsiMember.visibility(
    referenceSearcher: ReferenceSearcher,
    assignNonCodeElements: ((JKFormattingOwner, PsiElement) -> Unit)?
): JKVisibilityModifierElement {
    val visibilityFromModifier = modifierList?.children?.mapNotNull { child ->
        if (child !is PsiKeyword) return@mapNotNull null
        child.text.toKotlinVisibility(psiMember = this@visibility, referenceSearcher)?.let {
            JKVisibilityModifierElement(it)
        }?.also { modifier ->
            assignNonCodeElements?.also { it(modifier, child) }
        }
    }?.firstOrNull()

    return when {
        visibilityFromModifier != null -> visibilityFromModifier
        containingClass?.isInterface == true -> JKVisibilityModifierElement(Visibility.PUBLIC)
        else -> JKVisibilityModifierElement(Visibility.INTERNAL)
    }
}

private fun String.toKotlinVisibility(psiMember: PsiMember, referenceSearcher: ReferenceSearcher): Visibility? = when (this) {
    PsiModifier.PACKAGE_LOCAL -> Visibility.INTERNAL
    PsiModifier.PRIVATE -> Visibility.PRIVATE
    PsiModifier.PROTECTED -> handleProtectedVisibility(psiMember, referenceSearcher)
    PsiModifier.PUBLIC -> Visibility.PUBLIC
    else -> null
}

// Returns the visibility of an overridden method to determine the proper visibility of an overriding method.
// The overridden method may be a regular or compiled Java/Kotlin method.
internal fun PsiMethod.overriddenMethodVisibility(referenceSearcher: ReferenceSearcher): JKVisibilityModifierElement {
    // In some implementations (ex. compiled Java/Kotlin classes), modifierList.children() returns null.
    // However, modifierList.text still contains the modifiers as a string.
    val modifiers = modifierList.text?.split(" ").orEmpty()
    val visibilityFromModifier = modifiers.firstNotNullOfOrNull { modifier ->
        modifier.toKotlinVisibility(psiMember = this@overriddenMethodVisibility, referenceSearcher)
    }

    val visibility = when {
        visibilityFromModifier != null -> visibilityFromModifier
        containingClass?.isInterface == true -> Visibility.PUBLIC
        else -> Visibility.INTERNAL
    }

    return JKVisibilityModifierElement(visibility)
}

fun PsiMember.modality(assignNonCodeElements: ((JKFormattingOwner, PsiElement) -> Unit)?): JKModalityModifierElement {
    val modalityFromModifier = modifierList?.children?.mapNotNull { child ->
        if (child !is PsiKeyword) return@mapNotNull null
        when (child.text) {
            PsiModifier.FINAL -> Modality.FINAL
            PsiModifier.ABSTRACT -> Modality.ABSTRACT

            else -> null
        }?.let {
            JKModalityModifierElement(it)
        }?.also { modifier ->
            assignNonCodeElements?.let { it(modifier, child) }
        }
    }?.firstOrNull()

    return when {
        modalityFromModifier != null -> modalityFromModifier
        this is PsiField && containingClass?.isInterface == true -> JKModalityModifierElement(Modality.FINAL)
        else -> JKModalityModifierElement(Modality.OPEN)
    }
}

fun JvmClassKind.toJk(): JKClass.ClassKind = when (this) {
    JvmClassKind.CLASS -> JKClass.ClassKind.CLASS
    JvmClassKind.INTERFACE -> JKClass.ClassKind.INTERFACE
    JvmClassKind.ANNOTATION -> JKClass.ClassKind.ANNOTATION
    JvmClassKind.ENUM -> JKClass.ClassKind.ENUM
}

private fun handleProtectedVisibility(psiMember: PsiMember, referenceSearcher: ReferenceSearcher): Visibility {
    val originalClass = psiMember.containingClass ?: return Visibility.PROTECTED
    // Search for usages only in Java because java-protected member cannot be used in Kotlin from same package
    val usages = referenceSearcher.findUsagesForExternalCodeProcessing(psiMember, searchJava = true, searchKotlin = false)

    return if (usages.any { !allowProtected(it.element, psiMember, originalClass) })
        Visibility.PUBLIC
    else Visibility.PROTECTED
}

@Suppress("DuplicatedCode") // Copied from old J2K
private fun allowProtected(element: PsiElement, member: PsiMember, originalClass: PsiClass): Boolean {
    if (element.parent is PsiNewExpression && member is PsiMethod && member.isConstructor) {
        // calls to for protected constructors are allowed only within same class or as super calls
        return element.parentsWithSelf.contains(originalClass)
    }

    return element.parentsWithSelf.filterIsInstance<PsiClass>().any { accessContainingClass ->
        if (!InheritanceUtil.isInheritorOrSelf(accessContainingClass, originalClass, true)) return@any false

        if (element !is PsiReferenceExpression) return@any true

        val qualifierExpression = element.qualifierExpression ?: return@any true

        // super.foo is allowed if 'foo' is protected
        if (qualifierExpression is PsiSuperExpression) return@any true

        val receiverType = qualifierExpression.type ?: return@any true
        val resolvedClass = PsiUtil.resolveGenericsClassInType(receiverType).element ?: return@any true

        // receiver type should be subtype of containing class
        InheritanceUtil.isInheritorOrSelf(resolvedClass, accessContainingClass, true)
    }
}

fun PsiClass.classKind(): JKClass.ClassKind =
    when {
        isAnnotationType -> JKClass.ClassKind.ANNOTATION
        isEnum -> JKClass.ClassKind.ENUM
        isInterface -> JKClass.ClassKind.INTERFACE
        isRecord -> JKClass.ClassKind.RECORD
        else -> JKClass.ClassKind.CLASS
    }

val KtDeclaration.fqNameWithoutCompanions
    get() = generateSequence(this) { it.containingClassOrObject }
        .filter { it.safeAs<KtObjectDeclaration>()?.isCompanion() != true && it.name != null }
        .toList()
        .foldRight(containingKtFile.packageFqName) { container, acc -> acc.child(Name.identifier(container.name!!)) }

fun <T> runUndoTransparentActionInEdt(inWriteAction: Boolean, action: () -> T): T {
    var result: T? = null
    ApplicationManager.getApplication().invokeAndWait {
        CommandProcessor.getInstance().runUndoTransparentAction {
            result = when {
                inWriteAction -> runWriteAction(action)
                else -> action()
            }
        }
    }
    return result!!
}

@Suppress("LocalVariableName")
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

inline fun <reified T : PsiElement> List<PsiElement>.descendantsOfType(): List<T> =
    flatMap { it.collectDescendantsOfType() }

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

fun PsiElement.getExplicitLabelComment(): PsiComment? {
    val comment = prevSibling?.safeAs<PsiComment>()
    if (comment?.text?.asExplicitLabel() != null) return comment
    if (parent is KtValueArgument || parent is KtBinaryExpression || parent is KtContainerNode) {
        return parent.getExplicitLabelComment()
    }
    return null
}