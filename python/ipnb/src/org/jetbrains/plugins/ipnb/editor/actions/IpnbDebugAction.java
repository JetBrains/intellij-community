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
import org.jetbrains.plugins.ipnb.debugger.IpnbDebuggerTransport;
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

      final String filePath = codePanel.getFileEditor().getVirtualFile().getPath();
      final String debugConnectionPath = IpnbDebuggerTransport.DEBUG_CONNECTION_PREFIX + filePath;

      if (!connectionManager.hasConnection(debugConnectionPath)) {
        try {
          mySession = IpnbDebugRunner.Companion.connectToDebugger(project, codePanel, debugConnectionPath);
        }
        catch (ExecutionException e) {
        }
      }

      // init extension on Kernel side

      //try to execute something

      final IpnbEditablePanel cell2 = component.getSelectedCellPanel();
      if (cell2 == null) return;
      cell2.onFinishExecutionAction(null);
      cell2.runCell(false);
      if (cell2 instanceof IpnbCodePanel) {
        final IpnbCodePanel cell3 = (IpnbCodePanel)cell2;
        cell3.updateCellSource();
        cell3.updatePrompt();
        connectionManager.executeCode(cell3, debugConnectionPath, "print('Hello debugger!')");
        cell3.setEditing(false);
      }
    }
  }
}
