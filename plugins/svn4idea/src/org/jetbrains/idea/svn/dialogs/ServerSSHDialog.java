/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.auth.AcceptResult;

import javax.swing.*;
import java.awt.*;

public class ServerSSHDialog extends DialogWrapper {
  private AcceptResult myResult;
  private String myFingerprints;
  private JCheckBox myJCheckBox;
  private final boolean myStore;
  private final String myHost;
  private final String myAlgorithm;

  public ServerSSHDialog(Project project,
                         boolean store,
                         @NotNull final String host,
                         @Nullable final String algorithm,
                         @NotNull String fingerprints) {
    super(project, true);
    myStore = store;
    myHost = host;
    myAlgorithm = StringUtil.notNullize(algorithm);
    // todo ?
    myFingerprints = fingerprints;
    myResult = AcceptResult.REJECTED;
    setOKButtonText(SvnBundle.message("button.text.ssh.accept"));
    setCancelButtonText(SvnBundle.message("button.text.ssh.reject"));
    setTitle(SvnBundle.message("dialog.title.ssh.examine.server.fingerprints"));
    init();
    setResizable(false);
  }

  public boolean shouldCloseOnCross() {
    return false;
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction()};
  }

  protected void doOKAction() {
    myResult = myJCheckBox.isSelected() ? AcceptResult.ACCEPTED_PERMANENTLY : AcceptResult.ACCEPTED_TEMPORARILY;
    super.doOKAction();
  }

  public void doCancelAction() {
    myResult = AcceptResult.REJECTED;
    super.doCancelAction();
  }

  public AcceptResult getResult() {
    return myResult;
  }

  protected String getDimensionServiceKey() {
    return "org.jetbrains.idea.svn.dialogs.ServerSSHDialog";
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = JBUI.Panels.simplePanel(5, 5);
    final JPanel wrapper = new JPanel();
    final BoxLayout boxLayout = new BoxLayout(wrapper, BoxLayout.Y_AXIS);
    wrapper.setLayout(boxLayout);
    final JLabel label = new JLabel(SvnBundle.message("label.ssh.server.provided.fingerprints", myHost));
    wrapper.add(label);
    final JLabel label2 = new JLabel(SvnBundle.message("label.ssh.server.provided.fingerprints2", myAlgorithm));
    wrapper.add(label2);
    final JTextField textField = new JTextField(myFingerprints);
    textField.setEditable(false);
    textField.setBorder(JBUI.Borders.empty(10, 0));
    wrapper.add(textField);
    final JLabel label3 = new JLabel(SvnBundle.message("label.ssh.server.provided.fingerprints3"));
    label3.setBorder(JBUI.Borders.emptyBottom(5));
    wrapper.add(label3);
    panel.add(wrapper, BorderLayout.CENTER);
    myJCheckBox = new JCheckBox(SvnBundle.message("checkbox.svn.ssh.cache.fingerprint"));
    myJCheckBox.setSelected(myStore);
    myJCheckBox.setEnabled(myStore);
    panel.add(myJCheckBox, BorderLayout.SOUTH);
    return panel;
  }
}
