// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupFactory
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.getParameterNames
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.K2ExtractableSubstringInfo
import org.jetbrains.kotlin.idea.util.ElementKind
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal fun chooseApplicableComponentNames(
    contextExpression: KtExpression,
    editor: Editor?,
    callback: (List<String>) -> Unit
) {
    val componentNames = analyzeInModalWindow(contextExpression, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
        getParameterNames(contextExpression)
    } ?: return callback(emptyList())

    if (componentNames.size <= 1) return callback(emptyList())

    if (isUnitTestMode()) return callback(componentNames)

    if (editor == null) return callback(emptyList())

    val singleVariable = KotlinBundle.message("text.create.single.variable")
    val listOfVariants = listOf(
        singleVariable,
        KotlinBundle.message("text.create.destructuring.declaration"),
    )

    JBPopupFactory.getInstance()
        .createPopupChooserBuilder(listOfVariants)
        .setMovable(true)
        .setResizable(false)
        .setRequestFocus(true)
        .setItemChosenCallback { callback(if (it == singleVariable) emptyList() else componentNames) }
        .createPopup()
        .showInBestPositionFor(editor)
}

// a copy-paste of `org.jetbrains.kotlin.idea.refactoring.introduce.findStringTemplateFragment` from `idea.kotlin` module
internal fun findStringTemplateFragment(file: KtFile, startOffset: Int, endOffset: Int, kind: ElementKind): KtExpression? {
    if (kind != ElementKind.EXPRESSION) return null

    val startEntry = file.findElementAt(startOffset)?.getNonStrictParentOfType<KtStringTemplateEntry>() ?: return null
    val endEntry = file.findElementAt(endOffset - 1)?.getNonStrictParentOfType<KtStringTemplateEntry>() ?: return null

    if (startEntry.parent !is KtStringTemplateExpression || startEntry.parent != endEntry.parent) return null

    val prefixOffset = startOffset - startEntry.startOffset
    if (startEntry !is KtLiteralStringTemplateEntry && prefixOffset > 0) return null

    val suffixOffset = endOffset - endEntry.startOffset
    if (endEntry !is KtLiteralStringTemplateEntry && suffixOffset < endEntry.textLength) return null

    val prefix = startEntry.text.substring(0, prefixOffset)
    val suffix = endEntry.text.substring(suffixOffset)

    return K2ExtractableSubstringInfo(startEntry, endEntry, prefix, suffix).createExpression()
}