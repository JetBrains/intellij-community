package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.configuration.IpnbConnectionManager;
import org.jetbrains.plugins.ipnb.configuration.IpnbSettings;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbEditablePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;

import java.util.List;

public class IpnbRunAllCellsAction extends IpnbRunCellBaseAction {
  public IpnbRunAllCellsAction() {
    super();
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final DataContext context = event.getDataContext();
    final IpnbFileEditor ipnbEditor = IpnbFileEditor.DATA_KEY.getData(context);
    if (ipnbEditor != null) {
      final IpnbFilePanel ipnbFilePanel = ipnbEditor.getIpnbFilePanel();
      final List<IpnbEditablePanel> cells = ipnbFilePanel.getIpnbPanels();
      final Project project = ipnbFilePanel.getProject();
      final IpnbConnectionManager connectionManager = IpnbConnectionManager.getInstance(project);
      final VirtualFile virtualFile = ipnbEditor.getVirtualFile();
      final String path = virtualFile.getPath();
      if (!connectionManager.hasConnection(path)) {
        String url = IpnbSettings.getInstance(project).getURL();
        if (StringUtil.isEmptyOrSpaces(url)) {
          url = IpnbConnectionManager.showDialogUrl(url);
        }
        if (url == null) return;
        IpnbSettings.getInstance(project).setURL(url);
        final String finalUrl = url;
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            final boolean serverStarted = connectionManager.startIpythonServer(finalUrl, ipnbEditor);
            if (!serverStarted) {
              return;
            }
            UIUtil.invokeLaterIfNeeded(new Runnable() {
              @Override
              public void run() {
                connectionManager.startConnection(null, path, finalUrl, false);
              }
            });
            runCells(cells, ipnbFilePanel);
          }
        });

      }
      else {
        runCells(cells, ipnbFilePanel);
      }
    }
  }

  private static void runCells(List<IpnbEditablePanel> cells, IpnbFilePanel ipnbFilePanel) {
    for (IpnbEditablePanel cell : cells) {
      cell.runCell();
      ipnbFilePanel.revalidate();
      ipnbFilePanel.repaint();
      ipnbFilePanel.requestFocus();
    }
  }
}
