// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inlineSuperClass

import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.ui.components.JBLabel
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtClass
import java.awt.GridLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class KotlinInlineSuperClassDialog(
    private val sourceClass: KtClass,
) : RefactoringDialog(sourceClass.project, false) {
    init {
        title = KotlinBundle.message("inline.super.class.title")
        init()
    }

    override fun createCenterPanel(): JComponent = JPanel(GridLayout(0, 1)).apply {
        add(JBLabel(KotlinBundle.message("inline.super.class.label", sourceClass.name.orEmpty())))
        add(JBLabel(KotlinBundle.message("inline.super.class.all")))
    }

    override fun getHelpId(): String = "Inline_Super_Class"

    override fun doAction() {
        invokeRefactoring(KotlinInlineSuperClassProcessor(project, sourceClass))
    }
}
