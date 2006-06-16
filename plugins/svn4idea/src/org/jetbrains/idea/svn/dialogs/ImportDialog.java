package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.project.Project;
import org.jetbrains.idea.svn.checkout.SvnCheckoutProvider;
import org.tmatesoft.svn.core.SVNURL;

public class ImportDialog extends RepositoryBrowserDialog {

  public ImportDialog(Project project) {
    super(project);
  }

  public void init() {
    super.init();
    setTitle("Import into Subversion");
    setOKButtonText("Import");
  }

  protected void doOKAction() {
    doImport();
    super.doOKAction();
  }
}
