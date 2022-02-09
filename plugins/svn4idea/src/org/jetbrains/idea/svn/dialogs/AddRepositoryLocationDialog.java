// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.sorted;
import static com.intellij.util.ui.JBUI.insets;
import static com.intellij.util.ui.JBUI.size;
import static java.awt.GridBagConstraints.*;
import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.SvnUtil.createUrl;

public class AddRepositoryLocationDialog extends DialogWrapper {
  @NotNull private final List<String> myPreviousLocations;
  private JComboBox myCombo;
  private Url mySelected;
  private JTextField myComboField;

  public AddRepositoryLocationDialog(@NotNull Project project, @NotNull List<String> values) {
    super(project, true);
    myPreviousLocations = sorted(values);

    init();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myCombo;
  }

  @Override
  protected JComponent createCenterPanel() {
    JLabel selectText = new JLabel(message("repository.browser.add.location.prompt"));
    selectText.setUI(new MultiLineLabelUI());

    JPanel mainPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gb = new GridBagConstraints(0, 0, 1, 1, 1, 0, NORTHWEST, NONE, insets(5), 0, 0);

    mainPanel.add(selectText, gb);

    ++gb.gridy;

    myCombo = new ComboBox<>(new CollectionComboBoxModel<>(myPreviousLocations));
    myCombo.setEditable(true);
    myCombo.setMinimumSize(size(250, 20));
    gb.fill = HORIZONTAL;
    mainPanel.add(myCombo, gb);
    gb.fill = NONE;

    myComboField = (JTextField)myCombo.getEditor().getEditorComponent();

    JPanel wrapper = new JPanel(new GridBagLayout());
    wrapper.add(mainPanel, new GridBagConstraints(0, 0, 1, 1, 1, 1, NORTHWEST, HORIZONTAL, JBInsets.emptyInsets(), 0, 0));
    wrapper.setPreferredSize(size(400, 70));
    return wrapper;
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    try {
      mySelected = createUrl(myComboField.getText().trim(), false);
      return null;
    }
    catch (SvnBindException e) {
      return new ValidationInfo(message("dialog.message.could.not.parse.url"), myCombo);
    }
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{getOKAction(), getCancelAction()};
  }

  @Nullable
  public Url getSelected() {
    return mySelected;
  }

  public void setSelected(@NotNull Url url) {
    myComboField.setText(url.toDecodedString());
  }
}
