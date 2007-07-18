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

import com.intellij.CommonBundle;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.vfs.VcsFileSystem;
import com.intellij.openapi.vcs.vfs.VcsVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnApplicationSettings;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnProgressCanceller;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.actions.BrowseRepositoryAction;
import org.jetbrains.idea.svn.checkout.SvnCheckoutProvider;
import org.jetbrains.idea.svn.dialogs.browser.*;
import org.jetbrains.idea.svn.history.SvnFileRevision;
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
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.util.Collection;
import java.util.Map;

public class RepositoryBrowserDialog extends DialogWrapper {

  private Project myProject;
  private SvnVcs myVCS;
  private RepositoryBrowserComponent myRepositoryBrowser;

  @NonNls private static final String HELP_ID = "vcs.subversion.browseSVN";
  @NonNls public static final String COPY_OF_PREFIX = "CopyOf";
  @NonNls public static final String NEW_FOLDER_POSTFIX = "NewFolder";

  private RepositoryBrowserDialog.DeleteAction myDeleteAction = new DeleteAction();

  @NonNls private static final String PLACE_TOOLBAR = "RepositoryBrowser.Toolbar";
  @NonNls private static final String PLACE_MENU = "RepositoryBrowser.Menu";

  public RepositoryBrowserDialog(Project project) {
    super(project, true);
    myProject = project;
    myVCS = SvnVcs.getInstance(project);
    setTitle("SVN Repository Browser");
    setResizable(true);
    setOKButtonText(CommonBundle.getCloseButtonText());
    getHelpAction().setEnabled(true);
    init();
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HELP_ID);
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

  public JComponent createToolbar(boolean horizontal) {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new AddLocationAction());
    group.add(new DiscardLocationAction());
    group.add(new DetailsAction());
    group.addSeparator();
    final RefreshAction refreshAction = new RefreshAction();
    refreshAction.registerCustomShortcutSet(CommonShortcuts.getRerun(), getRepositoryBrowser());
    group.add(refreshAction);

    myDeleteAction.registerCustomShortcutSet(CommonShortcuts.DELETE, getRepositoryBrowser());

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
    if (!horizontal) {
      group.addSeparator();
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
    group.add(new CopyAction());
    group.add(new MoveAction());
    group.add(myDeleteAction);
    group.addSeparator();
    group.add(new RefreshAction());
    group.add(new DiscardLocationAction());
    ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu(PLACE_MENU, group);
    return menu.getComponent();
  }

