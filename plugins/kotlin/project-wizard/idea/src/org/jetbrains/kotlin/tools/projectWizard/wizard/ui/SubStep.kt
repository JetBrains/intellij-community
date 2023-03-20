// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.wizard.ui


import org.jetbrains.kotlin.tools.projectWizard.core.Context
import java.awt.BorderLayout
import javax.swing.JComponent

abstract class SubStep(
    context: Context
) : DynamicComponent(context) {
    protected abstract fun buildContent(): JComponent

    final override val component: JComponent by lazy(LazyThreadSafetyMode.NONE) {
        customPanel {
            add(buildContent(), BorderLayout.CENTER)
        }
    }
}

