/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.dialogs.DialogUtil;
import org.jetbrains.idea.svn.dialogs.SelectLocationDialog;
import org.jetbrains.annotations.NonNls;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class SvnUpdateConfigurable implements Configurable, ActionListener {
  private SvnVcs myVCS;

  private TextFieldWithBrowseButton myURLText;
  private JLabel myURLLabel;
  private JCheckBox myRevisionBox;
  private JTextField myRevisionText;
  private JCheckBox myStatusBox;
  private JCheckBox myRecursiveBox;
  private String myURL;
  private JRadioButton myUpdateButton;
  private JRadioButton myMergeButton;
  private JLabel myMergeURLLabel1;
  private JLabel myMergeURLLabel2;
  private TextFieldWithBrowseButton myMergeText1;
  private TextFieldWithBrowseButton myMergeText2;
  private JLabel myMergeRevisionLabel1;
  private JTextField myMergeRevisionText1;
  private JLabel myMergeRevisionLabel2;
  private JTextField myMergeRevisionText2;
  private JCheckBox myDryRunCheckbox;
  @NonNls private static final String HELP_ID = "vcs.subversion.updateProject";
  @NonNls public static final String HEAD_REVISION = "HEAD";

  public SvnUpdateConfigurable(SvnVcs vcs, String url) {
    myVCS = vcs;
    myURL = url;
  }

  public String getDisplayName() {
    return SvnBundle.message("update.switch.configurable.name");
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return HELP_ID;
  }

  public SVNRevision getTargetRevision() {
    if (!myRevisionText.isEnabled()) {
      return SVNRevision.HEAD;
    }
    String revStr = myRevisionText.getText();
    SVNRevision rev = SVNRevision.parse(revStr);
    if (!rev.isValid() || rev.isLocal()) {
      return null;
    }
    return rev;
  }

  public boolean isUpdate() {
    return myURL == null || myURL.equals(getTargetURL());
  }

  public String getTargetURL() {
    return myURLText.getText();
  }

  public boolean isRunStatus() {
    return myStatusBox.isSelected();
  }

  public boolean isRecursive() {
    return myRecursiveBox.isSelected();
  }

  public boolean isMerge() {
    return myMergeButton.isSelected();
  }

  public boolean isDryRun() {
    return myDryRunCheckbox.isSelected();
  }

  public String getMergeURL1() {
    return myMergeText1.getText();
  }

  public String getMergeURL2() {
    return myMergeText2.getText();
  }

  public SVNRevision getMergeRevision1() {
    String revStr = myMergeRevisionText1.getText();
    SVNRevision rev = SVNRevision.parse(revStr);
    if (!rev.isValid() || rev.isLocal()) {
      return null;
    }
    return rev;
  }

  public SVNRevision getMergeRevision2() {
    String revStr = myMergeRevisionText2.getText();
    SVNRevision rev = SVNRevision.parse(revStr);
    if (!rev.isValid() || rev.isLocal()) {
      return null;
    }
    return rev;
  }

  public JComponent createComponent() {
    JPanel component = new JPanel(new GridBagLayout());

    GridBagConstraints gc = new GridBagConstraints();

    gc.gridx = 0;
    gc.gridy = 0;
    gc.gridwidth = 4;
    gc.gridheight = 1;
    gc.weightx = 0;
    gc.weighty = 0;
    gc.insets = new Insets(2, 2, 2, 2);
    gc.anchor = GridBagConstraints.WEST;
    gc.fill = GridBagConstraints.NONE;

    myUpdateButton = new JRadioButton(SvnBundle.message("radio.update.switch.configurable.update.or.switch"));
    Font boldFont = myUpdateButton.getFont().deriveFont(Font.BOLD);
    myUpdateButton.setFont(boldFont);

    DialogUtil.registerMnemonic(myUpdateButton);
    component.add(myUpdateButton, gc);

    gc.gridy += 1;
    gc.gridwidth = 1;

    myURLLabel = new JLabel(SvnBundle.message("label.update.switch.configurable.url"));
    component.add(myURLLabel, gc);

    gc.gridx += 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.gridwidth = 3;
    gc.weightx = 1;

    myURLText = new TextFieldWithBrowseButton(this);
    myURLText.setEditable(false);
    component.add(myURLText, gc);
    DialogUtil.registerMnemonic(myURLLabel, myURLText);


    gc.gridy += 1;
    gc.gridx = 0;
    gc.weightx = 0;
    gc.gridwidth = 2;
    gc.fill = GridBagConstraints.NONE;
    myRevisionBox = new JCheckBox(SvnBundle.message("checkbox.update.switch.configurable.to.specific.revision"));
    component.add(myRevisionBox, gc);
    DialogUtil.registerMnemonic(myRevisionBox);
    myRevisionBox.addActionListener(this);

    gc.gridx += 2;
    gc.gridwidth = 1;

    myRevisionText = new JTextField(8);
    myRevisionText.setMinimumSize(myRevisionText.getPreferredSize());
    myRevisionText.setText(HEAD_REVISION);
    myRevisionText.selectAll();
    component.add(myRevisionText, gc);

    // merge part.
    gc.gridy += 1;
    gc.gridx = 0;
    gc.gridwidth = 4;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    component.add(new JSeparator(), gc);

    gc.gridy += 1;
    gc.weightx = 0;
    gc.fill = GridBagConstraints.NONE;

    myMergeButton = new JRadioButton(SvnBundle.message("radio.update.switch.configurable.merge"));
    myMergeButton.setFont(boldFont);
    component.add(myMergeButton, gc);
    DialogUtil.registerMnemonic(myMergeButton);

    gc.gridy += 1;
    gc.gridwidth = 1;
    myMergeURLLabel1 = new JLabel(SvnBundle.message("label.update.switch.configurable.url1"));
    component.add(myMergeURLLabel1, gc);

    gc.gridx += 1;
    gc.gridwidth = 3;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    myMergeText1 = new TextFieldWithBrowseButton();
    myMergeText1.setEditable(false);
    component.add(myMergeText1, gc);

    gc.gridy += 1;
    gc.gridwidth = 1;
    gc.gridx = 0;
    gc.weightx = 0;
    gc.fill = GridBagConstraints.NONE;
    myMergeRevisionLabel1 = new JLabel(SvnBundle.message("label.update.switch.configurable.revision1"));
    component.add(myMergeRevisionLabel1, gc);

    gc.gridx += 1;
    gc.gridwidth = 1;
    myMergeRevisionText1 = new JTextField(8);
    myMergeRevisionText1.setMinimumSize(myMergeRevisionText1.getPreferredSize());
    component.add(myMergeRevisionText1, gc);

    DialogUtil.registerMnemonic(myMergeRevisionLabel1, myMergeRevisionText1);

    gc.gridy += 1;
    gc.gridwidth = 1;
    gc.gridx = 0;
    gc.weightx = 0;
    gc.fill = GridBagConstraints.NONE;
    myMergeURLLabel2 = new JLabel(SvnBundle.message("lable.update.switch.configurable.url2"));
    component.add(myMergeURLLabel2, gc);

    gc.gridx += 1;
    gc.gridwidth = 3;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    myMergeText2 = new TextFieldWithBrowseButton();
    myMergeText2.setEditable(false);
    component.add(myMergeText2, gc);

    gc.gridy += 1;
    gc.gridwidth = 1;
    gc.gridx = 0;
    gc.weightx = 0;
    gc.fill = GridBagConstraints.NONE;
    myMergeRevisionLabel2 = new JLabel(SvnBundle.message("update.switch.configurable.revision2"));
    component.add(myMergeRevisionLabel2, gc);

    gc.gridx += 1;
    gc.gridwidth = 1;
    gc.fill = GridBagConstraints.NONE;
    myMergeRevisionText2 = new JTextField(8);
    myMergeRevisionText2.setMinimumSize(myMergeRevisionText2.getPreferredSize());
    component.add(myMergeRevisionText2, gc);

    DialogUtil.registerMnemonic(myMergeRevisionLabel2, myMergeRevisionText2);

    DialogUtil.registerMnemonic(myMergeURLLabel1, myMergeText1);
    DialogUtil.registerMnemonic(myMergeURLLabel2, myMergeText2);

    gc.gridx = 0;
    gc.gridwidth = 4;
    gc.gridy += 1;

    myDryRunCheckbox = new JCheckBox(SvnBundle.message("checkbox.update.switch.configurable.try.merge.without.changes"));
    component.add(myDryRunCheckbox, gc);
    DialogUtil.registerMnemonic(myDryRunCheckbox);

    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 1;
    gc.gridy += 1;

    component.add(new JSeparator(), gc);
    gc.gridy += 1;

    // common part

    myRecursiveBox = new JCheckBox(SvnBundle.message("checkbox.update.switch.configurable.descend.into.child.directories"));
    component.add(myRecursiveBox, gc);
    DialogUtil.registerMnemonic(myRecursiveBox);

    gc.gridy += 1;

    myStatusBox = new JCheckBox(SvnBundle.message("checkbox.update.switch.configurable.run.status"));
    component.add(myStatusBox, gc);
    DialogUtil.registerMnemonic(myStatusBox);

    gc.gridy += 1;
    gc.fill = GridBagConstraints.BOTH;
    gc.weightx = 1;
    gc.weighty = 1;
    component.add(new JLabel(), gc);

    ButtonGroup group = new ButtonGroup();
    group.add(myUpdateButton);
    group.add(myMergeButton);

    init();
    return component;
  }

  public void init() {
    myUpdateButton.setSelected(true);
    myMergeButton.setEnabled(myURL != null);

    if (myURL != null) {
      myURLText.setText(myURL);
    }

    myMergeRevisionText1.setText(HEAD_REVISION);
    myMergeRevisionText1.selectAll();
    myMergeRevisionText2.setText(HEAD_REVISION);
    myMergeRevisionText2.selectAll();
    if (myURL != null) {
      myMergeText1.setText(myURL);
      myMergeText2.setText(myURL);
    }

    myRecursiveBox.setSelected(true);
    myRecursiveBox.setEnabled(myURL != null);
    myUpdateButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateState();
        myMergeRevisionText1.selectAll();
        myMergeRevisionText2.selectAll();
      }
    });
    myMergeButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateState();
        myMergeRevisionText1.selectAll();
        myMergeRevisionText2.selectAll();
      }
    });
    myMergeText1.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        String url = myMergeText1.getText();
        SelectLocationDialog dialog = new SelectLocationDialog(myVCS.getProject(), url, null, null, true);
        dialog.show();
        if (dialog.isOK()) {
          url = dialog.getSelectedURL();
          if (url != null) {
            myMergeText1.setText(url);
          }
        }
      }
    });
    myMergeText2.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        String url = myMergeText2.getText();
        SelectLocationDialog dialog = new SelectLocationDialog(myVCS.getProject(), url, null, null, true);
        dialog.show();
        if (dialog.isOK()) {
          url = dialog.getSelectedURL();
          if (url != null) {
            myMergeText2.setText(url);
          }
        }
      }
    });
    updateState();
  }

  private void updateState() {
    boolean merge = myMergeButton.isSelected();

    myRevisionBox.setEnabled(!merge && myURL != null);
    myRevisionText.setEnabled(!merge && myURL != null && myRevisionBox.isSelected());
    myURLText.setEnabled(!merge && myURL != null);
    myURLLabel.setEnabled(!merge && myURL != null);

    myMergeRevisionLabel1.setEnabled(merge);
    myMergeRevisionLabel2.setEnabled(merge);
    myMergeRevisionText1.setEnabled(merge);
    myMergeRevisionText2.setEnabled(merge);
    myMergeURLLabel1.setEnabled(merge);
    myMergeURLLabel2.setEnabled(merge);
    myMergeText1.setEnabled(merge);
    myMergeText2.setEnabled(merge);
    myDryRunCheckbox.setEnabled(merge);
  }

  public boolean isModified() {
    return false;
  }

  public void apply() throws ConfigurationException {
    SVNRevision revision = getTargetRevision();
    if (revision == null) {
      throw new ConfigurationException(SvnBundle.message("exception.text.invalid.revision", myRevisionText.getText()));
    }
  }

  public void reset() {
  }

  public void disposeUIResources() {
  }

  public void actionPerformed(ActionEvent e) {
    if (e.getSource() == myRevisionBox) {
      myRevisionText.setEnabled(myRevisionBox.isSelected());
      if (myRevisionBox.isSelected()) {
        myRevisionText.selectAll();
        myRevisionText.requestFocus();
      }
    }
    else {
      String url = getTargetURL();
      SelectLocationDialog dialog = new SelectLocationDialog(myVCS.getProject(), url);
      dialog.show();
      if (dialog.isOK()) {
        url = dialog.getSelectedURL();
        if (url != null) {
          myURLText.setText(url);
        }
      }
    }
  }
}
