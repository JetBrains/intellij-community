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

import com.intellij.CommonBundle;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.ui.ChangeListViewerDialog;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.NotNullFunction;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.actions.BrowseRepositoryAction;
import org.jetbrains.idea.svn.checkout.SvnCheckoutProvider;
import org.jetbrains.idea.svn.dialogs.browser.*;
import org.jetbrains.idea.svn.dialogs.browserCache.Expander;
import org.jetbrains.idea.svn.dialogs.browserCache.KeepingExpandedExpander;
import org.jetbrains.idea.svn.dialogs.browserCache.SyntheticWorker;
import org.jetbrains.idea.svn.history.SvnHistoryProvider;
import org.jetbrains.idea.svn.history.SvnRepositoryLocation;
import org.jetbrains.idea.svn.status.SvnDiffEditor;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableEditor;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public class RepositoryBrowserDialog extends DialogWrapper {

  private final Project myProject;
  private final SvnVcs myVCS;
  private RepositoryBrowserComponent myRepositoryBrowser;

  @NonNls public static final String COPY_OF_PREFIX = "CopyOf";
  @NonNls public static final String NEW_FOLDER_POSTFIX = "NewFolder";

  private final DeleteAction myDeleteAction = new DeleteAction();
  private AnAction copyUrlAction;
  private AnAction mkDirAction;

  private final boolean myShowFiles;

  @NonNls private static final String PLACE_TOOLBAR = "RepositoryBrowser.Toolbar";
  @NonNls private static final String PLACE_MENU = "RepositoryBrowser.Menu";
  private final String myRepositoriesLabelText;
  protected JLabel myRepositoriesLabel;

  public RepositoryBrowserDialog(Project project) {
    this(project, true, null);
  }

  public RepositoryBrowserDialog(Project project, final boolean showFiles, @Nullable final String repositoriesLabelText) {
    super(project, true);
    myRepositoriesLabelText = repositoriesLabelText == null ? "Repositories:" : repositoriesLabelText;
    myShowFiles = showFiles;
    myProject = project;
    myVCS = SvnVcs.getInstance(project);
    setTitle("SVN Repository Browser");
    setResizable(true);
    setOKButtonText(CommonBundle.getCloseButtonText());
    getHelpAction().setEnabled(true);
    init();
  }

  protected String getHelpId() {
    return "reference.svn.repository";
  }

  protected Action[] createActions() {
    return new Action[] {getOKAction(), getHelpAction()};
  }

  protected String getDimensionServiceKey() {
    return "svn.repositoryBrowser";
  }

  protected boolean showImportAction() {
    return true;
  }

  public JComponent createToolbar(final boolean horizontal, final AnAction... additionalActions) {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new AddLocationAction());
    group.add(new EditLocationAction());
    group.add(new DiscardLocationAction());
    group.add(new DetailsAction());
    group.addSeparator();
    final RefreshAction refreshAction = new RefreshAction();
    refreshAction.registerCustomShortcutSet(CommonShortcuts.getRerun(), getRepositoryBrowser());
    group.add(refreshAction);

    myDeleteAction.registerCustomShortcutSet(CommonShortcuts.DELETE, getRepositoryBrowser());
    copyUrlAction = new CopyUrlAction();
    copyUrlAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT,
                                                              KeyEvent.CTRL_MASK|KeyEvent.CTRL_DOWN_MASK|
                                                              KeyEvent.ALT_MASK|KeyEvent.ALT_DOWN_MASK)), getRepositoryBrowser());
    mkDirAction = new MkDirAction();
    mkDirAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT,
                                                              KeyEvent.ALT_MASK|KeyEvent.ALT_DOWN_MASK)), getRepositoryBrowser());

    AnAction action = CommonActionsManager.getInstance().createCollapseAllAction(new TreeExpander() {
      public void expandAll() {
      }

      public boolean canExpand() {
        return false;
      }

      public void collapseAll() {
        JTree tree = getRepositoryBrowser().getRepositoryTree();
        int row = tree.getRowCount() - 1;
        while (row >= 0) {
          tree.collapseRow(row);
          row--;
        }
      }

      public boolean canCollapse() {
        return true;
      }
    }, getRepositoryBrowser());
    group.add(action);

    if ((additionalActions != null) || (! horizontal)) {
      group.addSeparator();
    }
    if (additionalActions != null) {
      for (AnAction anAction : additionalActions) {
        group.add(anAction);
      }
    }
    if (! horizontal) {
      group.add(new CloseToolWindowAction());
    }
    return ActionManager.getInstance().createActionToolbar(PLACE_TOOLBAR, group, horizontal).getComponent();
  }

  protected JPopupMenu createPopup(boolean toolWindow) {
    DefaultActionGroup group = new DefaultActionGroup();
    DefaultActionGroup newGroup = new DefaultActionGroup("_New", true);
    newGroup.add(new AddLocationAction());
    newGroup.add(new MkDirAction());
    group.add(newGroup);
    group.addSeparator();
    if (toolWindow) {
      group.add(new OpenAction());
      group.add(new HistoryAction());
    }
    group.add(new CheckoutAction());
    group.add(new DiffAction());
    group.add(new BrowseChangesAction());
    group.addSeparator();
    group.add(new ImportAction());
    group.add(new ExportAction());
    group.addSeparator();
    group.add(new CopyOrMoveAction("Branch or Tag...", "copy.dialog.title", false));
    group.add(new CopyOrMoveAction("_Move or Rename...", "move.dialog.title", true));
    group.add(myDeleteAction);
    group.add(copyUrlAction);
    group.addSeparator();
    group.add(new RefreshAction());
    group.add(new EditLocationAction());
    group.add(new DiscardLocationAction());
    ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu(PLACE_MENU, group);
    return menu.getComponent();
  }

  public JComponent createCenterPanel() {
    JPanel parentPanel = new JPanel(new BorderLayout());
    JPanel top = new JPanel(new BorderLayout());

    myRepositoriesLabel = new JLabel(myRepositoriesLabelText);
    top.add(myRepositoriesLabel, BorderLayout.WEST);
    top.add(createToolbar(true, null), BorderLayout.EAST);
    parentPanel.add(top, BorderLayout.NORTH);

    JComponent panel =  createBrowserComponent(false);
    parentPanel.add(panel, BorderLayout.CENTER);

    return parentPanel;
  }

  public JComponent createBrowserComponent(final boolean toolWindow) {
    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());

    GridBagConstraints gc = new GridBagConstraints();
    gc.gridx = 0;
    gc.gridwidth = 1;
    gc.gridy = 0;
    gc.gridheight = 1;

    gc.gridx = 0;
    gc.gridwidth = 2;
    gc.gridy += 1;
    gc.gridheight = 1;
    gc.weightx = 1;
    gc.weighty = 1;
    gc.fill = GridBagConstraints.BOTH;
    gc.anchor = GridBagConstraints.WEST;

    panel.add(getRepositoryBrowser(), gc);

    gc.gridy += 1;
    gc.weighty = 0;
    gc.fill = GridBagConstraints.HORIZONTAL;

    panel.add(new JLabel(), gc);

    Collection<String> urls = SvnApplicationSettings.getInstance().getCheckoutURLs();
    ArrayList<SVNURL> svnURLs = new ArrayList<SVNURL>();
    for (final String url : urls) {
      try {
        svnURLs.add(SVNURL.parseURIEncoded(url));
      }
      catch (SVNException e) {
        //
      }
    }
    getRepositoryBrowser().setRepositoryURLs(svnURLs.toArray(new SVNURL[svnURLs.size()]), myShowFiles);
    getRepositoryBrowser().getRepositoryTree().addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        showPopup(e);
      }
      public void mousePressed(MouseEvent e) {
        showPopup(e);
      }
      public void mouseReleased(MouseEvent e) {
        showPopup(e);
      }

      private void showPopup(MouseEvent e) {
        if (e.isPopupTrigger()) {
          JTree tree = getRepositoryBrowser().getRepositoryTree();
          int row = tree.getRowForLocation(e.getX(), e.getY());
          if (row >= 0) {
            tree.setSelectionRow(row);
          }
          JPopupMenu popupMenu = createPopup(toolWindow);
          if (popupMenu != null) {
            popupMenu.show(e.getComponent(), e.getX(), e.getY());
          }
        }
      }
    });
    return panel;
  }

  protected RepositoryBrowserComponent getRepositoryBrowser() {
    if (myRepositoryBrowser == null) {
      myRepositoryBrowser = new RepositoryBrowserComponent(SvnVcs.getInstance(myProject));
    }
    return myRepositoryBrowser;
  }

  public void disposeRepositoryBrowser() {
    if (myRepositoryBrowser != null) {
      Disposer.dispose(myRepositoryBrowser);
      myRepositoryBrowser = null;
    }
  }

  protected void dispose() {
    super.dispose();
    disposeRepositoryBrowser();
  }

  public JComponent getPreferredFocusedComponent() {
    return (JComponent) getRepositoryBrowser().getPreferredFocusedComponent();
  }

  public boolean shouldCloseOnCross() {
    return true;
  }

  public boolean isOKActionEnabled() {
    return true;
  }

  public String getSelectedURL() {
    return getRepositoryBrowser().getSelectedURL();
  }

  @Nullable
  protected RepositoryTreeNode getSelectedNode() {
    return getRepositoryBrowser().getSelectedNode();
  }

  protected class HistoryAction extends AnAction {
    public void update(AnActionEvent e) {
      e.getPresentation().setText(SvnBundle.message("repository.browser.history.action"));
      e.getPresentation().setDescription(SvnBundle.message("repository.browser.history.action"));
      final RepositoryTreeNode node = getRepositoryBrowser().getSelectedNode();
      e.getPresentation().setEnabled(node != null && node.getURL() != null && !myProject.isDefault());
    }

    public void actionPerformed(AnActionEvent e) {
      final RepositoryTreeNode node = getSelectedNode();
      if (node == null) {
        return;
      }
      boolean isDirectory = node.getUserObject() instanceof SVNURL ||
                            (node.getSVNDirEntry() != null && node.getSVNDirEntry().getKind() == SVNNodeKind.DIR);
      AbstractVcsHelper.getInstance(myProject).showFileHistory(
              new SvnHistoryProvider(myVCS, node.getURL(), SVNRevision.HEAD, isDirectory),
              VcsUtil.getFilePath(node.getURL().toString()), myVCS, node.getURL().toString());
      node.reload(false);
    }
  }

  protected class RefreshAction extends AnAction {
    public void update(AnActionEvent e) {
      e.getPresentation().setText(SvnBundle.message("action.name.refresh"));
      e.getPresentation().setDescription(SvnBundle.message("repository.browser.refresh.action"));
      e.getPresentation().setIcon(IconLoader.getIcon("/actions/sync.png"));
      e.getPresentation().setEnabled(getRepositoryBrowser().getSelectedNode() != null);
    }

    public void actionPerformed(AnActionEvent e) {
      final RepositoryTreeNode selectedNode = getSelectedNode();
      if (selectedNode != null) {
        selectedNode.reload(true);
      }
    }
  }

  protected class AddLocationAction extends AnAction {

    public AddLocationAction() {
      super(SvnBundle.message("repository.browser.add.location.menu.item"));
    }

    public void update(AnActionEvent e) {
      if (e.getPlace().equals(PLACE_TOOLBAR)) {
        e.getPresentation().setDescription(SvnBundle.message("repository.browser.add.location.action"));
        e.getPresentation().setText(SvnBundle.message("repository.browser.add.location.action"));
        e.getPresentation().setIcon(IconLoader.getIcon("/general/add.png"));
      }
    }

    public void actionPerformed(AnActionEvent e) {
      final SvnApplicationSettings settings = SvnApplicationSettings.getInstance();
      final AddRepositoryLocationDialog dialog = new AddRepositoryLocationDialog(myProject, settings.getTypedUrlsListCopy());
      dialog.show();
      if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
        final String url = dialog.getSelected();
        if (url != null && url.length() > 0) {
          settings.addTypedUrl(url);
          settings.addCheckoutURL(url);
          getRepositoryBrowser().addURL(url);
        }
      }
    }
  }

  protected class EditLocationAction extends AnAction {
    public EditLocationAction() {
      super(SvnBundle.message("repository.browser.edit.location.menu.item"));
    }

    public void update(AnActionEvent e) {
      RepositoryTreeNode node = getRepositoryBrowser().getSelectedNode();
      if (e.getPlace().equals(PLACE_TOOLBAR)) {
        e.getPresentation().setDescription(SvnBundle.message("repository.browser.edit.location.menu.item"));
        e.getPresentation().setText(SvnBundle.message("repository.browser.edit.location.menu.item"));
        e.getPresentation().setIcon(IconLoader.getIcon("/actions/editSource.png"));
      }
      e.getPresentation().setEnabled(node != null && node.getParent() instanceof RepositoryTreeRootNode);
    }

    public void actionPerformed(AnActionEvent e) {
      RepositoryTreeNode node = getRepositoryBrowser().getSelectedNode();
      if (node == null || (! (node.getParent() instanceof RepositoryTreeRootNode))) {
        return;
      }
      final String oldUrl = node.getURL().toString();
      final SvnApplicationSettings settings = SvnApplicationSettings.getInstance();
      final AddRepositoryLocationDialog dialog = new AddRepositoryLocationDialog(myProject, settings.getTypedUrlsListCopy()) {
        @Override
        protected String initText() {
          return oldUrl;
        }

        @Override
        public String getTitle() {
          return SvnBundle.message("repository.browser.edit.location.dialog.title");
        }
      };
      dialog.show();
      if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
        final String url = dialog.getSelected();
        if (url != null && url.length() > 0) {
          settings.addTypedUrl(url);
          settings.removeCheckoutURL(oldUrl);
          settings.addCheckoutURL(url);
          final RepositoryBrowserComponent browser = getRepositoryBrowser();
          browser.removeURL(oldUrl);
          browser.addURL(url);
        }
      }
    }
  }

  protected class DiscardLocationAction extends AnAction {
    public void update(AnActionEvent e) {
      RepositoryTreeNode node = getRepositoryBrowser().getSelectedNode();
      e.getPresentation().setText(SvnBundle.message("repository.browser.discard.location.action"), true);
      e.getPresentation().setIcon(IconLoader.getIcon("/general/remove.png"));
      e.getPresentation().setEnabled(node != null && node.getParent() instanceof RepositoryTreeRootNode);
    }

    public void actionPerformed(AnActionEvent e) {
      RepositoryTreeNode node = getSelectedNode();
      if (node == null) {
        return;
      }
      SVNURL url = node.getURL();
      if (url != null) {
        int rc = Messages.showYesNoDialog(myProject, SvnBundle.message("repository.browser.discard.location.prompt", url.toString()),
                                          SvnBundle.message("repository.browser.discard.location.title"), Messages.getQuestionIcon());
        if (rc != 0) {
          return;
        }
        SvnApplicationSettings.getInstance().removeCheckoutURL(url.toString());
        getRepositoryBrowser().removeURL(url.toString());
      }
    }
  }

  protected class MkDirAction extends AnAction {
    public void update(AnActionEvent e) {
      RepositoryTreeNode node = getRepositoryBrowser().getSelectedNode();
      e.getPresentation().setText(SvnBundle.message("repository.browser.new.folder.action"), true);
      if (node != null) {
        SVNDirEntry entry = node.getSVNDirEntry();
        e.getPresentation().setEnabled(entry == null || entry.getKind() == SVNNodeKind.DIR);
      } else {
        e.getPresentation().setEnabled(false);
      }
    }

    public void actionPerformed(AnActionEvent e) {
      // show dialog for comment and folder name, then create folder
      // then refresh selected node.
      final RepositoryTreeNode node = getSelectedNode();
      if (node == null) {
        return;
      }
      MkdirOptionsDialog dialog = new MkdirOptionsDialog(myProject, node.getURL());
      dialog.show();
      VcsConfiguration.getInstance(myProject).saveCommitMessage(dialog.getCommitMessage());
      if (dialog.isOK()) {
        SVNURL url = dialog.getURL();
        String message = dialog.getCommitMessage();
        doMkdir(url, message);

        final SVNURL repositoryUrl = (node.getSVNDirEntry() == null) ? node.getURL() : node.getSVNDirEntry().getRepositoryRoot();
        final SyntheticWorker worker = new SyntheticWorker(node.getURL());
        worker.addSyntheticChildToSelf(url, repositoryUrl, dialog.getName(), true);

        node.reload(false);
      }
    }
  }

  protected class DiffAction extends AnAction {
    public void update(AnActionEvent e) {
      RepositoryTreeNode node = getRepositoryBrowser().getSelectedNode();
      e.getPresentation().setText("Compare With...", true);
      if (node != null) {
        SVNDirEntry entry = node.getSVNDirEntry();
        e.getPresentation().setEnabled(entry == null || entry.getKind() == SVNNodeKind.DIR);
      } else {
        e.getPresentation().setEnabled(false);
      }
    }

    public void actionPerformed(AnActionEvent e) {
      // show dialog for comment and folder name, then create folder
      // then refresh selected node.
      SVNURL root;
      RepositoryTreeNode node = getRepositoryBrowser().getSelectedNode();
      if (node == null) {
        return;
      }
      while (node.getSVNDirEntry() != null) {
        node = (RepositoryTreeNode) node.getParent();
      }
      root = node.getURL();
      final RepositoryTreeNode selectedNode = getSelectedNode();
      if (selectedNode == null) {
        return;
      }
      SVNURL sourceURL = selectedNode.getURL();
      DiffOptionsDialog dialog = new DiffOptionsDialog(myProject, root, sourceURL);
      dialog.show();
      if (dialog.isOK()) {
        SVNURL targetURL = dialog.getTargetURL();
        if (dialog.isReverseDiff()) {
          targetURL = sourceURL;
          sourceURL = dialog.getTargetURL();
        }

        final SVNURL sURL = sourceURL;
        final SVNURL tURL = targetURL;

        Runnable command;
        boolean cancelable;
        if (dialog.isUnifiedDiff()) {
          final File targetFile = dialog.getTargetFile();
          command = new Runnable() {
            public void run() {
              targetFile.getParentFile().mkdirs();
              doUnifiedDiff(targetFile, sURL, tURL);
            }
          };
          cancelable = false;
        } else {
          command = new Runnable() {
            public void run() {
              try {
                doGraphicalDiff(sURL, tURL);
              }
              catch(SVNCancelException ex) {
                // ignore
              }
              catch (final SVNException e1) {
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                  public void run() {
                    Messages.showErrorDialog(myProject, e1.getErrorMessage().getFullMessage(), "Error");
                  }
                });
              }
            }
          };
          cancelable = true;
        }
        ProgressManager.getInstance().runProcessWithProgressSynchronously(command, SvnBundle.message("progress.computing.difference"),
                                                                          cancelable, myProject);
      }
    }
  }

  protected class CopyOrMoveAction extends AnAction {
    private final String myActionName;
    private final String myDialogTitleKey;
    private final boolean myMove;

    public CopyOrMoveAction(final String actionName, final String dialogTitleKey, final boolean move) {
      myActionName = actionName;
      myDialogTitleKey = dialogTitleKey;
      myMove = move;
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setText(myActionName);
      RepositoryTreeNode node = getRepositoryBrowser().getSelectedNode();
      e.getPresentation().setEnabled(node != null && node.getSVNDirEntry() != null);
    }

    public void actionPerformed(final AnActionEvent e) {
      final RepositoryTreeNode node = getSelectedNode();
      if (node == null) {
        return;
      }
      RepositoryTreeNode rootNode = node;
      while (! rootNode.isRepositoryRoot()) {
        rootNode = (RepositoryTreeNode) rootNode.getParent();
      }

      CopyOptionsDialog dialog = new CopyOptionsDialog(SvnBundle.message(myDialogTitleKey), myProject, rootNode, node);
      dialog.show();
      VcsConfiguration.getInstance(myProject).saveCommitMessage(dialog.getCommitMessage());
      if (dialog.isOK()) {
        SVNURL dst = dialog.getTargetURL();
        SVNURL src = dialog.getSourceURL();
        String message = dialog.getCommitMessage();
        doCopy(src, dst, myMove, message);

        final CopyMoveReloadHelper sourceReloader = myMove ? new MoveSourceReloader(node) : CopyMoveReloadHelper.EMPTY;
        final TargetReloader destinationReloader = new TargetReloader(dialog, node, rootNode, myRepositoryBrowser);

        sourceReloader.doSynthetic();
        destinationReloader.doSynthetic();
        if ((! myMove) || (! Comparing.equal(sourceReloader.parent(), destinationReloader.parent()))) {
          destinationReloader.doRefresh();
        }
        sourceReloader.doRefresh();
      }
    }
  }

  private static class TargetReloader implements CopyMoveReloadHelper {
    private final RepositoryTreeNode myDialogParent;
    private final SVNURL myDst;
    private final RepositoryTreeNode mySourceNode;
    private final RepositoryTreeNode myRoot;
    private final RepositoryBrowserComponent myBrowserComponent;
    private final String myNewName;

    private TargetReloader(final CopyOptionsDialog dialog, final RepositoryTreeNode node,
                           final RepositoryTreeNode root, final RepositoryBrowserComponent browserComponent) {
      myDialogParent = dialog.getTargetParentNode();
      myDst = dialog.getTargetURL();
      mySourceNode = node;
      myRoot = root;
      myBrowserComponent = browserComponent;
      myNewName = dialog.getName();
    }

    public void doRefresh() {
      final TreeNode[] oldPath = myDialogParent.getSelfPath();
      final TreeNode[] correctedPath = new TreeNode[oldPath.length + 1];
      System.arraycopy(oldPath, 0, correctedPath, 1, oldPath.length);

      myRoot.reload(new OpeningExpander(oldPath, myBrowserComponent, myDialogParent), false);
    }

    public void doSynthetic() {
      final SyntheticWorker parentWorker = new SyntheticWorker(myDialogParent.getURL());
      parentWorker.addSyntheticChildToSelf(myDst, myRoot.getURL(), myNewName, ! mySourceNode.isLeaf());
      parentWorker.copyTreeToSelf(mySourceNode);
    }

    public SVNURL parent() {
      return myDialogParent.getURL();
    }
  }

  private static class MoveSourceReloader implements CopyMoveReloadHelper {
    private final RepositoryTreeNode mySource;
    private final RepositoryTreeNode myParent;

    private MoveSourceReloader(final RepositoryTreeNode source) {
      mySource = source;
      myParent = (RepositoryTreeNode) source.getParent();
    }

    public void doRefresh() {
      myParent.reload(false);
    }

    public void doSynthetic() {
      final SyntheticWorker worker = new SyntheticWorker(mySource.getURL());
      worker.removeSelf();
    }

    public SVNURL parent() {
      return myParent.getURL();
    }
  }

  private interface CopyMoveReloadHelper {
    void doRefresh();
    void doSynthetic();
    @Nullable
    SVNURL parent();

    CopyMoveReloadHelper EMPTY = new CopyMoveReloadHelper() {
      public void doRefresh() {
      }
      public void doSynthetic() {
      }
      @Nullable
      public SVNURL parent() {
        return null;
      }
    };
  }

  protected class CopyUrlAction extends AnAction {
    public void update(AnActionEvent e) {
      e.getPresentation().setText("Copy URL...");
      RepositoryTreeNode node = getRepositoryBrowser().getSelectedNode();
      e.getPresentation().setEnabled(node != null);
    }

    public void actionPerformed(final AnActionEvent e) {
      final RepositoryTreeNode treeNode = getRepositoryBrowser().getSelectedNode();
      if (treeNode != null) {
        final String url = treeNode.getURL().toString();
        CopyPasteManager.getInstance().setContents(new StringSelection(url));
      }
    }
  }

  protected class DeleteAction extends AnAction {

    public void update(AnActionEvent e) {
      e.getPresentation().setText("_Delete...");
      RepositoryTreeNode node = getRepositoryBrowser().getSelectedNode();
      e.getPresentation().setEnabled(node != null && node.getSVNDirEntry() != null);
    }

    public void actionPerformed(AnActionEvent e) {
      DeleteOptionsDialog dialog = new DeleteOptionsDialog(myProject);
      RepositoryTreeNode node = getRepositoryBrowser().getSelectedNode();
      dialog.show();
      VcsConfiguration.getInstance(myProject).saveCommitMessage(dialog.getCommitMessage());
      if (dialog.isOK()) {
        SVNURL url = node.getURL();
        String message = dialog.getCommitMessage();
        final boolean successful = doDelete(url, message);

        if (successful) {
          final SyntheticWorker worker = new SyntheticWorker(url);
          worker.removeSelf();
          final RepositoryTreeNode parentNode = (RepositoryTreeNode) node.getParent();
          parentNode.reload(new KeepingExpandedExpander(myRepositoryBrowser, new AfterDeletionSelectionInstaller(node)), false);
        }
      }
    }
  }

  private class AfterDeletionSelectionInstaller implements Expander {
    private final RepositoryTreeNode myParentNode;
    private final String myDeletedNodeName;
    private final boolean myIsFolder;

    private AfterDeletionSelectionInstaller(final RepositoryTreeNode deletedNode) {
      myParentNode = (RepositoryTreeNode) deletedNode.getParent();
      myDeletedNodeName = deletedNode.toString();
      myIsFolder = ! deletedNode.isLeaf();
    }

    public void onBeforeRefresh(final RepositoryTreeNode node) {
    }

    public void onAfterRefresh(final RepositoryTreeNode node) {
      TreeNode nodeToSelect = myParentNode.getNextChildByKey(myDeletedNodeName, myIsFolder);
      nodeToSelect = (nodeToSelect == null) ? myParentNode : nodeToSelect;
      getRepositoryBrowser().setSelectedNode(nodeToSelect);
    }
  }

  protected class ImportAction extends AnAction {
    public void update(AnActionEvent e) {
      e.getPresentation().setVisible(showImportAction());
      e.getPresentation().setText(SvnBundle.message("repository.browser.import.action"));
      RepositoryTreeNode node = getRepositoryBrowser().getSelectedNode();
      final boolean running = ProjectLevelVcsManager.getInstance(myProject).isBackgroundVcsOperationRunning();
      if (node != null) {
        SVNDirEntry entry = node.getSVNDirEntry();
        e.getPresentation().setEnabled((entry == null || entry.getKind() == SVNNodeKind.DIR) && (! running));
      } else {
        e.getPresentation().setEnabled(false);
      }
    }

    public void actionPerformed(AnActionEvent e) {
      // get directory, then import.
      doImport();
    }
  }

  protected class ExportAction extends AnAction {
    public void update(AnActionEvent e) {
      e.getPresentation().setText("_Export...");
      e.getPresentation().setEnabled(getRepositoryBrowser().getSelectedNode() != null);
    }
    public void actionPerformed(AnActionEvent e) {
      final RepositoryTreeNode selectedNode = getSelectedNode();
      if (selectedNode == null) {
        return;
      }
      SVNURL url = selectedNode.getURL();
      final File dir = selectFile("Destination directory", "Select export destination directory");
      if (dir == null) {
        return;
      }
      Project p = e.getData(PlatformDataKeys.PROJECT);
      ExportOptionsDialog dialog = new ExportOptionsDialog(p, url, dir);
      dialog.show();
      if (dialog.isOK()) {
        SvnCheckoutProvider.doExport(myProject, dir, url.toString(), dialog.getDepth(),
                dialog.isIgnoreExternals(), dialog.isForce(), dialog.getEOLStyle());
      }
    }
  }
  protected class CheckoutAction extends AnAction {
    public void update(AnActionEvent e) {
      e.getPresentation().setText("_Checkout...", true);
      RepositoryTreeNode node = getRepositoryBrowser().getSelectedNode();
      if (node != null) {
        SVNDirEntry entry = node.getSVNDirEntry();
        e.getPresentation().setEnabled(entry == null || entry.getKind() == SVNNodeKind.DIR);
      } else {
        e.getPresentation().setEnabled(false);
      }
    }
    public void actionPerformed(AnActionEvent e) {
      final RepositoryTreeNode selectedNode = getSelectedNode();
      if (! ModalityState.NON_MODAL.equals(ModalityState.current())) {
        doCancelAction();
      }
      doCheckout(ProjectLevelVcsManager.getInstance(myProject).getCompositeCheckoutListener(), selectedNode);
    }
  }

  protected class BrowseChangesAction extends AnAction {
    public BrowseChangesAction() {
      super(SvnBundle.message("repository.browser.browse.changes.action"),
            SvnBundle.message("repository.browser.browse.changes.description"), null);
    }

    public void actionPerformed(AnActionEvent e) {
      RepositoryTreeNode node = getSelectedNode();
      if (node == null) {
        return;
      }
      SVNURL url = node.getURL();
      AbstractVcsHelper.getInstance(myProject).showChangesBrowser(myVCS.getCommittedChangesProvider(), 
                                                                  new SvnRepositoryLocation(url.toString()),
                                                                  "Changes in " + url.toString(), null);
    }

    public void update(final AnActionEvent e) {
      e.getPresentation().setEnabled(getRepositoryBrowser().getSelectedNode() != null);
    }
  }

  protected class OpenAction extends AnAction {
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(false);
      if (myVCS == null) {
        return;
      }
      e.getPresentation().setText("_Open", true);
      e.getPresentation().setEnabled(getRepositoryBrowser().getSelectedVcsFile() != null);
    }
    public void actionPerformed(AnActionEvent e) {
      VirtualFile vcsVF = getRepositoryBrowser().getSelectedVcsFile();
      if (vcsVF != null) {
        FileEditorManager.getInstance(myVCS.getProject()).openFile(vcsVF, true);
      }
    }
  }

  protected class DetailsAction extends ToggleAction {

    private boolean myIsSelected;

    public void update(final AnActionEvent e) {
      e.getPresentation().setDescription(SvnBundle.message("repository.browser.details.action"));
      e.getPresentation().setText(SvnBundle.message("repository.browser.details.action"));
      e.getPresentation().setIcon(IconLoader.getIcon("/actions/annotate.png"));
      super.update(e);
    }

    public boolean isSelected(AnActionEvent e) {
      return myIsSelected;
    }

    public void setSelected(AnActionEvent e, boolean state) {
      myIsSelected = state;
      SvnRepositoryTreeCellRenderer r = new SvnRepositoryTreeCellRenderer();
      r.setShowDetails(state);
      getRepositoryBrowser().getRepositoryTree().setCellRenderer(r);
    }
  }

  @Nullable
  private File selectFile(String title, String description) {
    FileChooserDescriptor fcd = new FileChooserDescriptor(false, true, false, false, false, false);
    fcd.setShowFileSystemRoots(true);
    fcd.setTitle(title);
    fcd.setDescription(description);
    fcd.setHideIgnored(false);
    VirtualFile[] files = FileChooser.chooseFiles(myProject, fcd, null);
    if (files.length != 1 || files[0] == null) {
      return null;
    }
    final String path = files[0].getPath();
    if (path.endsWith(":")) {   // workaround for VFS oddities with drive root (IDEADEV-20870)
      return new File(path + "/");
    }
    return new File(path);
  }

  protected void doMkdir(final SVNURL url, final String comment) {
    final SVNException[] exception = new SVNException[1];
    Runnable command = new Runnable() {
      public void run() {
        ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
        if (progress != null) {
          progress.setText(SvnBundle.message("progress.text.browser.creating", url.toString()));
        }
        SvnVcs vcs = SvnVcs.getInstance(myProject);
        try {
          SVNCommitClient committer = vcs.createCommitClient();
          committer.doMkDir(new SVNURL[] {url}, comment);
        }
        catch (SVNException e) {
          exception[0] = e;
        }
      }
    };
    ProgressManager.getInstance().runProcessWithProgressSynchronously(command, SvnBundle.message("progress.text.create.remote.folder"), false, myProject);
    if (exception[0] != null) {
      Messages.showErrorDialog(exception[0].getMessage(), SvnBundle.message("message.text.error"));
    }
  }
  private boolean doDelete(final SVNURL url, final String comment) {
    final SVNException[] exception = new SVNException[1];
    Runnable command = new Runnable() {
      public void run() {
        ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
        if (progress != null) {
          progress.setText(SvnBundle.message("progres.text.deleting", url.toString()));
        }
        SvnVcs vcs = SvnVcs.getInstance(myProject);
        try {
          SVNCommitClient committer = vcs.createCommitClient();
          committer.doDelete(new SVNURL[]{url}, comment);
        }
        catch (SVNException e) {
          exception[0] = e;
        }
      }
    };
    ProgressManager.getInstance().runProcessWithProgressSynchronously(command, SvnBundle.message("progress.title.browser.delete"), false, myProject);
    if (exception[0] != null) {
      Messages.showErrorDialog(exception[0].getMessage(), SvnBundle.message("message.text.error"));
    }
    return exception[0] == null;
  }

  private void doCopy(final SVNURL src, final SVNURL dst, final boolean move, final String comment) {
    final SVNException[] exception = new SVNException[1];
    Runnable command = new Runnable() {
      public void run() {
        ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
        if (progress != null) {
          progress.setText((move ? SvnBundle.message("progress.text.browser.moving", src) : SvnBundle.message("progress.text.browser.copying", src)));
          progress.setText2(SvnBundle.message("progress.text.browser.remote.destination", dst));
        }
        SvnVcs vcs = SvnVcs.getInstance(myProject);
        try {
          SVNCopyClient committer = vcs.createCopyClient();
          final SVNCopySource[] copySource = new SVNCopySource[] {new SVNCopySource(SVNRevision.HEAD, SVNRevision.HEAD, src)};
          committer.doCopy(copySource, dst, move, true, true, comment, null);
        }
        catch (SVNException e) {
          exception[0] = e;
        }
      }
    };
    String progressTitle = move ? SvnBundle.message("progress.title.browser.move") : SvnBundle.message("progress.title.browser.copy");
    ProgressManager.getInstance().runProcessWithProgressSynchronously(command, progressTitle, false, myProject);
    if (exception[0] != null) {
      Messages.showErrorDialog(exception[0].getMessage(), SvnBundle.message("message.text.error"));
    }
  }

  protected void doCheckout(@Nullable final CheckoutProvider.Listener listener, final RepositoryTreeNode selectedNode) {
    if (selectedNode == null) {
      return;
    }
    SVNURL url = selectedNode.getURL();

    final String relativePath;
    if (selectedNode.isRepositoryRoot()) {
      relativePath = "";
    } else {
      final SVNDirEntry dirEntry = selectedNode.getSVNDirEntry();
      if (dirEntry == null) {
        return;
      }
      if (dirEntry.getRepositoryRoot() != null) {
        relativePath = SVNPathUtil.getRelativePath(dirEntry.getRepositoryRoot().toString(), url.toString());
      } else {
        relativePath = dirEntry.getRelativePath();
      }
    }

    File dir = selectFile(SvnBundle.message("svn.checkout.destination.directory.title"),
                          SvnBundle.message("svn.checkout.destination.directory.description"));
    if (dir == null) {
      return;
    }

    Project p = myProject;
    CheckoutOptionsDialog dialog = new CheckoutOptionsDialog(p, url, dir, SvnUtil.getVirtualFile(dir.getAbsolutePath()), relativePath);
    dialog.show();
    dir = dialog.getTarget();
    if (dialog.isOK() && dir != null) {
      final SVNRevision revision;
        try {
          revision =  dialog.getRevision();
        } catch (ConfigurationException e) {
          Messages.showErrorDialog(SvnBundle.message("message.text.cannot.checkout", e.getMessage()), SvnBundle.message("message.title.check.out"));
          return;
        }

      SvnCheckoutProvider.doCheckout(myProject, dir, url.toString(), revision, dialog.getDepth(), dialog.isIgnoreExternals(), listener);
    }
  }

  /**
   * @return true only if import was called
   */
  protected boolean doImport() {
    File dir = selectFile("Import Directory", "Select directory to import into repository");
    if (dir == null) {
      return false;
    }

    final RepositoryTreeNode selectedNode = getSelectedNode();
    if (selectedNode == null) {
      return false;
    }
    SVNURL url = selectedNode.getURL();
    ImportOptionsDialog dialog = new ImportOptionsDialog(myProject, url, dir);
    dialog.show();
    VcsConfiguration.getInstance(myProject).saveCommitMessage(dialog.getCommitMessage());
    if (dialog.isOK()) {
      File src = dialog.getTarget();
      boolean ignored = dialog.isIncludeIgnored();
      String message = dialog.getCommitMessage();
      SvnCheckoutProvider.doImport(myProject, src, url, dialog.getDepth(), ignored, message);
      selectedNode.reload(false);
    }
    return dialog.isOK();
  }

  private void doUnifiedDiff(File targetFile, SVNURL sourceURL, SVNURL targetURL) {
    OutputStream os = null;
    try {
      os = new BufferedOutputStream(new FileOutputStream(targetFile));
      myVCS.createDiffClient().doDiff(sourceURL, SVNRevision.HEAD, targetURL, SVNRevision.HEAD, true, false, os);
    } catch (IOException e1) {
      //
    } catch (SVNException e1) {
      //
    } finally {
      if (os != null) {
        try {
          os.close();
        } catch (IOException e1) {
          //
        }
      }
    }
  }

  private void doGraphicalDiff(SVNURL sourceURL, SVNURL targetURL) throws SVNException {
    SVNRepository sourceRepository = myVCS.createRepository(sourceURL.toString());
    sourceRepository.setCanceller(new SvnProgressCanceller());
    SvnDiffEditor diffEditor;
    final long rev;
    SVNRepository targetRepository = null;
    try {
      rev = sourceRepository.getLatestRevision();
      // generate Map of path->Change
      targetRepository = myVCS.createRepository(targetURL.toString());
      diffEditor = new SvnDiffEditor(sourceRepository, targetRepository, -1, false);
      final ISVNEditor cancellableEditor = SVNCancellableEditor.newInstance(diffEditor, new SvnProgressCanceller(), null);
      sourceRepository.diff(targetURL, rev, rev, null, true, true, false, new ISVNReporterBaton() {
        public void report(ISVNReporter reporter) throws SVNException {
          reporter.setPath("", null, rev, false);
          reporter.finishReport();
        }
      }, cancellableEditor);
    }
    finally {
      sourceRepository.closeSession();
      if (targetRepository != null) {
        targetRepository.closeSession();
      }
    }
    final String sourceTitle = SVNPathUtil.tail(sourceURL.toString());
    final String targetTitle = SVNPathUtil.tail(targetURL.toString());
    showDiffEditorResults(diffEditor.getChangesMap(), sourceTitle, targetTitle, sourceURL, targetURL, rev);
  }

  private void showDiffEditorResults(final Map<String, Change> changes, String sourceTitle, String targetTitle,
                                     final SVNURL sourceUrl, final SVNURL targetUrl, final long revision) {
    if (changes.isEmpty()) {
      // display no changes dialog.
      final String text = SvnBundle.message("repository.browser.compare.no.difference.message", sourceTitle, targetTitle);
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          Messages.showInfoMessage(myProject, text, SvnBundle.message("repository.browser.compare.no.difference.title"));
        }
      });
      return;
    }
    final Collection<Change> changesList = changes.values();
    /*final Collection<Change> changesListConverted = new ArrayList<Change>(changesList.size());
    for (Change change : changesList) {
      final FilePath path = ChangesUtil.getFilePath(change);
      final Change newChange = new Change(
          new UrlContentRevision(change.getBeforeRevision(),
                                 FilePathImpl.createNonLocal(SVNPathUtil.append(sourceUrl.toString(), path.getName()), path.isDirectory()), revision),
          new UrlContentRevision(change.getAfterRevision(),
                                 FilePathImpl.createNonLocal(SVNPathUtil.append(targetUrl.toString(), path.getName()), path.isDirectory()), revision));
      changesListConverted.add(newChange);
    }*/

    final String title = SvnBundle.message("repository.browser.compare.title", sourceTitle, targetTitle);
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        final ChangeListViewerDialog dlg = new ChangeListViewerDialog(myRepositoryBrowser, myProject, changesList, true);
        dlg.setTitle(title);
        dlg.setConvertor(new NotNullFunction<Change, Change>() {
          @NotNull
          public Change fun(final Change change) {
            final FilePath path = ChangesUtil.getFilePath(change);

            return new Change(new UrlContentRevision(change.getBeforeRevision(),
                                 FilePathImpl.createNonLocal(SVNPathUtil.append(sourceUrl.toString(), path.getPath()), path.isDirectory()), revision),
                              new UrlContentRevision(change.getAfterRevision(),
                                 FilePathImpl.createNonLocal(SVNPathUtil.append(targetUrl.toString(), path.getPath()), path.isDirectory()), revision));
          }
        });
        dlg.show();
      }
    });
  }

  private class CloseToolWindowAction extends AnAction {
    public void actionPerformed(AnActionEvent e) {
      disposeRepositoryBrowser();
      Project p = e.getData(PlatformDataKeys.PROJECT);
      ToolWindowManager.getInstance(p).unregisterToolWindow(BrowseRepositoryAction.REPOSITORY_BROWSER_TOOLWINDOW);

    }

    public void update(AnActionEvent e) {
      e.getPresentation().setText("Close");
      e.getPresentation().setDescription("Close this tool window");
      e.getPresentation().setIcon(IconLoader.getIcon("/actions/cancel.png"));
    }
  }

  public void setDefaultExpander(final NotNullFunction<RepositoryBrowserComponent, Expander> expanderFactory) {
    myRepositoryBrowser.setLazyLoadingExpander(expanderFactory);
  }

  private static class UrlContentRevision implements ContentRevision {
    private final ContentRevision myContentRevision;
    private final FilePath myPath;
    private final SvnRevisionNumber myNumber;

    private UrlContentRevision(final ContentRevision contentRevision, final FilePath path, final long revision) {
      myContentRevision = contentRevision;
      myPath = path;
      myNumber = new SvnRevisionNumber(SVNRevision.create(revision));
    }

    public String getContent() throws VcsException {
      return (myContentRevision == null) ? "" : myContentRevision.getContent();
    }

    @NotNull
    public FilePath getFile() {
      return myPath;
    }

    @NotNull
    public VcsRevisionNumber getRevisionNumber() {
      return myNumber;
    }
  }
}
