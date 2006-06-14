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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.help.HelpManager;
import com.intellij.util.ui.DialogUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.annotations.NonNls;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 25.06.2005
 * Time: 17:12:47
 * To change this template use File | Settings | File Templates.
 */
public class SelectLocationDialog extends DialogWrapper {
  private Project myProject;
  private RepositoryBrowserComponent myRepositoryBrowser;
  private SVNURL myURL;
  private String myDstName;
  private String myDstLabel;
  private JTextField myDstText;
  private boolean myIsShowFiles;

  @NonNls private static final String HELP_ID = "vcs.subversion.common";

  public SelectLocationDialog(Project project, String url) {
    this(project, url, null, null, true);
  }

  public SelectLocationDialog(Project project, String url,
                              String dstLabel, String dstName, boolean showFiles) {
    super(project, true);
    myProject = project;
    myDstLabel = dstLabel;
    myDstName = dstName;
    try {
      myURL = SVNURL.parseURIEncoded(url);
    } catch (SVNException e) {
      //
    }
    myIsShowFiles = showFiles;
    setTitle(SvnBundle.message("dialog.title.select.repository.location"));
    getHelpAction().setEnabled(true);
    init();
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HELP_ID);
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected String getDimensionServiceKey() {
    return "svn.repositoryBrowser";
  }

  protected void init() {
    super.init();
    try {
      SVNRepository repos = SvnVcs.getInstance(myProject).createRepository(myURL.toString());
      myURL = repos.getRepositoryRoot(true);
    } catch (SVNException e) {
      // show error dialog.
    }
    myRepositoryBrowser.setRepositoryURL(myURL, myIsShowFiles);
    myRepositoryBrowser.addChangeListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        getOKAction().setEnabled(isOKActionEnabled());
      }
    });
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());

    GridBagConstraints gc = new GridBagConstraints();
    gc.insets = new Insets(2, 2, 2, 2);
    gc.gridwidth = 2;
    gc.gridheight = 1;
    gc.gridx = 0;
    gc.gridy = 0;
    gc.anchor = GridBagConstraints.WEST;
    gc.fill = GridBagConstraints.BOTH;
    gc.weightx = 1;
    gc.weighty = 1;


    myRepositoryBrowser = new RepositoryBrowserComponent(SvnVcs.getInstance(myProject));
    panel.add(myRepositoryBrowser, gc);
    if (myDstName != null) {
      gc.gridy += 1;
      gc.gridwidth = 1;
      gc.gridx = 0;
      gc.fill = GridBagConstraints.NONE;
      gc.weightx = 0;
      gc.weighty = 0;

      JLabel dstLabel = new JLabel(myDstLabel);
      panel.add(dstLabel, gc);

      gc.gridx += 1;
      gc.weightx = 1;
      gc.fill = GridBagConstraints.HORIZONTAL;

      myDstText = new JTextField();
      myDstText.setText(myDstName);
      myDstText.selectAll();
      panel.add(myDstText, gc);

      myDstText.getDocument().addDocumentListener(new DocumentListener() {
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

      dstLabel.setLabelFor(myDstText);
      gc.gridx = 0;
      gc.gridy += 1;
      gc.gridwidth = 2;

      panel.add(new JSeparator(), gc);
    }

    return panel;
  }

  public JComponent getPreferredFocusedComponent() {
    return (JComponent)myRepositoryBrowser.getPreferredFocusedComponent();
  }

  public boolean shouldCloseOnCross() {
    return true;
  }

  public boolean isOKActionEnabled() {
    boolean ok = myRepositoryBrowser.getSelectedURL() != null;
    if (ok && myDstText != null) {
      return myDstText.getText().trim().length() > 0;
    }
    return ok;
  }

  public String getDestinationName() {
    return SVNEncodingUtil.uriEncode(myDstText.getText().trim());
  }

  public String getSelectedURL() {
    return myRepositoryBrowser.getSelectedURL();
  }
}
