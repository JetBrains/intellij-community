
package com.intellij.ide.navigationToolbar;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;

import java.awt.*;

/**
 * User: anna
 * Date: 19-Dec-2005
 */
public class PopupToolbarAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) return;
    if (UISettings.getInstance().SHOW_NAVIGATION_BAR){
      new SelectInNavBarTarget(project).select(null, false);
      return;
    }
    final Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    final NavBarPanel toolbarPanel = new NavBarPanel(project) {
      public Dimension getPreferredSize() {
        final Dimension dimension = super.getPreferredSize();
        return new Dimension(getPreferredWidth(), dimension.height);
      }
    };
    toolbarPanel.showHint(editor, dataContext);
  }
}
