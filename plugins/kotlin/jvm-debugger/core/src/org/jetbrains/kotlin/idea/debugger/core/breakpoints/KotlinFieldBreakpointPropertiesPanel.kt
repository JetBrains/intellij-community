// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core.breakpoints

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBBox
import com.intellij.util.ui.DialogUtil
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import org.jetbrains.kotlin.idea.debugger.core.KotlinDebuggerCoreBundle
import java.awt.BorderLayout
import javax.swing.Box
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.properties.Delegates

class KotlinFieldBreakpointPropertiesPanel : XBreakpointCustomPropertiesPanel<XLineBreakpoint<KotlinPropertyBreakpointProperties>>() {
    private var myWatchInitializationCheckBox: JCheckBox by Delegates.notNull()
    private var myWatchAccessCheckBox: JCheckBox by Delegates.notNull()
    private var myWatchModificationCheckBox: JCheckBox by Delegates.notNull()

    override fun getComponent(): JComponent {
        myWatchInitializationCheckBox =
            JCheckBox(KotlinDebuggerCoreBundle.message("property.watchpoint.initialization"))
        myWatchAccessCheckBox = JCheckBox(KotlinDebuggerCoreBundle.message("property.watchpoint.access"))
        myWatchModificationCheckBox =
            JCheckBox(KotlinDebuggerCoreBundle.message("property.watchpoint.modification"))

        DialogUtil.registerMnemonic(myWatchInitializationCheckBox)
        DialogUtil.registerMnemonic(myWatchAccessCheckBox)
        DialogUtil.registerMnemonic(myWatchModificationCheckBox)

        fun Box.addNewPanelForCheckBox(checkBox: JCheckBox) {
            val panel = JPanel(BorderLayout())
            panel.add(checkBox, BorderLayout.NORTH)
            this.add(panel)
        }

        val watchBox = JBBox.createVerticalBox()
        watchBox.addNewPanelForCheckBox(myWatchInitializationCheckBox)
        watchBox.addNewPanelForCheckBox(myWatchAccessCheckBox)
        watchBox.addNewPanelForCheckBox(myWatchModificationCheckBox)

        val mainPanel = JPanel(BorderLayout())
        val innerPanel = JPanel(BorderLayout())
        innerPanel.add(watchBox, BorderLayout.CENTER)
        innerPanel.add(Box.createHorizontalStrut(3), BorderLayout.WEST)
        innerPanel.add(Box.createHorizontalStrut(3), BorderLayout.EAST)
        mainPanel.add(innerPanel, BorderLayout.NORTH)
        mainPanel.border = IdeBorderFactory.createTitledBorder(JavaDebuggerBundle.message("label.group.watch.events"), true)
        return mainPanel
    }

    override fun loadFrom(breakpoint: XLineBreakpoint<KotlinPropertyBreakpointProperties>) {
        myWatchInitializationCheckBox.isSelected = breakpoint.properties.watchInitialization
        myWatchAccessCheckBox.isSelected = breakpoint.properties.watchAccess
        myWatchModificationCheckBox.isSelected = breakpoint.properties.watchModification
    }

    override fun saveTo(breakpoint: XLineBreakpoint<KotlinPropertyBreakpointProperties>) {
        var changed = breakpoint.properties.watchAccess != myWatchAccessCheckBox.isSelected
        breakpoint.properties.watchAccess = myWatchAccessCheckBox.isSelected

        changed = breakpoint.properties.watchModification != myWatchModificationCheckBox.isSelected || changed
        breakpoint.properties.watchModification = myWatchModificationCheckBox.isSelected

        changed = breakpoint.properties.watchInitialization != myWatchInitializationCheckBox.isSelected || changed
        breakpoint.properties.watchInitialization = myWatchInitializationCheckBox.isSelected

        if (changed) {
            (breakpoint as XBreakpointBase<*, *, *>).fireBreakpointChanged()
        }
    }
}
