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
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 05.07.2005
 * Time: 23:35:12
 */
public class CopyDialog extends DialogWrapper implements ActionListener {
  private File mySrcFile;
  private Project myProject;
  private String myURL;

  private JTextField myFromURLText;
  private TextFieldWithBrowseButton myToURLText;

  private JRadioButton myWorkingRevisionButton;
  private JRadioButton myHEADRevisionButton;
  private JRadioButton mySpecificRevisionButton;
  private JTextField myRevisionText;
  private JTextArea myCommentText;
  private String mySrcURL;

  @NonNls private static final String HELP_ID = "vcs.subversion.branch";

  public CopyDialog(Project project, boolean canBeParent, File file) {
    super(project, canBeParent);
    mySrcFile = file;
    myProject = project;
    setResizable(true);
    setTitle(SvnBundle.message("dialog.title.branch"));
    getHelpAction().setEnabled(true);
    init();
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HELP_ID);
  }

  protected void init() {
    super.init();
    SvnVcs vcs = SvnVcs.getInstance(myProject);
    String revStr = "";
    try {
      SVNWCClient client = vcs.createWCClient();
      SVNInfo info = client.doInfo(mySrcFile, SVNRevision.WORKING);
      if (info != null) {
        mySrcURL = info.getURL() == null ? null : info.getURL().toString();
        revStr = info.getRevision() + "";
        myURL = mySrcURL;
        if (myURL != null) {
          @NonNls String dstName = "CopyOf" + SVNPathUtil.tail(myURL);
          if (mySrcFile.isDirectory()) {
            myURL = SVNPathUtil.append(myURL, dstName);
          }
          else {
            myURL = SVNPathUtil.append(SVNPathUtil.removeTail(myURL), dstName);
          }
        }
      }
    }
    catch (SVNException e) {
      //
    }
    if (myURL == null) {
      return;
    }
    myFromURLText.setText(mySrcURL);
    myToURLText.setText(myURL);
    myRevisionText.setText(revStr);
    myRevisionText.selectAll();
    myRevisionText.setEnabled(mySpecificRevisionButton.isSelected());

    myWorkingRevisionButton.setSelected(true);

    getOKAction().setEnabled(isOKActionEnabled());
  }

  public String getComment() {
    return myCommentText.getText();
  }

  public SVNRevision getRevision() {
    if (myWorkingRevisionButton.isSelected()) {
      return SVNRevision.WORKING;
    }
    else if (myHEADRevisionButton.isSelected()) {
      return SVNRevision.HEAD;
    }
    else {
      String revStr = myRevisionText.getText();
      return SVNRevision.parse(revStr);
    }
  }

  public String getToURL() {
    return myToURLText.getText();
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());

    GridBagConstraints gc = createGridBagConstraints();

    JLabel fromLabel = new JLabel(SvnBundle.message("label.copy.from"));
    panel.add(fromLabel, gc);

    gc.gridx += 1;
    gc.weightx = 1;
    gc.gridwidth = 2;
    gc.fill = GridBagConstraints.HORIZONTAL;

    myFromURLText = new JTextField();
    myFromURLText.setEditable(false);

    panel.add(myFromURLText, gc);
    fromLabel.setLabelFor(myFromURLText);

    JLabel toLabel = new JLabel(SvnBundle.message("label.copy.to"));
    gc.gridy += 1;
    gc.gridx = 0;
    gc.gridwidth = 1;
    gc.weightx = 0;
    gc.fill = GridBagConstraints.NONE;

    panel.add(toLabel, gc);

    gc.gridx += 1;
    gc.gridwidth = 2;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;

    myToURLText = new TextFieldWithBrowseButton(this);
    myToURLText.setEditable(false);
    panel.add(myToURLText, gc);

    toLabel.setLabelFor(myToURLText);

    gc.gridy += 1;
    gc.gridx = 0;
    gc.gridwidth = 2;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.NONE;

    panel.add(new JLabel(SvnBundle.message("label.copy.from.revision")), gc);

    myWorkingRevisionButton = new JRadioButton(SvnBundle.message("radio.copy.working.copy"));
    myHEADRevisionButton = new JRadioButton(SvnBundle.message("radio.copy.head"));
    mySpecificRevisionButton = new JRadioButton(SvnBundle.message("radio.copy.specific"));

    myWorkingRevisionButton.addActionListener(this);
    myHEADRevisionButton.addActionListener(this);
    mySpecificRevisionButton.addActionListener(this);

    ButtonGroup group = new ButtonGroup();
    group.add(myWorkingRevisionButton);
    group.add(myHEADRevisionButton);
    group.add(mySpecificRevisionButton);
    myWorkingRevisionButton.setSelected(true);

    gc.gridy += 1;
    panel.add(myWorkingRevisionButton, gc);
    gc.gridy += 1;
    panel.add(myHEADRevisionButton, gc);
    gc.gridy += 1;
    gc.gridwidth = 2;
    panel.add(mySpecificRevisionButton, gc);

    gc.gridx = 2;
    gc.weightx = 0;
    gc.anchor = GridBagConstraints.WEST;
    gc.fill = GridBagConstraints.NONE;

    myRevisionText = new JTextField(8);
    myRevisionText.setMinimumSize(myRevisionText.getPreferredSize());
    panel.add(myRevisionText, gc);

    myRevisionText.getDocument().addDocumentListener(new DocumentListener() {
      public void insertUpdate(DocumentEvent e) {
        getOKAction().setEnabled(isOKActionEnabled());
      }

      public void removeUpdate(DocumentEvent e) {
        getOKAction().setEnabled(isOKActionEnabled());
      }

      public void changedUpdate(DocumentEvent e) {
        getOKAction().setEnabled(isOKActionEnabled());
      }
    });

    gc.gridy += 1;
    gc.gridx = 0;
    gc.gridwidth = 3;
    JLabel commentLabel = new JLabel(SvnBundle.message("label.copy.comment"));
    panel.add(commentLabel, gc);

    gc.gridy += 1;
    gc.weightx = 1;
    gc.weighty = 1;
    gc.fill = GridBagConstraints.BOTH;

    myCommentText = new JTextArea(7, 25);
    panel.add(new JScrollPane(myCommentText), gc);

    commentLabel.setLabelFor(myCommentText);

    return panel;
  }

  public void actionPerformed(ActionEvent e) {
    myRevisionText.setEnabled(mySpecificRevisionButton.isSelected());
    if (mySpecificRevisionButton.isSelected()) {
      myRevisionText.selectAll();
      myRevisionText.requestFocus();
    }
    if (e != null &&
        (e.getSource() == mySpecificRevisionButton ||
         e.getSource() == myWorkingRevisionButton ||
         e.getSource() == myHEADRevisionButton)) {

      getOKAction().setEnabled(isOKActionEnabled());
      return;
    }
    // select repository
    String url = myToURLText.getText();
    String dstName = SVNPathUtil.tail(myURL);
    dstName = SVNEncodingUtil.uriDecode(dstName);
    SelectLocationDialog dialog = new SelectLocationDialog(myProject, SVNPathUtil.removeTail(url), SvnBundle.message("label.copy.select.location.dialog.copy.as"), dstName, false);
    dialog.show();
    if (dialog.isOK()) {
      url = dialog.getSelectedURL();
      String name = dialog.getDestinationName();
      url = SVNPathUtil.append(url, name);
      myToURLText.setText(url);
    }
    getOKAction().setEnabled(isOKActionEnabled());
  }

  public JComponent getPreferredFocusedComponent() {
    return myToURLText;
  }

  public boolean shouldCloseOnCross() {
    return true;
  }

  protected String getDimensionServiceKey() {
    return "svn.copyDialog";
  }

  public boolean isOKActionEnabled() {
    if (myURL == null) {
      return false;
    }
    String url = myToURLText.getText();
    if (url != null && url.trim().length() > 0) {
      if (mySpecificRevisionButton.isSelected()) {
        String revStr = myRevisionText.getText();
        SVNRevision revision = SVNRevision.parse(revStr);
        if (revision != null && revision.isValid() && !revision.isLocal()) {
          return true;
        }
      }
      return true;
    }
    return false;
  }

  private static GridBagConstraints createGridBagConstraints() {
    GridBagConstraints gc = new GridBagConstraints();
    gc.insets = new Insets(2, 2, 2, 2);
    gc.gridwidth = 1;
    gc.gridheight = 1;
    gc.gridx = 0;
    gc.gridy = 0;
    gc.anchor = GridBagConstraints.WEST;
    gc.fill = GridBagConstraints.NONE;
    gc.weightx = 0;
    gc.weighty = 0;
    return gc;
  }
}
