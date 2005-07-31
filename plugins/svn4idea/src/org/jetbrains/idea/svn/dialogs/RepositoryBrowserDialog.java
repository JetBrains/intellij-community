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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.help.HelpManager;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 25.06.2005
 * Time: 17:12:47
 * To change this template use File | Settings | File Templates.
 */
public class RepositoryBrowserDialog extends DialogWrapper implements ActionListener {
  private Project myProject;
  private RepositoryBrowserComponent myRepositoryBrowser;
  private boolean myIsDialogClosed;
  private String myCopiedURL;

  private JComboBox myRepositoryBox;
  private JButton myMkDirButton;
  private JButton myDeleteButton;
  private JButton myCopyButton;
  private JButton myPasteButton;
  private SVNDirEntry myCopiedEntry;

  private static final String HELP_ID = "vcs.subversion.browseSVN";

  public RepositoryBrowserDialog(Project project) {
    super(project, true);
    myProject = project;
    setTitle("Browse SVN Repository");
    setResizable(true);
    setOKButtonText("Close");
    getHelpAction().setEnabled(true);
    init();
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HELP_ID);
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getHelpAction()};
  }

  protected String getDimensionServiceKey() {
    return "svn.repositoryBrowser";
  }

  protected void doOKAction() {
    myIsDialogClosed = true;
    saveLastURL();
    super.doOKAction();
  }

  private void saveLastURL() {
    if (myRepositoryBrowser.getRootURL() == null) {
      return;
    }
    if (myProject != null) {
      SvnVcs vcs = SvnVcs.getInstance(myProject);
      if (vcs != null) {
        String url = myRepositoryBrowser.getRootURL();
        vcs.getSvnConfiguration().setLastSelectedCheckoutURL(url);
      }
    }
  }

  protected void init() {
    super.init();

    SvnConfiguration config = SvnConfiguration.getInstance(myProject);
    Collection urls = config == null ? Collections.EMPTY_LIST : config.getCheckoutURLs();

    String lastURL = null;
    for (Iterator us = urls.iterator(); us.hasNext();) {
      String url = (String)us.next();
      myRepositoryBox.addItem(url);
      lastURL = url;
    }

    if (config != null && config.getLastSelectedCheckoutURL() != null) {
      lastURL = config.getLastSelectedCheckoutURL();
    }
    if (lastURL != null) {
      myRepositoryBox.getEditor().setItem(lastURL);
      myRepositoryBox.setSelectedItem(lastURL);
    }
    else {
      lastURL = (String)myRepositoryBox.getSelectedItem();
    }
    myRepositoryBox.getEditor().selectAll();
    if (urls.isEmpty()) {
      myRepositoryBrowser.setRepositoryURL(null, true);
    }
    else {
      myRepositoryBrowser.setRepositoryURL(lastURL, true);
      if (myRepositoryBrowser.getRootURL() != null) {
        myRepositoryBrowser.getPreferredFocusedComponent().requestFocus();
      }
    }
    myCopiedURL = null;
    myCopiedEntry = null;

    final JTextField editor = (JTextField)myRepositoryBox.getEditor().getEditorComponent();
    editor.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          myCopiedURL = null;
          myCopiedEntry = null;
          e.consume();
          String url = editor.getText();
          myRepositoryBrowser.setRepositoryURL(url, true);
          if (myRepositoryBrowser.getRootURL() != null) {
            myRepositoryBrowser.getPreferredFocusedComponent().requestFocus();
          }
        }
      }
    });

    myRepositoryBox.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED && !myIsDialogClosed) {
          myCopiedURL = null;
          myCopiedEntry = null;
          String url = (String)myRepositoryBox.getEditor().getItem();
          myRepositoryBrowser.setRepositoryURL(url, true);
          if (myRepositoryBrowser.getRootURL() != null) {
            SvnConfiguration.getInstance(myProject).addCheckoutURL(myRepositoryBrowser.getRootURL());
            myRepositoryBrowser.getPreferredFocusedComponent().requestFocus();
          }
        }
      }
    });

    myRepositoryBrowser.addChangeListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        updateState();
      }
    });
    updateState();
  }

  private void updateState() {
    SVNDirEntry entry = myRepositoryBrowser.getSelectedEntry();

    myMkDirButton.setEnabled(entry != null && entry.getKind() == SVNNodeKind.DIR);
    myDeleteButton.setEnabled(entry != null && !"/".equals(entry.getPath()));
    myPasteButton.setEnabled(entry != null && myCopiedURL != null && entry.getKind() == SVNNodeKind.DIR);
    myCopyButton.setEnabled(entry != null);
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());

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

    JLabel label = new JLabel("Repository &URL:");
    panel.add(label, gc);
    gc.gridx += 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 1;

    myRepositoryBox = new JComboBox();
    myRepositoryBox.setEditable(true);
    panel.add(myRepositoryBox, gc);
    DialogUtil.registerMnemonic(label, myRepositoryBox);

    gc.gridx += 1;
    gc.fill = GridBagConstraints.NONE;
    gc.anchor = GridBagConstraints.EAST;
    gc.weightx = 0;

    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new RefreshAction());
    ActionToolbar tb = ActionManager.getInstance().createActionToolbar("", group, false);
    panel.add(tb.getComponent(), gc);

    gc.gridy += 1;
    gc.gridx = 0;
    gc.weightx = 1;
    gc.weighty = 1;
    gc.fill = GridBagConstraints.BOTH;
    gc.anchor = GridBagConstraints.WEST;
    gc.gridwidth = 3;
    gc.insets = new Insets(0, 0, 0, 0);

    panel.add(createRepositoryBrowserComponent(), gc);

    gc.gridy += 1;
    gc.weighty = 0;
    gc.fill = GridBagConstraints.HORIZONTAL;

    panel.add(new JSeparator(), gc);

    return panel;
  }

  private JComponent createRepositoryBrowserComponent() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gc = new GridBagConstraints();
    gc.insets = new Insets(2, 2, 2, 2);
    gc.gridwidth = 1;
    gc.gridheight = 5;
    gc.gridx = 0;
    gc.gridy = 0;
    gc.anchor = GridBagConstraints.WEST;
    gc.fill = GridBagConstraints.BOTH;
    gc.weightx = 1;
    gc.weighty = 1;

    myRepositoryBrowser = new RepositoryBrowserComponent(SvnVcs.getInstance(myProject));

    panel.add(myRepositoryBrowser, gc);

    myMkDirButton = new JButton("&New Folder...");
    myDeleteButton = new JButton("&Delete...");
    myCopyButton = new JButton("&Copy");
    myPasteButton = new JButton("&Paste...");

    gc.gridx += 1;
    gc.gridheight = 1;
    gc.weightx = 0;
    gc.weighty = 0;
    gc.anchor = GridBagConstraints.NORTHWEST;
    gc.fill = GridBagConstraints.HORIZONTAL;

    panel.add(new JLabel(" "), gc);
    gc.gridy += 1;
    panel.add(myMkDirButton, gc);
    gc.gridy += 1;
    panel.add(myCopyButton, gc);
    gc.gridy += 1;
    panel.add(myPasteButton, gc);
    gc.gridy += 1;
    panel.add(myDeleteButton, gc);

    DialogUtil.registerMnemonic(myMkDirButton);
    DialogUtil.registerMnemonic(myDeleteButton);
    DialogUtil.registerMnemonic(myCopyButton);
    DialogUtil.registerMnemonic(myPasteButton);

    myMkDirButton.addActionListener(this);
    myDeleteButton.addActionListener(this);
    myCopyButton.addActionListener(this);
    myPasteButton.addActionListener(this);

    return panel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myRepositoryBox;
  }

  public boolean shouldCloseOnCross() {
    return true;
  }

  public boolean isOKActionEnabled() {
    return true;
  }

  public String getSelectedURL() {
    return myRepositoryBrowser.getSelectedURL();
  }

  public void actionPerformed(ActionEvent e) {
    String url = myRepositoryBrowser.getSelectedURL();
    SVNDirEntry entry = myRepositoryBrowser.getSelectedEntry();
    if (e.getSource() == myDeleteButton) {
      RepositoryBrowserCommitDialog dialog = new RepositoryBrowserCommitDialog(myProject,
                                                                               RepositoryBrowserCommitDialog.DELETE, url, null, false);
      dialog.show();
      if (dialog.isOK()) {
        doDelete(url, dialog.getComment());
        myRepositoryBrowser.refresh(entry, true);
        myCopiedEntry = null;
        myCopiedURL = null;
      }
    }
    else if (e.getSource() == myCopyButton) {
      myCopiedURL = getSelectedURL();
      myCopiedEntry = myRepositoryBrowser.getSelectedEntry();
      updateState();
    }
    else if (e.getSource() == myPasteButton) {
      String url1 = myCopiedURL;
      url = SVNPathUtil.append(url, "CopyOf" + SVNPathUtil.tail(url1));
      RepositoryBrowserCommitDialog dialog = new RepositoryBrowserCommitDialog(myProject,
                                                                               RepositoryBrowserCommitDialog.COPY, url1, url,
                                                                               !"/".equals(entry.getPath()));
      dialog.show();
      if (dialog.isOK()) {
        url = dialog.getURL2();
        boolean move = dialog.isMove();
        doCopy(url1, url, move, dialog.getComment());
        myRepositoryBrowser.refresh(entry, false);
        if (move) {
          // refresh src.
          myRepositoryBrowser.refresh(myCopiedEntry, true);
          myCopiedEntry = null;
          myCopiedURL = null;
        }
      }
    }
    else if (e.getSource() == myMkDirButton) {
      url = SVNPathUtil.append(url, "NewFolder");
      RepositoryBrowserCommitDialog dialog = new RepositoryBrowserCommitDialog(myProject,
                                                                               RepositoryBrowserCommitDialog.MKDIR, url, null, false);
      dialog.show();
      url = dialog.getURL1();
      if (dialog.isOK()) {
        doMkdir(url, dialog.getComment());
        myRepositoryBrowser.refresh(entry, false);
      }
    }
  }

  private void doDelete(final String url, final String comment) {
    final SVNException[] exception = new SVNException[1];
    Runnable command = new Runnable() {
      public void run() {
        ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
        if (progress != null) {
          progress.setText("Deleting " + url);
        }
        SvnVcs vcs = SvnVcs.getInstance(myProject);
        try {
          SVNCommitClient committer = vcs.createCommitClient();
          committer.doDelete(new String[]{url}, comment);
        }
        catch (SVNException e) {
          exception[0] = e;
        }
      }
    };
    ApplicationManager.getApplication().runProcessWithProgressSynchronously(command,
                                                                            "Delete", false, myProject);
    if (exception[0] != null) {
      Messages.showErrorDialog(exception[0].getMessage(), "Error");
    }
  }

  private void doMkdir(final String url, final String comment) {
    final SVNException[] exception = new SVNException[1];
    Runnable command = new Runnable() {
      public void run() {
        ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
        if (progress != null) {
          progress.setText("Creating " + url);
        }
        SvnVcs vcs = SvnVcs.getInstance(myProject);
        try {
          SVNCommitClient committer = vcs.createCommitClient();
          committer.doMkDir(new String[]{url}, comment);
        }
        catch (SVNException e) {
          exception[0] = e;
        }
      }
    };
    ApplicationManager.getApplication().runProcessWithProgressSynchronously(command,
                                                                            "Create Remote Folder", false, myProject);
    if (exception[0] != null) {
      Messages.showErrorDialog(exception[0].getMessage(), "Error");
    }
  }

  private void doCopy(final String src, final String dst, final boolean move, final String comment) {
    final SVNException[] exception = new SVNException[1];
    Runnable command = new Runnable() {
      public void run() {
        ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
        if (progress != null) {
          progress.setText((move ? "Moving " : "Copying ") + src);
          progress.setText2("To " + dst);
        }
        SvnVcs vcs = SvnVcs.getInstance(myProject);
        try {
          SVNCopyClient committer = vcs.createCopyClient();
          committer.doCopy(src, SVNRevision.UNDEFINED, SVNRevision.HEAD, dst, SVNRevision.UNDEFINED, move, comment);
        }
        catch (SVNException e) {
          exception[0] = e;
        }
      }
    };
    ApplicationManager.getApplication().runProcessWithProgressSynchronously(command,
                                                                            move ? "Move" : "Copy", false, myProject);
    if (exception[0] != null) {
      Messages.showErrorDialog(exception[0].getMessage(), "Error");
    }
  }

  private class RefreshAction extends AnAction {
    public void update(AnActionEvent anActionEvent) {
      anActionEvent.getPresentation().setText("Refresh");
      anActionEvent.getPresentation().setIcon(IconLoader.findIcon("/actions/sync.png"));
      String url = (String)myRepositoryBox.getEditor().getItem();
      try {
        SVNURL.parseURIEncoded(url);
      }
      catch (SVNException e) {
        url = null;
      }
      anActionEvent.getPresentation().setEnabled(url != null);
    }

    public void actionPerformed(AnActionEvent anActionEvent) {
      myCopiedURL = null;
      String url = (String)myRepositoryBox.getEditor().getItem();
      myRepositoryBrowser.setRepositoryURL(url, true);
      if (myRepositoryBrowser.getRootURL() != null) {
        myRepositoryBrowser.getPreferredFocusedComponent().requestFocus();
      }
    }
  }

}
