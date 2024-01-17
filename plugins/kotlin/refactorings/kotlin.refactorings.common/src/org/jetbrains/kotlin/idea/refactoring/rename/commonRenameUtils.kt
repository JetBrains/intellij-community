// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.CommonBundle
import com.intellij.ide.IdeBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.showYesNoCancelDialog
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.*
import com.intellij.psi.presentation.java.SymbolPresentationUtil
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.rename.ResolvableCollisionUsageInfo
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewTypeLocation
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.references.AbstractKtReference
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

/**
 * Parameter can be referenced via named arguments,
 * so we have to expand it's [KtParameter.getUseScope] in case we want to rename it.
 *
 * @see KtParameter.getUseScope
 */
val KtParameter.useScopeForRename: SearchScope
    get() {
        val owner = ownerFunction as? KtFunction
        return owner?.useScope ?: useScope
    }

/**
 * A utility function to call [ProgressManager.runProcessWithProgressSynchronously] more conveniently.
 *
 * [progressTitle] is marked as [NlsContexts.DialogMessage] since it's more appropriate in most cases
 * than [NlsContexts.DialogTitle].
 */
@Suppress("DialogTitleCapitalization")
inline fun <T> runProcessWithProgressSynchronously(
    @NlsContexts.DialogMessage progressTitle: String,
    canBeCancelled: Boolean,
    project: Project,
    crossinline action: () -> T
): T = ProgressManager.getInstance().runProcessWithProgressSynchronously(
    ThrowableComputable { action() },
    progressTitle,
    canBeCancelled,
    project
)

fun checkConflictsAndReplaceUsageInfos(
    element: PsiElement,
    allRenames: Map<out PsiElement?, String>,
    result: MutableList<UsageInfo>
) {
    element.getOverriddenFunctionWithDefaultValues(allRenames)?.let { baseFunction ->
        result += LostDefaultValuesInOverridingFunctionUsageInfo(element.unwrapped as KtNamedFunction, baseFunction)
    }

    val usageIterator = result.listIterator()
    while (usageIterator.hasNext()) {
        val usageInfo = usageIterator.next() as? MoveRenameUsageInfo ?: continue
        val ref = usageInfo.reference as? AbstractKtReference<*> ?: continue
        if (!ref.canRename()) {
            val refElement = usageInfo.element ?: continue
            val referencedElement = usageInfo.referencedElement ?: continue
            usageIterator.set(UnresolvableConventionViolationUsageInfo(refElement, referencedElement))
        }
    }
}

class UnresolvableConventionViolationUsageInfo(
    element: PsiElement,
    referencedElement: PsiElement
) : UnresolvableCollisionUsageInfo(element, referencedElement) {
    override fun getDescription(): String = KotlinBundle.message("naming.convention.will.be.violated.after.rename")
}

class LostDefaultValuesInOverridingFunctionUsageInfo(
    function: KtNamedFunction,
    private val baseFunction: KtNamedFunction
) : ResolvableCollisionUsageInfo(function, function) {
    fun apply() {
        val function = element as? KtNamedFunction ?: return
        for ((subParam, superParam) in (function.valueParameters zip baseFunction.valueParameters)) {
            val defaultValue = superParam.defaultValue ?: continue
            subParam.dropDefaultValue()
            subParam.addRange(superParam.equalsToken, defaultValue)
        }
    }
}

private fun PsiElement.getOverriddenFunctionWithDefaultValues(allRenames: Map<out PsiElement?, String>): KtNamedFunction? {
    val elementsToRename = allRenames.keys.mapNotNull { it?.unwrapped }
    val function = unwrapped as? KtNamedFunction ?: return null
    val overridenFunctions = KotlinRenameRefactoringSupport.getInstance().getAllOverridenFunctions(function)
    return overridenFunctions
        .filterIsInstance<KtNamedFunction>()
        .firstOrNull { it !in elementsToRename && it.valueParameters.any { it.hasDefaultValue() } }
}

private fun KtParameter.dropDefaultValue() {
    val from = equalsToken ?: return
    val to = defaultValue ?: from
    deleteChildRange(from, to)
}

