/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;

import javax.swing.*;
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
          updateOKAction();
        }
      }
    });
    updateOKAction();
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[] {getOKAction(), getCancelAction(), getHelpAction()};
  }
  @Override
  protected String getHelpId() {
    return "vcs.subversion.import";
  }

  private void updateOKAction() {
    getOKAction().setEnabled(getRepositoryBrowser().getSelectedURL() != null);
  }

  protected void doOKAction() {
    if(doImport()) {
      super.doOKAction();
    }
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("vcs.subversion.import");
  }

  @Override
  protected boolean showImportAction() {
    return false;
  }
}
