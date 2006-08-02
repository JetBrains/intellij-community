package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.project.Project;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;

public class ImportDialog extends RepositoryBrowserDialog {

  public ImportDialog(Project project) {
    super(project);
  }

  public void init() {
    super.init();
    setTitle("Import into Subversion");
    setOKButtonText("Import");
    getRepositoryBrowser().addChangeListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        if (getOKAction() != null) {
          getOKAction().setEnabled(getRepositoryBrowser().getSelectedURL() != null);
        }
      }
    });
    getOKAction().setEnabled(getRepositoryBrowser().getSelectedURL() != null);
  }

  protected void doOKAction() {
    doImport();
    super.doOKAction();
  }
}
