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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckoutProvider;
import org.jetbrains.idea.svn.SvnBundle;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;

public class CheckoutDialog extends RepositoryBrowserDialog {
  private final CheckoutProvider.Listener myListener;

  public CheckoutDialog(Project project, final CheckoutProvider.Listener listener) {
    super(project, false, null);
    myListener = listener;
  }

  public void init() {
    super.init();
    setTitle(SvnBundle.message("checkout.dialog.title"));
    setOKButtonText(SvnBundle.message("checkout.dialog.button"));
    getRepositoryBrowser().addChangeListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        if (getOKAction() != null) {
          getOKAction().setEnabled(getRepositoryBrowser().getSelectedURL() != null);
        }
      }
    });
    getOKAction().setEnabled(getRepositoryBrowser().getSelectedURL() != null);
  }

  protected String getHelpId() {
    return "vcs.subversion.browseSVN";
  }

  protected void doOKAction() {
    final RepositoryTreeNode selectedNode = getSelectedNode();
    close(OK_EXIT_CODE);
    doCheckout(myListener, selectedNode);
  }
}
