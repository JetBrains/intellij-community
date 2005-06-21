package com.intellij.execution.ui;

import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.InsertPathAction;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * User: anna
 * Date: Jun 21, 2005
 */
public class AlternativeJREPanel extends JPanel{
  private TextFieldWithBrowseButton myPathField;
  private JCheckBox myCbEnabled;

  public AlternativeJREPanel() {
    super(new GridBagLayout());
    myCbEnabled = new JCheckBox("Use alternative JRE: ");
    myCbEnabled.setMnemonic('U');
    final GridBagConstraints gc = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0, GridBagConstraints.NORTHWEST,
                                                         GridBagConstraints.HORIZONTAL, new Insets(2, -2, 2, 2), 0, 0);
    add(myCbEnabled, gc);

    myPathField = new TextFieldWithBrowseButton();
    myPathField.addBrowseFolderListener("Select Alternative JRE", "Select directory with JRE to run with", null, BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR);
    gc.insets.left = 20;
    add(myPathField, gc);
    InsertPathAction.addTo(myPathField.getTextField());

    gc.weighty = 1;
    add(Box.createVerticalBox(), gc);

    myCbEnabled.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        enabledChanged();
      }
    });
    enabledChanged();
  }

  private void enabledChanged() {
    final boolean pathEnabled = isPathEnabled();
    GuiUtils.enableChildren(myPathField, pathEnabled, null);
  }

  public String getPath() {
    return FileUtil.toSystemIndependentName(myPathField.getText().trim());
  }

  public void setPath(final String path) {
    myPathField.setText(FileUtil.toSystemDependentName(path == null ? "" : path));
  }

  public boolean isPathEnabled() {
    return myCbEnabled.isSelected();
  }
  public void setPathEnabled(boolean b) {
    myCbEnabled.setSelected(b);
    enabledChanged();
  }

  public void init(String path, boolean isEnabled){
    setPathEnabled(isEnabled);
    setPath(path);
  }

}

