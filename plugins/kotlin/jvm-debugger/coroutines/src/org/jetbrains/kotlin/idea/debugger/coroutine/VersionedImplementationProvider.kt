// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.coroutine

import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import javax.swing.ListCellRenderer

class VersionedImplementationProvider {
    fun comboboxListCellRenderer(): ListCellRenderer<in String>? =
        SimpleListCellRenderer.create { label: JBLabel, value: String?, index: Int ->
            if (value != null) {
                label.text = value
            } else if (index >= 0) {
                label.text = KotlinDebuggerCoroutinesBundle.message("coroutine.dump.threads.loading")
            }
        }
}