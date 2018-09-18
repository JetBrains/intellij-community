// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.project.Project;
import org.jetbrains.idea.svn.SvnBundle;

public class ImportDialog extends RepositoryBrowserDialog {
  public ImportDialog(Project project) {
    super(project);
  }

  @Override
  public void init() {
    super.init();
    setTitle(SvnBundle.message("import.dialog.title"));
    setOKButtonText(SvnBundle.message("import.dialog.button"));
    getRepositoryBrowser().addChangeListener(e -> updateOKAction());
    updateOKAction();
  }

  @Override
  protected String getHelpId() {
    return "vcs.subversion.import";
  }

  private void updateOKAction() {
    getOKAction().setEnabled(getRepositoryBrowser().getSelectedURL() != null);
  }

  @Override
  protected void doOKAction() {
    if (doImport()) {
      super.doOKAction();
    }
  }

  @Override
  protected boolean showImportAction() {
    return false;
  }
}