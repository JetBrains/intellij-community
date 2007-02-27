package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.project.Project;
import org.jetbrains.idea.svn.SvnBundle;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;

public class ImportDialog extends RepositoryBrowserDialog {
  public ImportDialog(Project project) {
    super(project);
  }

  public void init() {
    super.init();
    setTitle(SvnBundle.message("import.dialog.title"));
    setOKButtonText(SvnBundle.message("import.dialog.button"));
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

  @Override
  protected boolean showImportAction() {
    return false;
  }
}
