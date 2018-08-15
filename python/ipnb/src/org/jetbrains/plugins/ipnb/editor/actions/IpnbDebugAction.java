// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.configuration.IpnbConnectionManager;
import org.jetbrains.plugins.ipnb.debugger.IpnbDebugRunner;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbEditablePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodePanel;

public class IpnbDebugAction extends AnAction {
  private final IpnbFileEditor myFileEditor;
  private XDebugSession mySession = null;

  public IpnbDebugAction(IpnbFileEditor fileEditor) {
    super("Debug Cell", "Debug Cell", AllIcons.Actions.StartDebugger);
    myFileEditor = fileEditor;
    getTemplatePresentation().setText("Debug Cell");
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final IpnbFilePanel component = myFileEditor.getIpnbFilePanel();
    final IpnbEditablePanel cell = component.getSelectedCellPanel();
    if (cell == null) return;
    if (cell instanceof IpnbCodePanel) {
      final IpnbCodePanel codePanel = (IpnbCodePanel)cell;
      cell.onFinishExecutionAction(null);
      final Project project = event.getProject();
      if (project == null) return;
      final IpnbConnectionManager connectionManager = IpnbConnectionManager.getInstance(project);

      if (mySession == null || !connectionManager.hasConnection("Debug")) {
        try {
          mySession = IpnbDebugRunner.Companion.connectToDebugger(project, codePanel);
        }
        catch (ExecutionException e) {
        }
      }
    }
  }
}