private fun KtNamedDeclaration.isAbstract(): Boolean = when {
    hasModifier(KtTokens.ABSTRACT_KEYWORD) -> true
    (containingClassOrObject as? KtClass)?.isInterface() != true -> false
    this is KtProperty -> initializer == null && delegate == null && accessors.isEmpty()
    this is KtNamedFunction -> !hasBody()
    else -> false
}

fun checkSuperMethodsWithPopup(
    declaration: KtNamedDeclaration,
    deepestSuperMethods: List<PsiElement>,
    editor: Editor,
    action: (List<PsiElement>) -> Unit
) {
    if (deepestSuperMethods.isEmpty()) return action(listOf(declaration))

    val title = getRenameBaseTitle(declaration, deepestSuperMethods) ?: return action(listOf(declaration))

    if (isUnitTestMode()) return action(deepestSuperMethods)

    val kindIndex = when (declaration) {
        is KtNamedFunction -> 1 // "function"
        is KtProperty, is KtParameter -> 2 // "property"
        else -> return
    }

    val unwrappedSupers = deepestSuperMethods.mapNotNull { it.namedUnwrappedElement }
    val hasJavaMethods = unwrappedSupers.any { it is PsiMethod }
    val hasKtMembers = unwrappedSupers.any { it is KtNamedDeclaration }
    val superKindIndex = when {
        hasJavaMethods && hasKtMembers -> 3 // "member"
        hasJavaMethods -> 4 // "method"
        else -> kindIndex
    }

    val renameBase = KotlinBundle.message("rename.base.0", superKindIndex + (if (deepestSuperMethods.size > 1) 10 else 0))
    val renameCurrent = KotlinBundle.message("rename.only.current.0", kindIndex)

    JBPopupFactory.getInstance()
        .createPopupChooserBuilder(listOf(renameBase, renameCurrent))
        .setTitle(title)
        .setMovable(false)
        .setResizable(false)
        .setRequestFocus(true)
        .setItemChosenCallback { value: String? ->
            if (value == null) return@setItemChosenCallback
            val chosenElements = if (value == renameBase) deepestSuperMethods + declaration else listOf(declaration)
            action(chosenElements)
        }
        .createPopup()
        .showInBestPositionFor(editor)
}

fun checkSuperMethods(
    declaration: KtNamedDeclaration,
    deepestSuperMethods: List<PsiElement>
): List<PsiElement> {
    if (deepestSuperMethods.isEmpty()) return listOf(declaration)

    val title = getRenameBaseTitle(declaration, deepestSuperMethods) ?: return listOf(declaration)
    if (isUnitTestMode()) return deepestSuperMethods

    val exitCode = showYesNoCancelDialog(
        declaration.project, title, IdeBundle.message("title.warning"),
        KotlinBundle.message("button.rename.base"),
        KotlinBundle.message("button.rename.current"), CommonBundle.getCancelButtonText(), Messages.getQuestionIcon()
    )

    return when (exitCode) {
        Messages.YES -> deepestSuperMethods
        Messages.NO -> listOf(declaration)
        else -> emptyList()
    }
}

private fun getRenameBaseTitle(declaration: KtNamedDeclaration,
                               deepestSuperMethods: List<PsiElement>): @DialogMessage String? {
    val superMethod = deepestSuperMethods.first()
    val (superClass, isAbstract) = when (superMethod) {
        is PsiMember -> superMethod.containingClass to superMethod.hasModifierProperty(PsiModifier.ABSTRACT)
        is KtNamedDeclaration -> superMethod.containingClassOrObject to superMethod.isAbstract()
        else -> null
    } ?: return null

    if (superClass == null) return null

    return KotlinBundle.message(
        "rename.declaration.title.0.implements.1.2.of.3",
        declaration.name ?: "",
        if (isAbstract) 1 else 2,
        ElementDescriptionUtil.getElementDescription(superMethod, UsageViewTypeLocation.INSTANCE),
        SymbolPresentationUtil.getSymbolPresentableText(superClass)
    )
}
