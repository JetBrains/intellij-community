// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.StandardVersionFilterComponent;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class SvnVersionFilterComponent extends StandardVersionFilterComponent<ChangeBrowserSettings> {
  private JCheckBox myUseAuthorFilter;
  private JTextField myAuthorField;
  private JPanel myPanel;
  private JPanel myStandardPanel;
  private JCheckBox myStopOnCopyCheckBox;

  public SvnVersionFilterComponent(boolean showDateFilter) {
    super(showDateFilter);
    myStandardPanel.setLayout(new BorderLayout());
    myStandardPanel.add(getStandardPanel(), BorderLayout.CENTER);
    init(new ChangeBrowserSettings());
  }

  @Override
  protected void updateAllEnabled(@Nullable ActionEvent e) {
    super.updateAllEnabled(e);
    updatePair(myUseAuthorFilter, myAuthorField, e);
  }

  @Override
  protected void initValues(@NotNull ChangeBrowserSettings settings) {
    super.initValues(settings);
    myUseAuthorFilter.setSelected(settings.USE_USER_FILTER);
    myAuthorField.setText(settings.USER);
    myStopOnCopyCheckBox.setSelected(settings.STOP_ON_COPY);
  }

  @Override
  public void saveValues(@NotNull ChangeBrowserSettings settings) {
    super.saveValues(settings);
    settings.USER = myAuthorField.getText();
    settings.USE_USER_FILTER = myUseAuthorFilter.isSelected();
    settings.STOP_ON_COPY = myStopOnCopyCheckBox.isSelected();
  }

  @Override
  protected void installCheckBoxListener(@NotNull ActionListener filterListener) {
    super.installCheckBoxListener(filterListener);
    myUseAuthorFilter.addActionListener(filterListener);
    myAuthorField.addActionListener(filterListener);
    myStopOnCopyCheckBox.addActionListener(filterListener);
  }

  public JPanel getPanel() {
    return myPanel;
  }

  @Nullable
  public String getAuthorFilter() {
    if (myUseAuthorFilter.isSelected() && myAuthorField.getText().length() > 0) {
      return myAuthorField.getText();
    }
    else {
      return null;
    }
  }

  @Override
  protected String getChangeNumberTitle() {
    return SvnBundle.message("revision.title");
  }

  @Override
  protected @Nls String getChangeFromParseError() {
    return SvnBundle.message("error.revision.from.must.be.a.valid.number");
  }

  @Override
  protected @Nls String getChangeToParseError() {
    return SvnBundle.message("error.revision.to.must.be.a.valid.number");
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return getPanel();
  }
}
