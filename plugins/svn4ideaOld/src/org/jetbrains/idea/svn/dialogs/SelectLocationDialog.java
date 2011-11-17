/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.browser.UrlOpeningExpander;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.SVNRepository;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;

/**
 * @author alex
 */
public class SelectLocationDialog extends DialogWrapper {
  private final Project myProject;
  private RepositoryBrowserComponent myRepositoryBrowser;
  private final SVNURL myURL;
  private final String myDstName;
  private final String myDstLabel;
  private JTextField myDstText;
  private final boolean myIsShowFiles;

  @NonNls private static final String HELP_ID = "vcs.subversion.common";

  // todo check that works when authenticated
  @Nullable
  public static String selectLocation(Project project, String url) {
    try {
      SVNURL.parseURIEncoded(url);
      final SVNURL svnurl = initRoot(project, url);
      SelectLocationDialog dialog = new SelectLocationDialog(project, svnurl, null, null, true);
      dialog.show();
      if (!dialog.isOK()) return null;
      return dialog.getSelectedURL();
    } catch (SVNException e) {
      Messages.showErrorDialog(project, e.getMessage(), SvnBundle.message("dialog.title.select.repository.location"));
      return null;
    }
  }

  @Nullable
  public static String selectCopyDestination(Project project, String url, String dstLabel, String dstName, boolean showFiles) {
    try {
      SVNURL.parseURIEncoded(url);
      final SVNURL svnurl = initRoot(project, url);
      SelectLocationDialog dialog = new SelectLocationDialog(project, svnurl, dstLabel, dstName, showFiles);
      dialog.show();
      if (!dialog.isOK()) return null;

      final String result = dialog.getSelectedURL();
      final String name = dialog.getDestinationName();
      return SVNPathUtil.append(result, name);
    } catch (SVNException e) {
      Messages.showErrorDialog(project, SvnBundle.message("select.location.invalid.url.message", url),
                               SvnBundle.message("dialog.title.select.repository.location"));
      return null;
    }
  }

  private SelectLocationDialog(Project project, SVNURL url,
                              String dstLabel, String dstName, boolean showFiles) throws SVNException {
    super(project, true);
    myProject = project;
    myDstLabel = dstLabel;
    myDstName = dstName;
    myURL = url;
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

  private static SVNURL initRoot(final Project project, final String urlString) throws SVNException {
    final Ref<SVNURL> result = new Ref<SVNURL>();
    final Ref<SVNException> excRef = new Ref<SVNException>();

    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        SVNRepository repos = null;
        try {
          repos = SvnVcs.getInstance(project).createRepository(urlString);
          result.set(repos.getRepositoryRoot(true));
        } catch (SVNException e) {
          excRef.set(e);
        } finally {
          if (repos != null) {
            repos.closeSession();
          }
        }
      }
    }, "Detecting repository root", true, project);
    if (! excRef.isNull()) {
      throw excRef.get();
    }
    return result.get();
  }

  protected void init() {
    super.init();
    final String urlString = myURL.toString();
    myRepositoryBrowser.setRepositoryURL(myURL, myIsShowFiles, new UrlOpeningExpander.Factory(urlString, urlString));
    myRepositoryBrowser.addChangeListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        getOKAction().setEnabled(isOKActionEnabled());
      }
    });
  }

  @Override
  protected void dispose() {
    super.dispose();
    Disposer.dispose(myRepositoryBrowser);
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

  public SVNURL getSelectedSVNURL() {
    return myRepositoryBrowser.getSelectedSVNURL();
  }
}
