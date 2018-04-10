// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.dialogs.browser.UrlOpeningExpander;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

import static com.intellij.openapi.util.Pair.create;
import static com.intellij.util.ObjectUtils.notNull;
import static org.jetbrains.idea.svn.SvnUtil.append;

/**
 * @author alex
 */
public class SelectLocationDialog extends DialogWrapper {
  private final Project myProject;
  private RepositoryBrowserComponent myRepositoryBrowser;
  private final Url myURL;
  private final String myDstName;
  private final String myDstLabel;
  private JTextField myDstText;
  private final boolean myIsShowFiles;
  private final boolean myAllowActions;

  @NonNls private static final String HELP_ID = "vcs.subversion.common";

  // todo check that works when authenticated
  @Nullable
  public static Url selectLocation(Project project, @NotNull Url url) {
    SelectLocationDialog dialog = openDialog(project, url, null, null, true, false, null);

    return dialog == null || !dialog.isOK() ? null : dialog.getSelectedURL();
  }

  @Nullable
  public static Pair<Url, Url> selectLocationAndRoot(Project project, @NotNull Url url) {
    SelectLocationDialog dialog = new SelectLocationDialog(project, url, null, null, true, true);
    return dialog.showAndGet() ? create(dialog.getSelectedURL(), dialog.getRootUrl()) : null;
  }

  @Nullable
  public static Url selectCopyDestination(Project project, @NotNull Url url, String dstLabel, String dstName, boolean showFiles)
    throws SvnBindException {
    SelectLocationDialog dialog =
      openDialog(project, url, dstLabel, dstName, showFiles, false, SvnBundle.message("select.location.invalid.url.message", url));

    return dialog == null || !dialog.isOK() ? null : append(notNull(dialog.getSelectedURL()), dialog.getDestinationName());
  }

  @Nullable
  private static SelectLocationDialog openDialog(Project project,
                                                 @NotNull Url url,
                                                 String dstLabel,
                                                 String dstName,
                                                 boolean showFiles,
                                                 boolean allowActions,
                                                 String errorMessage) {
    try {
      final Url repositoryUrl = initRoot(project, url);
      if (repositoryUrl == null) {
        Messages.showErrorDialog(project, "Can not detect repository root for URL: " + url,
                                 SvnBundle.message("dialog.title.select.repository.location"));
        return null;
      }
      SelectLocationDialog dialog = new SelectLocationDialog(project, repositoryUrl, dstLabel, dstName, showFiles, allowActions);
      dialog.show();
      return dialog;
    }
    catch (SvnBindException e) {
      Messages.showErrorDialog(project, errorMessage != null ? errorMessage : e.getMessage(),
                               SvnBundle.message("dialog.title.select.repository.location"));
      return null;
    }
  }

  private SelectLocationDialog(Project project, Url url, String dstLabel, String dstName, boolean showFiles, boolean allowActions) {
    super(project, true);
    myProject = project;
    myDstLabel = dstLabel;
    myDstName = dstName;
    myURL = url;
    myIsShowFiles = showFiles;
    myAllowActions = allowActions;
    setTitle(SvnBundle.message("dialog.title.select.repository.location"));
    getHelpAction().setEnabled(true);
    init();
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HELP_ID);
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected String getDimensionServiceKey() {
    return "svn.repositoryBrowser";
  }

  @Nullable
  private static Url initRoot(final Project project, final Url url) throws SvnBindException {
    final Ref<Url> result = new Ref<>();
    final Ref<SvnBindException> excRef = new Ref<>();

    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      try {
        result.set(SvnUtil.getRepositoryRoot(SvnVcs.getInstance(project), url));
      }
      catch (SvnBindException e) {
        excRef.set(e);
      }
    }, "Detecting repository root", true, project);
    if (!excRef.isNull()) {
      throw excRef.get();
    }
    return result.get();
  }

  protected void init() {
    super.init();
    if (myAllowActions) {
      // initialize repo browser this way - to make actions work correctly
      myRepositoryBrowser.setRepositoryURLs(new Url[]{myURL}, myIsShowFiles, new UrlOpeningExpander.Factory(myURL), true);
    }
    else {
      myRepositoryBrowser.setRepositoryURL(myURL, myIsShowFiles, new UrlOpeningExpander.Factory(myURL));
    }
    myRepositoryBrowser.addChangeListener(e -> getOKAction().setEnabled(isOKActionEnabled()));
  }

  @Override
  protected void dispose() {
    super.dispose();
    Disposer.dispose(myRepositoryBrowser);
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());

    JPanel browserPanel = new JPanel();
    browserPanel.setLayout(new GridBagLayout());

    GridBagConstraints gc = new GridBagConstraints();
    gc.insets = JBUI.insets(2);
    gc.gridwidth = 2;
    gc.gridheight = 1;
    gc.gridx = 0;
    gc.gridy = 0;
    gc.anchor = GridBagConstraints.WEST;
    gc.fill = GridBagConstraints.BOTH;
    gc.weightx = 1;
    gc.weighty = 1;


    myRepositoryBrowser = new RepositoryBrowserComponent(SvnVcs.getInstance(myProject));
    browserPanel.add(myRepositoryBrowser, gc);
    if (myDstName != null) {
      gc.gridy += 1;
      gc.gridwidth = 1;
      gc.gridx = 0;
      gc.fill = GridBagConstraints.NONE;
      gc.weightx = 0;
      gc.weighty = 0;

      JLabel dstLabel = new JLabel(myDstLabel);
      browserPanel.add(dstLabel, gc);

      gc.gridx += 1;
      gc.weightx = 1;
      gc.fill = GridBagConstraints.HORIZONTAL;

      myDstText = new JTextField();
      myDstText.setText(myDstName);
      myDstText.selectAll();
      browserPanel.add(myDstText, gc);

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

      browserPanel.add(new JSeparator(), gc);
    }

    if (myAllowActions) {
      panel.add(createToolbar(), BorderLayout.NORTH);
    }
    panel.add(browserPanel, BorderLayout.CENTER);

    return panel;
  }

  @NotNull
  private JComponent createToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new RepositoryBrowserDialog.EditLocationAction(myRepositoryBrowser));

    return ActionManager.getInstance().createActionToolbar(RepositoryBrowserDialog.PLACE_TOOLBAR, group, true).getComponent();
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

  @NotNull
  public String getDestinationName() {
    return myDstText.getText().trim();
  }

  @Nullable
  public Url getSelectedURL() {
    return myRepositoryBrowser.getSelectedSVNURL();
  }

  @Nullable
  public Url getRootUrl() {
    RepositoryTreeNode node = myRepositoryBrowser.getSelectedNode();

    // find the most top parent of type RepositoryTreeNode
    while (node != null && node.getParent() instanceof RepositoryTreeNode) {
      node = (RepositoryTreeNode)node.getParent();
    }

    return node != null ? node.getURL() : null;
  }
}
