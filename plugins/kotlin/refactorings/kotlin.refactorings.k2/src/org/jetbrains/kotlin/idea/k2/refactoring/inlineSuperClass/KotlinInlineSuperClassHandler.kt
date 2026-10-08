// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inlineSuperClass

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.inline.KotlinInlineActionHandler
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement

internal class KotlinInlineSuperClassHandler : KotlinInlineActionHandler() {
    override val refactoringName: String get() = KotlinBundle.message("inline.super.class.title")
    override val helpId: String get() = "Inline_Super_Class"

    override fun canInlineKotlinElement(element: KtElement): Boolean =
        element is KtClass && !element.isEnum() && !element.isAnnotation()

    override fun inlineKotlinElement(project: Project, editor: Editor?, element: KtElement) {
        val sourceClass = element as? KtClass ?: return
        if (!checkSources(project, editor, sourceClass)) return
        if (isUnitTestMode()) {
            KotlinInlineSuperClassProcessor(project, sourceClass).run()
        } else {
            KotlinInlineSuperClassDialog(sourceClass).show()
        }
    }
}
