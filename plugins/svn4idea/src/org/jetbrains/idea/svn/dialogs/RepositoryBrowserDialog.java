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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.ui.treeStructure.actions.CollapseAllAction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.browser.*;
import org.jetbrains.idea.svn.checkout.SvnCheckoutProvider;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

public class RepositoryBrowserDialog extends DialogWrapper {

  private Project myProject;
  private SvnVcs myVCS;
  private RepositoryBrowserComponent myRepositoryBrowser;

  @NonNls private static final String HELP_ID = "vcs.subversion.browseSVN";
  @NonNls public static final String COPY_OF_PREFIX = "CopyOf";
  @NonNls public static final String NEW_FOLDER_POSTFIX = "NewFolder";

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

  protected void init() {
    super.init();
    SvnConfiguration config = SvnConfiguration.getInstance(myProject);
    Collection urls = config == null ? Collections.EMPTY_LIST : config.getCheckoutURLs();
    SVNURL[] svnURLs = new SVNURL[urls.size()];
    int i = 0;
    for (Iterator iterator = urls.iterator(); iterator.hasNext();) {
      String url = (String) iterator.next();
      try {
        svnURLs[i++] = SVNURL.parseURIEncoded(url);
      } catch (SVNException e) {
        //
      }
    }
    myRepositoryBrowser.setRepositoryURLs(svnURLs, true);
    myRepositoryBrowser.getRepositoryTree().addMouseListener(new MouseAdapter() {
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
          JTree tree = myRepositoryBrowser.getRepositoryTree();
          int row = tree.getRowForLocation(e.getX(), e.getY());
          if (row >= 0) {
            tree.setSelectionRow(row);
          }
          JPopupMenu popupMenu = createPopup();
          if (popupMenu != null) {
            popupMenu.show(e.getComponent(), e.getX(), e.getY());
          }
        }
      }
    });
  }

  protected JComponent createToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new AddLocationAction());
    group.add(new DetailsAction());
    group.addSeparator();
    group.add(new RefreshAction());
    group.add(new CollapseAllAction(myRepositoryBrowser.getRepositoryTree()));
    return ActionManager.getInstance().createActionToolbar("", group, true).getComponent();
  }

  protected JPopupMenu createPopup() {
    DefaultActionGroup group = new DefaultActionGroup();
    DefaultActionGroup newGroup = new DefaultActionGroup("_New", true);
    newGroup.add(new AddLocationAction());
    newGroup.add(new MkDirAction());
    group.add(newGroup);
    group.addSeparator();
    group.add(new CheckoutAction());
    group.addSeparator();
    group.add(new ImportAction());
    group.add(new ExportAction());
    group.addSeparator();
    group.add(new CopyAction());
    group.add(new MoveAction());
    group.add(new DeleteAction());
    group.addSeparator();
    group.add(new RefreshAction());
    group.add(new DiscardLocationAction());
    ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu("", group);
    return menu.getComponent();
  }

  protected JComponent createCenterPanel() {
    JComponent browser = createRepositoryBrowserComponent();

    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());

    GridBagConstraints gc = new GridBagConstraints();
    gc.gridx = 0;
    gc.gridwidth = 1;
    gc.gridy = 0;
    gc.gridheight = 1;
    gc.weightx = 1;
    gc.weighty = 0;
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.anchor = GridBagConstraints.SOUTHWEST;
    gc.insets = new Insets(2, 2, 2, 2);

    panel.add(new JLabel("SVN Repositories:"), gc);
    gc.weightx = 0;
    gc.gridx = 1;
    gc.anchor = GridBagConstraints.EAST;
    panel.add(createToolbar(), gc);

    gc.gridx = 0;
    gc.gridwidth = 2;
    gc.gridy += 1;
    gc.gridheight = 1;
    gc.weightx = 1;
    gc.weighty = 1;
    gc.fill = GridBagConstraints.BOTH;
    gc.anchor = GridBagConstraints.WEST;

    panel.add(browser, gc);

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
    gc.gridheight = 1;
    gc.gridx = 0;
    gc.gridy = 0;
    gc.anchor = GridBagConstraints.WEST;
    gc.fill = GridBagConstraints.BOTH;
    gc.weightx = 1;
    gc.weighty = 1;

    myRepositoryBrowser = new RepositoryBrowserComponent(SvnVcs.getInstance(myProject));

    panel.add(myRepositoryBrowser, gc);
    return panel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myRepositoryBrowser;
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

  protected class RefreshAction extends AnAction {
    public void update(AnActionEvent e) {
      e.getPresentation().setText(SvnBundle.message("action.name.refresh"));
      e.getPresentation().setIcon(IconLoader.findIcon("/actions/sync.png"));
      e.getPresentation().setEnabled(myRepositoryBrowser.getSelectedNode() != null);
    }
    public void actionPerformed(AnActionEvent e) {
      myRepositoryBrowser.getSelectedNode().reload();
    }
  }
  protected class AddLocationAction extends AnAction {

    public AddLocationAction() {
      super("_Repository Location...");
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setDescription("Add Repository Location");
      e.getPresentation().setText("Add Repository Location");
      e.getPresentation().setIcon(IconLoader.findIcon("/general/add.png"));
      super.update(e);
    }

    public void actionPerformed(AnActionEvent e) {
      String url = Messages.showInputDialog(myProject, "Repository URL:", "New Repository Location", null, "http://", new InputValidator() {
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
        SvnConfiguration config = SvnConfiguration.getInstance(myVCS.getProject());
        config.addCheckoutURL(url);
        myRepositoryBrowser.addURL(url);
      }
    }
  }

  protected class DiscardLocationAction extends AnAction {
    public void update(AnActionEvent e) {
      RepositoryTreeNode node = myRepositoryBrowser.getSelectedNode();
      e.getPresentation().setText("Discard _Location", true);
      e.getPresentation().setEnabled(node != null && node.getParent() instanceof RepositoryTreeRootNode);
    }

    public void actionPerformed(AnActionEvent e) {
      RepositoryTreeNode node = myRepositoryBrowser.getSelectedNode();
      SVNURL url = node.getURL();
      if (url != null) {
        SvnConfiguration config = SvnConfiguration.getInstance(myVCS.getProject());
        config.removeCheckoutURL(url.toString());
        myRepositoryBrowser.removeURL(url.toString());
      }
    }
  }
  protected class MkDirAction extends AnAction {
    public void update(AnActionEvent e) {
      RepositoryTreeNode node = myRepositoryBrowser.getSelectedNode();
      e.getPresentation().setText("Remote _Folder...", true);
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
      Project p = (Project) e.getDataContext().getData(DataConstants.PROJECT);
      MkdirOptionsDialog dialog = new MkdirOptionsDialog(p, myRepositoryBrowser.getSelectedNode().getURL());
      dialog.show();
      if (dialog.isOK()) {
        SVNURL url = dialog.getURL();
        String message = dialog.getCommitMessage();
        doMkdir(url, message);
        myRepositoryBrowser.getSelectedNode().reload();
      }
    }
  }
  protected class CopyAction extends AnAction {
    public void update(AnActionEvent e) {
      e.getPresentation().setText("Branch or Tag...");
      RepositoryTreeNode node = myRepositoryBrowser.getSelectedNode();
      e.getPresentation().setEnabled(node != null && node.getSVNDirEntry() != null);
    }
    public void actionPerformed(AnActionEvent e) {
      Project p = (Project) e.getDataContext().getData(DataConstants.PROJECT);
      SVNURL root;
      RepositoryTreeNode node = myRepositoryBrowser.getSelectedNode();
      while (node.getSVNDirEntry() != null) {
        node = (RepositoryTreeNode) node.getParent();
      }
      root = node.getURL();
      CopyOptionsDialog dialog = new CopyOptionsDialog("Branch or Tag", p, root, myRepositoryBrowser.getSelectedNode().getURL());
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
      RepositoryTreeNode node = myRepositoryBrowser.getSelectedNode();
      e.getPresentation().setEnabled(node != null && node.getSVNDirEntry() != null);
    }

    public void actionPerformed(AnActionEvent e) {
      Project p = (Project) e.getDataContext().getData(DataConstants.PROJECT);
      SVNURL root;
      RepositoryTreeNode node = myRepositoryBrowser.getSelectedNode();
      while (node.getSVNDirEntry() != null) {
        node = (RepositoryTreeNode) node.getParent();
      }
      root = node.getURL();
      CopyOptionsDialog dialog = new CopyOptionsDialog("Move or Rename", p, root, myRepositoryBrowser.getSelectedNode().getURL());
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
      RepositoryTreeNode node = myRepositoryBrowser.getSelectedNode();
      e.getPresentation().setEnabled(node != null && node.getSVNDirEntry() != null);
    }

    public void actionPerformed(AnActionEvent e) {
      Project p = (Project) e.getDataContext().getData(DataConstants.PROJECT);
      DeleteOptionsDialog dialog = new DeleteOptionsDialog(p);
      RepositoryTreeNode node = myRepositoryBrowser.getSelectedNode();
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
      e.getPresentation().setText("_Import...");
      RepositoryTreeNode node = myRepositoryBrowser.getSelectedNode();
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
      e.getPresentation().setEnabled(myRepositoryBrowser.getSelectedNode() != null);
    }
    public void actionPerformed(AnActionEvent e) {
      SVNURL url = myRepositoryBrowser.getSelectedNode().getURL();
      final File dir = selectFile("Destination directory", "Select export destination directory");
      if (dir == null) {
        return;
      }
      Project p = (Project) e.getDataContext().getData(DataConstants.PROJECT);
      ExportOptionsDialog dialog = new ExportOptionsDialog(p, url, dir);
      dialog.show();
      if (dialog.isOK()) {
        SvnCheckoutProvider.doExport(myVCS.getProject(), dir, url.toString(), dialog.isRecursive(),
                dialog.isIgnoreExternals(), dialog.isForce(), dialog.getEOLStyle());
      }
    }
  }
  protected class CheckoutAction extends AnAction {
    public void update(AnActionEvent e) {
      e.getPresentation().setText("_Checkout...", true);
      RepositoryTreeNode node = myRepositoryBrowser.getSelectedNode();
      if (node != null) {
        SVNDirEntry entry = node.getSVNDirEntry();
        e.getPresentation().setEnabled(entry == null || entry.getKind() == SVNNodeKind.DIR);
      } else {
        e.getPresentation().setEnabled(false);
      }
    }
    public void actionPerformed(AnActionEvent e) {
      doCheckout();
    }
  }

  protected class DetailsAction extends ToggleAction {

    private boolean myIsSelected;

    public void update(final AnActionEvent e) {
      e.getPresentation().setDescription("Show/Hide Details");
      e.getPresentation().setText("Show/Hide Details");
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
      myRepositoryBrowser.getRepositoryTree().setCellRenderer(r);
    }
  }

  private File selectFile(String title, String description) {
    FileChooserDescriptor fcd = new FileChooserDescriptor(false, true, false, false, false, false);
    fcd.setShowFileSystemRoots(true);
    fcd.setTitle(title);
    fcd.setDescription(description);
    fcd.setHideIgnored(false);
    VirtualFile[] files = FileChooser.chooseFiles(myRepositoryBrowser, fcd, null);
    if (files == null || files.length != 1 || files[0] == null) {
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


  protected void doCheckout() {
    SVNURL url = myRepositoryBrowser.getSelectedNode().getURL();
    final File dir = selectFile("Destination directory", "Select checkout destination directory");
    if (dir == null) {
      return;
    }
    Project p = myProject;
    CheckoutOptionsDialog dialog = new CheckoutOptionsDialog(p, url, dir);
    dialog.show();
    if (dialog.isOK()) {
      SvnCheckoutProvider.doCheckout(myVCS.getProject(), dir, url.toString(), dialog.isRecursive(), dialog.isIgnoreExternals());
    }
  }

  protected void doImport() {
    File dir = selectFile("Import Directory", "Select directory to import into repository");
    if (dir == null) {
      return;
    }

    SVNURL url = myRepositoryBrowser.getSelectedNode().getURL();
    ImportOptionsDialog dialog = new ImportOptionsDialog(myProject, url, dir);
    dialog.show();
    if (dialog.isOK()) {
      File src = dialog.getTarget();
      boolean recursive = dialog.isRecursive();
      boolean ignored = dialog.isIncludeIgnored();
      String message = dialog.getCommitMessage();
      SvnCheckoutProvider.doImport(myProject, src, url, recursive, ignored, message);
      myRepositoryBrowser.getSelectedNode().reload();

    }
  }

}
