// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.CollectionComboBoxModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.sorted;
import static com.intellij.util.ui.JBUI.*;
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

    setTitle(getTitle());
    init();
    myComboField.setText(initText());
  }

  @Override
  public String getTitle() {
    return message("repository.browser.add.location.title");
  }

  protected String initText() {
    return "http://";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myCombo;
  }

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
    wrapper.add(mainPanel, new GridBagConstraints(0, 0, 1, 1, 1, 1, NORTHWEST, HORIZONTAL, emptyInsets(), 0, 0));
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
      return new ValidationInfo("Could not parse url", myCombo);
    }
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction()};
  }

  @Nullable
  public Url getSelected() {
    return mySelected;
  }
}
