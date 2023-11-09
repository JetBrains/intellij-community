// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.toolWindowWithActions

import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.actionSystem.AnAction
import javax.swing.JComponent

internal class ConsolePanelWithActions(val consoleView: ConsoleView,
                                       closeListeners: MutableCollection<Runnable>,
                                       actionListenerComponent: JComponent?,
                                       vararg customActions: AnAction,
) : PanelWithActions(consoleView.component,
                     closeListeners,
                     actionListenerComponent,
                     *customActions) {

}
