// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupFactory
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.getParameterNames
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.KtExpression

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