  public JComponent createCenterPanel() {
    JPanel parentPanel = new JPanel(new BorderLayout());
    JPanel top = new JPanel(new BorderLayout());

    top.add(new JLabel("Repositories:"), BorderLayout.WEST);
    top.add(createToolbar(true), BorderLayout.EAST);
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
    SVNURL[] svnURLs = new SVNURL[urls.size()];
    int i = 0;
    for (final String url : urls) {
      try {
        svnURLs[i++] = SVNURL.parseURIEncoded(url);
      }
      catch (SVNException e) {
        //
      }
    }
    getRepositoryBrowser().setRepositoryURLs(svnURLs, true);
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

  private void disposeRepositoryBrowser() {
    if (myRepositoryBrowser != null) {
      Disposer.dispose(myRepositoryBrowser);
    }
  }

  protected void dispose() {
    super.dispose();
    disposeRepositoryBrowser();
  }

  public JComponent getPreferredFocusedComponent() {
    return getRepositoryBrowser();
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

  @NotNull
  private RepositoryTreeNode getNotNullSelectedNode() {
    final RepositoryTreeNode node = getRepositoryBrowser().getSelectedNode();
    assert node != null;
    return node;
  }

  protected class HistoryAction extends AnAction {
    public void update(AnActionEvent e) {
      e.getPresentation().setText(SvnBundle.message("repository.browser.history.action"));
      e.getPresentation().setDescription(SvnBundle.message("repository.browser.history.action"));
      final RepositoryTreeNode node = getRepositoryBrowser().getSelectedNode();
      e.getPresentation().setEnabled(node != null && node.getURL() != null && !myProject.isDefault());
    }

    public void actionPerformed(AnActionEvent e) {
      final RepositoryTreeNode node = getNotNullSelectedNode();
      boolean isDirectory = node.getUserObject() instanceof SVNURL ||
                            (node.getSVNDirEntry() != null && node.getSVNDirEntry().getKind() == SVNNodeKind.DIR);
      AbstractVcsHelper.getInstance(myProject).showFileHistory(
              new SvnHistoryProvider(myVCS, node.getURL(), SVNRevision.HEAD, isDirectory),
              VcsUtil.getFilePath(node.getURL().toString()));
      node.reload();
    }
  }

  protected class RefreshAction extends AnAction {
    public void update(AnActionEvent e) {
      e.getPresentation().setText(SvnBundle.message("action.name.refresh"));
      e.getPresentation().setDescription(SvnBundle.message("repository.browser.refresh.action"));
      e.getPresentation().setIcon(IconLoader.findIcon("/actions/sync.png"));
      e.getPresentation().setEnabled(getRepositoryBrowser().getSelectedNode() != null);
    }

    public void actionPerformed(AnActionEvent e) {
      getNotNullSelectedNode().reload();
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
        e.getPresentation().setIcon(IconLoader.findIcon("/general/add.png"));
      }
    }

    public void actionPerformed(AnActionEvent e) {
      String url = Messages.showInputDialog(myProject, SvnBundle.message("repository.browser.add.location.prompt"),
                                            SvnBundle.message("repository.browser.add.location.title"), null, "http://", new InputValidator() {
        public boolean checkInput(String inputString) {
          if (inputString == null) {
            return false;
          }
          try {
            return SVNURL.parseURIEncoded(inputString) != null;
          } catch (SVNException e) {
            //
          }
          return false;
        }
        public boolean canClose(String inputString) {
          return true;
        }
      });
      if (url != null) {
        SvnApplicationSettings.getInstance().addCheckoutURL(url);
        getRepositoryBrowser().addURL(url);
      }
    }
  }

  protected class DiscardLocationAction extends AnAction {
    public void update(AnActionEvent e) {
      RepositoryTreeNode node = getRepositoryBrowser().getSelectedNode();
      e.getPresentation().setText(SvnBundle.message("repository.browser.discard.location.action"), true);
      e.getPresentation().setIcon(IconLoader.findIcon("/general/remove.png"));
      e.getPresentation().setEnabled(node != null && node.getParent() instanceof RepositoryTreeRootNode);
    }

    public void actionPerformed(AnActionEvent e) {
      RepositoryTreeNode node = getNotNullSelectedNode();
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
      final RepositoryTreeNode node = getNotNullSelectedNode();
      MkdirOptionsDialog dialog = new MkdirOptionsDialog(myProject, node.getURL());
      dialog.show();
      if (dialog.isOK()) {
        SVNURL url = dialog.getURL();
        String message = dialog.getCommitMessage();
        doMkdir(url, message);
        node.reload();
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
      while (node.getSVNDirEntry() != null) {
        node = (RepositoryTreeNode) node.getParent();
      }
      root = node.getURL();
      SVNURL sourceURL = getNotNullSelectedNode().getURL();
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

  protected class CopyAction extends AnAction {
    public void update(AnActionEvent e) {
      e.getPresentation().setText("Branch or Tag...");
      RepositoryTreeNode node = getRepositoryBrowser().getSelectedNode();
      e.getPresentation().setEnabled(node != null && node.getSVNDirEntry() != null);
    }
    public void actionPerformed(AnActionEvent e) {
      SVNURL root;
      RepositoryTreeNode node = getRepositoryBrowser().getSelectedNode();
      while (node.getSVNDirEntry() != null) {
        node = (RepositoryTreeNode) node.getParent();
      }
      root = node.getURL();
      CopyOptionsDialog dialog = new CopyOptionsDialog("Branch or Tag", myProject, root, getNotNullSelectedNode().getURL());
      dialog.show();
      if (dialog.isOK()) {
        SVNURL dst = dialog.getTargetURL();
        SVNURL src = dialog.getSourceURL();
        String message = dialog.getCommitMessage();
        doCopy(src, dst, false, message);
        node.reload();
      }
    }
  }
  protected class MoveAction extends AnAction {

    public void update(AnActionEvent e) {
      e.getPresentation().setText("_Move or Rename...");
      RepositoryTreeNode node = getRepositoryBrowser().getSelectedNode();
      e.getPresentation().setEnabled(node != null && node.getSVNDirEntry() != null);
    }

    public void actionPerformed(AnActionEvent e) {
      SVNURL root;
      RepositoryTreeNode node = getRepositoryBrowser().getSelectedNode();
      while (node.getSVNDirEntry() != null) {
        node = (RepositoryTreeNode) node.getParent();
      }
      root = node.getURL();
      CopyOptionsDialog dialog = new CopyOptionsDialog("Move or Rename", myProject, root, getRepositoryBrowser().getSelectedNode().getURL());
      dialog.show();
      if (dialog.isOK()) {
        SVNURL dst = dialog.getTargetURL();
        SVNURL src = dialog.getSourceURL();
        String message = dialog.getCommitMessage();
        doCopy(src, dst, true, message);
        node.reload();
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
      if (dialog.isOK()) {
        SVNURL url = node.getURL();
        String message = dialog.getCommitMessage();
        doDelete(url, message);
        ((RepositoryTreeNode) node.getParent()).reload();
      }
    }
  }

  protected class ImportAction extends AnAction {
    public void update(AnActionEvent e) {
      e.getPresentation().setVisible(showImportAction());
      e.getPresentation().setText(SvnBundle.message("repository.browser.import.action"));
      RepositoryTreeNode node = getRepositoryBrowser().getSelectedNode();
      if (node != null) {
        SVNDirEntry entry = node.getSVNDirEntry();
        e.getPresentation().setEnabled(entry == null || entry.getKind() == SVNNodeKind.DIR);
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
      SVNURL url = getNotNullSelectedNode().getURL();
      final File dir = selectFile("Destination directory", "Select export destination directory");
      if (dir == null) {
        return;
      }
      Project p = e.getData(DataKeys.PROJECT);
      ExportOptionsDialog dialog = new ExportOptionsDialog(p, url, dir);
      dialog.show();
      if (dialog.isOK()) {
        SvnCheckoutProvider.doExport(myProject, dir, url.toString(), dialog.isRecursive(),
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
      doCheckout(null);
    }
  }

  protected class BrowseChangesAction extends AnAction {
    public BrowseChangesAction() {
      super(SvnBundle.message("repository.browser.browse.changes.action"),
            SvnBundle.message("repository.browser.browse.changes.description"), null);
    }

    public void actionPerformed(AnActionEvent e) {
      RepositoryTreeNode node = getNotNullSelectedNode();
      SVNURL url = node.getURL();
      AbstractVcsHelper.getInstance(myProject).showChangesBrowser(myVCS.getCommittedChangesProvider(), 
                                                                  new SvnRepositoryLocation(url.toString()),
                                                                  "Changes in " + url.toString(), getContentPane());
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
      RepositoryTreeNode node = getRepositoryBrowser().getSelectedNode();
      if (node != null) {
        SVNDirEntry entry = node.getSVNDirEntry();
        if (entry != null && entry.getKind() == SVNNodeKind.FILE) {
          String name = entry.getName();
          FileTypeManager manager = FileTypeManager.getInstance();
          e.getPresentation().setEnabled(entry.getName().lastIndexOf('.') > 0 && !manager.getFileTypeByFileName(name).isBinary());
        }
      }
    }
    public void actionPerformed(AnActionEvent e) {
      RepositoryTreeNode node = getRepositoryBrowser().getSelectedNode();
      SVNDirEntry entry = node.getSVNDirEntry();
      SVNURL url = node.getURL();
      Project p = e.getData(DataKeys.PROJECT);
      SVNRevision rev = SVNRevision.create(entry.getRevision());
      final SvnFileRevision revision = new SvnFileRevision(myVCS, SVNRevision.UNDEFINED, rev, url.toString(),
              entry.getAuthor(), entry.getDate(), null, null);
      VirtualFile vcsVF = new VcsVirtualFile(node.getSVNDirEntry().getName(), revision, VcsFileSystem.getInstance());
      FileEditorManager.getInstance(p).openFile(vcsVF, true);
    }
  }

  protected class DetailsAction extends ToggleAction {

    private boolean myIsSelected;

    public void update(final AnActionEvent e) {
      e.getPresentation().setDescription(SvnBundle.message("repository.browser.details.action"));
      e.getPresentation().setText(SvnBundle.message("repository.browser.details.action"));
      e.getPresentation().setIcon(IconLoader.findIcon("/actions/annotate.png"));
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
    VirtualFile[] files = FileChooser.chooseFiles(getRepositoryBrowser(), fcd, null);
    if (files.length != 1 || files[0] == null) {
      return null;
    }
    return new File(files[0].getPath());
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
  private void doDelete(final SVNURL url, final String comment) {
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
  }

  private void doCopy(final SVNURL src, final SVNURL dst, final boolean move, final String comment) {
    final SVNException[] exception = new SVNException[1];
    Runnable command = new Runnable() {
      public void run() {
        ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
        if (progress != null) {
          progress.setText((move ? SvnBundle.message("progress.text.browser.moving") : SvnBundle.message("progress.text.browser.copying")) + src);
          progress.setText2(SvnBundle.message("progress.text.browser.remote.destination", dst));
        }
        SvnVcs vcs = SvnVcs.getInstance(myProject);
        try {
          SVNCopyClient committer = vcs.createCopyClient();
          committer.doCopy(src, SVNRevision.HEAD, dst, move, comment);
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


  protected void doCheckout(@Nullable final CheckoutProvider.Listener listener) {
    SVNURL url = getNotNullSelectedNode().getURL();
    File dir = selectFile("Destination directory", "Select checkout destination directory");
    if (dir == null) {
      return;
    }
    Project p = myProject;
    CheckoutOptionsDialog dialog = new CheckoutOptionsDialog(p, url, dir);
    dialog.show();
    dir = dialog.getTarget();
    if (dialog.isOK()) {
      SvnCheckoutProvider.doCheckout(myProject, dir, url.toString(), dialog.isRecursive(), dialog.isIgnoreExternals(), listener);
    }
  }

  protected void doImport() {
    File dir = selectFile("Import Directory", "Select directory to import into repository");
    if (dir == null) {
      return;
    }

    SVNURL url = getNotNullSelectedNode().getURL();
    ImportOptionsDialog dialog = new ImportOptionsDialog(myProject, url, dir);
    dialog.show();
    if (dialog.isOK()) {
      File src = dialog.getTarget();
      boolean recursive = dialog.isRecursive();
      boolean ignored = dialog.isIncludeIgnored();
      String message = dialog.getCommitMessage();
      SvnCheckoutProvider.doImport(myProject, src, url, recursive, ignored, message);
      getNotNullSelectedNode().reload();
    }
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
    try {
      final long rev = sourceRepository.getLatestRevision();
      // generate Map of path->Change
      diffEditor = new SvnDiffEditor(sourceRepository, myVCS.createRepository(targetURL.toString()), -1, false);
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
    }
    final String sourceTitle = SVNPathUtil.tail(sourceURL.toString());
    final String targetTitle = SVNPathUtil.tail(targetURL.toString());
    showDiffEditorResults(diffEditor.getChangesMap(), sourceTitle, targetTitle);
  }

  public void showDiffEditorResults(final Map<String, Change> changes, String sourceTitle, String targetTitle) {
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

    final String title = SvnBundle.message("repository.browser.compare.title", sourceTitle, targetTitle);
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        AbstractVcsHelper.getInstance(myProject).showChangesBrowser(myRepositoryBrowser, changesList, title);
      }
    });
  }

  private class CloseToolWindowAction extends AnAction {
    public void actionPerformed(AnActionEvent e) {
      disposeRepositoryBrowser();
      Project p = e.getData(DataKeys.PROJECT);
      ToolWindowManager.getInstance(p).unregisterToolWindow(BrowseRepositoryAction.REPOSITORY_BROWSER_TOOLWINDOW);

    }

    public void update(AnActionEvent e) {
      e.getPresentation().setText("Close");
      e.getPresentation().setDescription("Close this tool window");
      e.getPresentation().setIcon(IconLoader.findIcon("/actions/cancel.png"));
    }
  }
}
