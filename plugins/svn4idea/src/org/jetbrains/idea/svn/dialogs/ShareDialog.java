package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.ActionManager;

import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.*;

public class ShareDialog extends RepositoryBrowserDialog {

  public ShareDialog(Project project) {
    super(project);
  }

  public void init() {
    super.init();
    setTitle("Select Parent Location");
    setOKButtonText("Share");
    getRepositoryBrowser().addChangeListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        if (getOKAction() != null) {
          getOKAction().setEnabled(getRepositoryBrowser().getSelectedURL() != null);
        }
      }
    });
    getOKAction().setEnabled(getRepositoryBrowser().getSelectedURL() != null);
    ((RepositoryTreeModel) getRepositoryBrowser().getRepositoryTree().getModel()).setShowFiles(false);
  }

  protected JPopupMenu createPopup(boolean toolWindow) {
    DefaultActionGroup group = new DefaultActionGroup();
    DefaultActionGroup newGroup = new DefaultActionGroup("_New", true);
    newGroup.add(new AddLocationAction());
    newGroup.add(new MkDirAction());
    group.add(newGroup);
    group.addSeparator();
    group.add(new RefreshAction());
    group.add(new DiscardLocationAction());
    ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu("", group);
    return menu.getComponent();
  }
}
