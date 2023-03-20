// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckoutProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;

import javax.swing.*;

public class CheckoutDialog extends RepositoryBrowserDialog {
  private final CheckoutProvider.Listener myListener;

  public CheckoutDialog(Project project, final CheckoutProvider.Listener listener) {
    super(project, false, null);
    myListener = listener;
  }

  @Override
  public void init() {
    super.init();
    setTitle(SvnBundle.message("checkout.dialog.title"));
    setOKButtonText(SvnBundle.message("checkout.dialog.button"));
    getRepositoryBrowser().addChangeListener(e -> {
      getOKAction().setEnabled(getRepositoryBrowser().getSelectedURL() != null);
    });
    getOKAction().setEnabled(getRepositoryBrowser().getSelectedURL() != null);
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[] {getOKAction(), getCancelAction(), getHelpAction()};
  }
  @Override
  protected String getHelpId() {
    return "vcs.subversion.browseSVN";
  }

  @Override
  protected void doOKAction() {
    final RepositoryTreeNode selectedNode = getSelectedNode();
    close(OK_EXIT_CODE);
    doCheckout(myListener, selectedNode);
  }
}
