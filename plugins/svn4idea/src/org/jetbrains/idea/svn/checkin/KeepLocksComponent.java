// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.checkin;

import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;

import javax.swing.*;
import java.awt.*;

class KeepLocksComponent implements RefreshableOnComponent {
  @NotNull private final SvnVcs mySvnVcs;
  @NotNull private final JCheckBox myKeepLocksBox;
  @NotNull private final JPanel myPanel;
  @NotNull private final JCheckBox myAutoUpdate;

  KeepLocksComponent(@NotNull SvnVcs vcs) {
    mySvnVcs = vcs;
    myPanel = new JPanel(new BorderLayout());
    myKeepLocksBox = new JCheckBox(SvnBundle.message("checkbox.checkin.keep.files.locked"));
    myAutoUpdate = new JCheckBox(SvnBundle.message("checkbox.checkin.auto.update.after.commit"));

    myPanel.add(myAutoUpdate, BorderLayout.NORTH);
    myPanel.add(myKeepLocksBox, BorderLayout.CENTER);
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  public boolean isKeepLocks() {
    return myKeepLocksBox.isSelected();
  }

  public boolean isAutoUpdate() {
    return myAutoUpdate.isSelected();
  }

  @Override
  public void saveState() {
    final SvnConfiguration configuration = mySvnVcs.getSvnConfiguration();
    configuration.setKeepLocks(isKeepLocks());
    configuration.setAutoUpdateAfterCommit(isAutoUpdate());
  }

  @Override
  public void restoreState() {
    final SvnConfiguration configuration = mySvnVcs.getSvnConfiguration();
    myKeepLocksBox.setSelected(configuration.isKeepLocks());
    myAutoUpdate.setSelected(configuration.isAutoUpdateAfterCommit());
  }
}
