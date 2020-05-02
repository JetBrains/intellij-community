// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.ChangeListViewerDialog;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.IconUtil;
import com.intellij.util.WaitForProgressToShow;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.idea.svn.SvnApplicationSettings;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.browse.DirectoryEntry;
import org.jetbrains.idea.svn.checkout.SvnCheckoutProvider;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.dialogs.browser.*;
import org.jetbrains.idea.svn.dialogs.browserCache.Expander;
import org.jetbrains.idea.svn.dialogs.browserCache.KeepingExpandedExpander;
import org.jetbrains.idea.svn.dialogs.browserCache.SyntheticWorker;
import org.jetbrains.idea.svn.history.SvnRepositoryLocation;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import static com.intellij.util.ArrayUtil.isEmpty;
import static com.intellij.util.ui.JBUI.size;
import static org.jetbrains.idea.svn.SvnUtil.createUrl;
import static org.jetbrains.idea.svn.SvnUtil.getRelativeUrl;

public class RepositoryBrowserDialog extends DialogWrapper {

  private static final Logger LOG = Logger.getInstance(RepositoryBrowserDialog.class);

  private final Project myProject;
  protected final SvnVcs myVCS;
  @NotNull private final RepositoryBrowserComponent myRepositoryBrowser;

  private final DeleteAction myDeleteAction;
  private AnAction copyUrlAction;
  private AnAction mkDirAction;

  private final boolean myShowFiles;

  @NonNls public static final String PLACE_TOOLBAR = "RepositoryBrowser.Toolbar";
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

    myRepositoryBrowser = new RepositoryBrowserComponent(myVCS);
    myRepositoryBrowser.setPreferredSize(size(300, 300));

    setTitle("SVN Repository Browser");
    setResizable(true);
    setOKButtonText(CommonBundle.getCloseButtonText());
    getHelpAction().setEnabled(true);
    Disposer.register(project, getDisposable());
    myDeleteAction = new DeleteAction(getRepositoryBrowser());
    init();
  }

  @Override
  protected String getHelpId() {
    return "reference.svn.repository";
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[] {getOKAction(), getHelpAction()};
  }

  @Override
  protected String getDimensionServiceKey() {
    return "svn.repositoryBrowser";
  }

  protected boolean showImportAction() {
    return true;
  }

  public JComponent createToolbar(final boolean horizontal, final AnAction... additionalActions) {
    DefaultActionGroup group = new DefaultActionGroup();
    final RepositoryBrowserComponent browser = getRepositoryBrowser();
    group.add(new AddLocationAction(browser));
    group.add(new EditLocationAction(browser));
    group.add(new DiscardLocationAction(browser));
    group.add(new DetailsAction());
    group.addSeparator();
    final RefreshAction refreshAction = new RefreshAction(browser);
    refreshAction.registerCustomShortcutSet(CommonShortcuts.getRerun(), browser);
    group.add(refreshAction);

    copyUrlAction = new CopyUrlAction();
    copyUrlAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT,
                                                              InputEvent.CTRL_MASK | InputEvent.CTRL_DOWN_MASK |
                                                              InputEvent.ALT_MASK | InputEvent.ALT_DOWN_MASK)), browser);
    mkDirAction = new MkDirAction(browser);
    mkDirAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT,
                                                              InputEvent.ALT_MASK | InputEvent.ALT_DOWN_MASK)), browser);

    AnAction action = CommonActionsManager.getInstance().createCollapseAllAction(new TreeExpander() {
      @Override
      public boolean canExpand() {
        return false;
      }

      @Override
      public void collapseAll() {
        JTree tree = browser.getRepositoryTree();
        int row = tree.getRowCount() - 1;
        while (row >= 0) {
          tree.collapseRow(row);
          row--;
        }
      }

      @Override
      public boolean canCollapse() {
        return true;
      }
    }, browser);
    group.add(action);

    if (!isEmpty(additionalActions)) {
      group.addSeparator();
      group.addAll(additionalActions);
    }
    return ActionManager.getInstance().createActionToolbar(PLACE_TOOLBAR, group, horizontal).getComponent();
  }

  protected JPopupMenu createPopup(boolean toolWindow) {
    ActionManager actionManager = ActionManager.getInstance();
    DefaultActionGroup group = new DefaultActionGroup();
    DefaultActionGroup newGroup = DefaultActionGroup.createPopupGroup(() -> "_New");
    final RepositoryBrowserComponent browser = getRepositoryBrowser();
    newGroup.add(new AddLocationAction(browser));
    newGroup.add(new MkDirAction(browser));
    group.add(newGroup);
    group.addSeparator();
    if (toolWindow) {
      group.add(actionManager.getAction(IdeActions.ACTION_EDIT_SOURCE));
      group.add(new HistoryAction());
    }
    group.add(new CheckoutAction());
    group.add(new DiffAction());
    group.add(new BrowseCommittedChangesAction());
    group.addSeparator();
    group.add(new ImportAction());
    group.add(new ExportAction());
    group.addSeparator();
    group.add(
      new CopyOrMoveAction(SvnBundle.messagePointer("action.DumbAware.RepositoryBrowserDialog.text.branch.or.tag"), "copy.dialog.title",
                           false));
    group.add(
      new CopyOrMoveAction(SvnBundle.messagePointer("action.DumbAware.RepositoryBrowserDialog.text.move.or.rename"), "move.dialog.title",
                           true));
    group.add(myDeleteAction);
    group.add(copyUrlAction);
    group.addSeparator();
    group.add(new RefreshAction(browser));
    group.add(new EditLocationAction(browser));
    group.add(new DiscardLocationAction(browser));
    ActionPopupMenu menu = actionManager.createActionPopupMenu(PLACE_MENU, group);
    return menu.getComponent();
  }

  @Override
  public JComponent createCenterPanel() {
    JPanel parentPanel = new JPanel(new BorderLayout());
    JPanel top = new JPanel();
    final BoxLayout layout = new BoxLayout(top, BoxLayout.X_AXIS);
    top.setLayout(layout);

    myRepositoriesLabel = new JLabel(myRepositoriesLabelText);
    top.add(myRepositoriesLabel);
    top.add(createToolbar(true, (AnAction[])null));
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
    ArrayList<Url> svnURLs = new ArrayList<>();
    for (final String url : urls) {
      try {
        svnURLs.add(createUrl(url));
      }
      catch (SvnBindException ignored) {
      }
    }
    getRepositoryBrowser().setRepositoryURLs(svnURLs.toArray(new Url[0]), myShowFiles);
    getRepositoryBrowser().getRepositoryTree().addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        JTree tree = getRepositoryBrowser().getRepositoryTree();
        int row = tree.getRowForLocation(x, y);
        if (row >= 0) {
          tree.setSelectionRow(row);
        }
        JPopupMenu popupMenu = createPopup(toolWindow);
        if (popupMenu != null) {
          popupMenu.show(comp, x, y);
        }
      }
    });
    getRepositoryBrowser().getStatusText()
      .clear()
      .appendText(SvnBundle.message("repository.browser.no.locations.added.info"))
      .appendSecondaryText(SvnBundle.message("repository.browser.add.location.action.description"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                           e -> addLocation(getRepositoryBrowser()));
    return panel;
  }

  @NotNull
  protected RepositoryBrowserComponent getRepositoryBrowser() {
    return myRepositoryBrowser;
  }

  public void disposeRepositoryBrowser() {
    Disposer.dispose(myRepositoryBrowser);
  }

  @Override
  protected void dispose() {
    super.dispose();
    disposeRepositoryBrowser();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return (JComponent) getRepositoryBrowser().getPreferredFocusedComponent();
  }

  @Override
  public boolean shouldCloseOnCross() {
    return true;
  }

  @Override
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

  protected class HistoryAction extends DumbAwareAction {
    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setText(SvnBundle.messagePointer("repository.browser.history.action"));
      e.getPresentation().setDescription(SvnBundle.messagePointer("repository.browser.history.action"));
      final RepositoryTreeNode node = getRepositoryBrowser().getSelectedNode();
      e.getPresentation().setEnabled(node != null && node.getURL() != null && !myProject.isDefault());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final RepositoryTreeNode node = getSelectedNode();
      if (node == null) {
        return;
      }
      boolean isDirectory =
        node.getUserObject() instanceof Url || (node.getSVNDirEntry() != null && node.getSVNDirEntry().isDirectory());
      String url = node.getURL().toDecodedString();

      AbstractVcsHelper.getInstance(myProject)
        .showFileHistory(myVCS.getVcsHistoryProvider(), VcsUtil.getFilePathOnNonLocal(url, isDirectory), myVCS);
      node.reload(false);
    }
  }

  public static class RefreshAction extends DumbAwareAction {
    private final RepositoryBrowserComponent myBrowserComponent;

    public RefreshAction(final RepositoryBrowserComponent browserComponent) {
      super(SvnBundle.message("action.name.refresh"), SvnBundle.message("repository.browser.refresh.action"), AllIcons.Actions.Refresh);
      myBrowserComponent = browserComponent;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setText(SvnBundle.messagePointer("action.name.refresh"));
      e.getPresentation().setDescription(SvnBundle.messagePointer("repository.browser.refresh.action"));
      e.getPresentation().setIcon(AllIcons.Actions.Refresh);
      e.getPresentation().setEnabled(myBrowserComponent.getSelectedNode() != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final RepositoryTreeNode selectedNode = myBrowserComponent.getSelectedNode();
      if (selectedNode != null) {
        selectedNode.reload(true);
      }
    }
  }

  protected static class AddLocationAction extends DumbAwareAction {

    private final RepositoryBrowserComponent myBrowserComponent;

    public AddLocationAction(final RepositoryBrowserComponent browserComponent) {
      super(SvnBundle.message("repository.browser.add.location.menu.item"));
      myBrowserComponent = browserComponent;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (e.getPlace().equals(PLACE_TOOLBAR)) {
        e.getPresentation().setDescription(SvnBundle.messagePointer("repository.browser.add.location.action.description"));
        e.getPresentation().setText(SvnBundle.messagePointer("repository.browser.add.location.action.text"));
        e.getPresentation().setIcon(IconUtil.getAddIcon());
      }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      addLocation(myBrowserComponent);
    }
  }

  private static void addLocation(@NotNull RepositoryBrowserComponent browserComponent) {
    final SvnApplicationSettings settings = SvnApplicationSettings.getInstance();
    final AddRepositoryLocationDialog dialog =
      new AddRepositoryLocationDialog(browserComponent.getProject(), settings.getTypedUrlsListCopy());
    dialog.show();
    if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      Url url = dialog.getSelected();
      if (url != null) {
        settings.addTypedUrl(url.toDecodedString());
        settings.addCheckoutURL(url.toDecodedString());
        browserComponent.addURL(url);
      }
    }
  }

  protected static class EditLocationAction extends DumbAwareAction {

    @NotNull private final RepositoryBrowserComponent myBrowserComponent;

    public EditLocationAction(@NotNull RepositoryBrowserComponent browserComponent) {
      super(SvnBundle.message("repository.browser.edit.location.menu.item"));
      myBrowserComponent = browserComponent;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      RepositoryTreeNode node = myBrowserComponent.getSelectedNode();
      if (e.getPlace().equals(PLACE_TOOLBAR)) {
        e.getPresentation().setDescription(SvnBundle.messagePointer("repository.browser.edit.location.menu.item"));
        e.getPresentation().setText(SvnBundle.messagePointer("repository.browser.edit.location.menu.item"));
        e.getPresentation().setIcon(AllIcons.Actions.EditSource);
      }
      e.getPresentation().setEnabled(node != null && node.getParent() instanceof RepositoryTreeRootNode);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      RepositoryTreeNode node = myBrowserComponent.getSelectedNode();
      if (node == null || (! (node.getParent() instanceof RepositoryTreeRootNode))) {
        return;
      }
      Url oldUrl = node.getURL();
      final SvnApplicationSettings settings = SvnApplicationSettings.getInstance();
      final AddRepositoryLocationDialog dialog =
        new AddRepositoryLocationDialog(myBrowserComponent.getProject(), settings.getTypedUrlsListCopy()) {
        @Override
        protected String initText() {
          return oldUrl.toDecodedString();
        }

        @Override
        public String getTitle() {
          return SvnBundle.message("repository.browser.edit.location.dialog.title");
        }
      };
      dialog.show();
      if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
        Url url = dialog.getSelected();
        if (url != null) {
          settings.addTypedUrl(url.toDecodedString());
          settings.removeCheckoutURL(oldUrl.toString());
          settings.addCheckoutURL(url.toDecodedString());

          myBrowserComponent.removeURL(oldUrl);
          myBrowserComponent.addURL(url);
        }
      }
    }
  }

  protected static class DiscardLocationAction extends DumbAwareAction {
    private final RepositoryBrowserComponent myBrowserComponent;

    public DiscardLocationAction(final RepositoryBrowserComponent browserComponent) {
      super(SvnBundle.message("repository.browser.discard.location.action"), SvnBundle.message("repository.browser.discard.location.action"), AllIcons.General.Remove);
      myBrowserComponent = browserComponent;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      RepositoryTreeNode node = myBrowserComponent.getSelectedNode();
      e.getPresentation().setText(SvnBundle.message("repository.browser.discard.location.action"), true);
      e.getPresentation().setIcon(AllIcons.General.Remove);
      e.getPresentation().setEnabled(node != null && node.getParent() instanceof RepositoryTreeRootNode);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      RepositoryTreeNode node = myBrowserComponent.getSelectedNode();
      if (node == null) {
        return;
      }
      Url url = node.getURL();
      if (url != null) {
        int rc = Messages.showYesNoDialog(myBrowserComponent.getProject(), SvnBundle.message("repository.browser.discard.location.prompt", url.toString()),
                                          SvnBundle.message("repository.browser.discard.location.title"), Messages.getQuestionIcon());
        if (rc != Messages.YES) {
          return;
        }
        SvnApplicationSettings.getInstance().removeCheckoutURL(url.toString());
        myBrowserComponent.removeURL(url);
      }
    }
  }

  public static class MkDirAction extends DumbAwareAction {
    private final RepositoryBrowserComponent myBrowserComponent;

    public MkDirAction(final RepositoryBrowserComponent browserComponent) {
      super(SvnBundle.message("repository.browser.new.folder.action"));
      myBrowserComponent = browserComponent;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      //e.getPresentation().setText(SvnBundle.message("repository.browser.new.folder.action"), true);
      setEnabled(e, myBrowserComponent.getSelectedNode());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      // show dialog for comment and folder name, then create folder
      // then refresh selected node.
      final RepositoryTreeNode node = myBrowserComponent.getSelectedNode();
      if (node == null) {
        return;
      }
      final Project project = myBrowserComponent.getProject();
      MkdirOptionsDialog dialog = new MkdirOptionsDialog(project, node.getURL());
      dialog.show();
      VcsConfiguration.getInstance(project).saveCommitMessage(dialog.getCommitMessage());
      if (dialog.isOK()) {
        Url url = dialog.getURL();
        String message = dialog.getCommitMessage();
        doMkdir(url, message, project);

        final Url repositoryUrl = (node.getSVNDirEntry() == null) ? node.getURL() : node.getSVNDirEntry().getRepositoryRoot();
        final SyntheticWorker worker = new SyntheticWorker(node.getURL());
        worker.addSyntheticChildToSelf(url, repositoryUrl, dialog.getName(), true);

        node.reload(false);
      }
    }
  }

  protected class DiffAction extends DumbAwareAction {
    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setText("Compare With...", true);
      setEnabled(e, getRepositoryBrowser().getSelectedNode());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      // show dialog for comment and folder name, then create folder
      // then refresh selected node.
      Url root;
      RepositoryTreeNode node = getRepositoryBrowser().getSelectedNode();
      if (node == null) {
        return;
      }
      while (node.getSVNDirEntry() != null) {
        node = (RepositoryTreeNode)node.getParent();
      }
      root = node.getURL();
      final RepositoryTreeNode selectedNode = getSelectedNode();
      if (selectedNode == null) {
        return;
      }
      Url sourceURL = selectedNode.getURL();
      DiffOptionsDialog dialog = new DiffOptionsDialog(myProject, root, sourceURL);
      if (dialog.showAndGet()) {
        Url targetURL = dialog.getTargetURL();
        if (dialog.isReverseDiff()) {
          targetURL = sourceURL;
          sourceURL = dialog.getTargetURL();
        }

        final Url sURL = sourceURL;
        final Url tURL = targetURL;

        Runnable command;
        boolean cancelable;
        if (dialog.isUnifiedDiff()) {
          final File targetFile = dialog.getTargetFile();
          command = () -> {
            targetFile.getParentFile().mkdirs();
            doUnifiedDiff(targetFile, sURL, tURL);
          };
          cancelable = false;
        }
        else {
          command = () -> {
            try {
              doGraphicalDiff(sURL, tURL);
            }
            catch (final VcsException ex) {
              WaitForProgressToShow
                .runOrInvokeLaterAboveProgress(() -> Messages.showErrorDialog(myProject, ex.getMessage(), "Error"), null, myProject);
            }
          };
          cancelable = true;
        }
        ProgressManager.getInstance().runProcessWithProgressSynchronously(command, SvnBundle.message("progress.computing.difference"),
                                                                          cancelable, myProject);
      }
    }
  }

  protected class CopyOrMoveAction extends DumbAwareAction {
    private final Supplier<String> myActionName;
    private final @PropertyKey(resourceBundle = SvnBundle.BUNDLE) String myDialogTitleKey;
    private final boolean myMove;

    public CopyOrMoveAction(final Supplier<String> actionName,
                            @PropertyKey(resourceBundle = SvnBundle.BUNDLE) String dialogTitleKey, final boolean move) {
      myActionName = actionName;
      myDialogTitleKey = dialogTitleKey;
      myMove = move;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setText(myActionName);
      RepositoryTreeNode node = getRepositoryBrowser().getSelectedNode();
      e.getPresentation().setEnabled(node != null && node.getSVNDirEntry() != null);
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
      final RepositoryTreeNode node = getSelectedNode();
      if (node == null) {
        return;
      }
      RepositoryTreeNode rootNode = node;
      while (! rootNode.isRepositoryRoot()) {
        rootNode = (RepositoryTreeNode) rootNode.getParent();
      }

      CopyOptionsDialog dialog = new CopyOptionsDialog(SvnBundle.message(myDialogTitleKey), myProject, rootNode, node, ! myMove);
      dialog.show();
      VcsConfiguration.getInstance(myProject).saveCommitMessage(dialog.getCommitMessage());
      if (dialog.isOK()) {
        Url dst = dialog.getTargetURL();
        Url src = dialog.getSourceURL();
        final String path = src.getPath();
        final int folder = path.replace('\\', '/').lastIndexOf('/');
        if (folder != -1) {
          final String lastFolder = path.substring(folder + 1);
          if (myMove && "trunk".equalsIgnoreCase(lastFolder)) {
            final int result =
              Messages.showOkCancelDialog(myProject, "You are about to move folder named '" + lastFolder +
                                                     "'. Are you sure?", SvnBundle.message(myDialogTitleKey), Messages.getWarningIcon());
            if (Messages.OK != result) return;
          }
        }
        String message = dialog.getCommitMessage();
        doCopy(src, dst, myMove, message);

        final CopyMoveReloadHelper sourceReloader = myMove ? new MoveSourceReloader(node) : CopyMoveReloadHelper.EMPTY;
        final TargetReloader destinationReloader = new TargetReloader(dialog, node, rootNode, getRepositoryBrowser());

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
    private final Url myDst;
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

    @Override
    public void doRefresh() {
      final TreeNode[] oldPath = myDialogParent.getSelfPath();

      myRoot.reload(new OpeningExpander(oldPath, myBrowserComponent, myDialogParent), false);
    }

    @Override
    public void doSynthetic() {
      final SyntheticWorker parentWorker = new SyntheticWorker(myDialogParent.getURL());
      parentWorker.addSyntheticChildToSelf(myDst, myRoot.getURL(), myNewName, ! mySourceNode.isLeaf());
      parentWorker.copyTreeToSelf(mySourceNode);
    }

    @Override
    public Url parent() {
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

    @Override
    public void doRefresh() {
      myParent.reload(false);
    }

    @Override
    public void doSynthetic() {
      final SyntheticWorker worker = new SyntheticWorker(mySource.getURL());
      worker.removeSelf();
    }

    @Override
    public Url parent() {
      return myParent.getURL();
    }
  }

  private interface CopyMoveReloadHelper {
    void doRefresh();
    void doSynthetic();
    @Nullable
    Url parent();

    CopyMoveReloadHelper EMPTY = new CopyMoveReloadHelper() {
      @Override
      public void doRefresh() {
      }
      @Override
      public void doSynthetic() {
      }
      @Override
      @Nullable
      public Url parent() {
        return null;
      }
    };
  }

  protected class CopyUrlAction extends DumbAwareAction {
    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setText("Copy URL...");
      RepositoryTreeNode node = getRepositoryBrowser().getSelectedNode();
      e.getPresentation().setEnabled(node != null);
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
      final RepositoryTreeNode treeNode = getRepositoryBrowser().getSelectedNode();
      if (treeNode != null) {
        final String url = treeNode.getURL().toString();
        CopyPasteManager.getInstance().setContents(new StringSelection(url));
      }
    }
  }

  public static class DeleteAction extends DumbAwareAction {
    private final RepositoryBrowserComponent myBrowserComponent;

    public DeleteAction(final RepositoryBrowserComponent browserComponent) {
      super("_Delete...");
      myBrowserComponent = browserComponent;
      registerCustomShortcutSet(CommonShortcuts.getDelete(), myBrowserComponent);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      RepositoryTreeNode node = myBrowserComponent.getSelectedNode();
      e.getPresentation().setEnabled(node != null && node.getSVNDirEntry() != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      DeleteOptionsDialog dialog = new DeleteOptionsDialog(myBrowserComponent.getProject());
      RepositoryTreeNode node = myBrowserComponent.getSelectedNode();
      dialog.show();
      VcsConfiguration.getInstance(myBrowserComponent.getProject()).saveCommitMessage(dialog.getCommitMessage());
      if (dialog.isOK()) {
        Url url = node.getURL();
        String message = dialog.getCommitMessage();
        final boolean successful = doDelete(url, message);

        if (successful) {
          final SyntheticWorker worker = new SyntheticWorker(url);
          worker.removeSelf();
          final RepositoryTreeNode parentNode = (RepositoryTreeNode) node.getParent();
          parentNode.reload(new KeepingExpandedExpander(myBrowserComponent, new AfterDeletionSelectionInstaller(node, myBrowserComponent)), false);
        }
      }
    }
    private boolean doDelete(final Url url, final String comment) {
      final Ref<Exception> exception = new Ref<>();
      final Project project = myBrowserComponent.getProject();
      Runnable command = () -> {
        ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
        if (progress != null) {
          progress.setText(SvnBundle.message("progres.text.deleting", url.toString()));
        }
        SvnVcs vcs = SvnVcs.getInstance(project);
        try {
          vcs.getFactoryFromSettings().createDeleteClient().delete(url, comment);
        }
        catch (VcsException e) {
          exception.set(e);
        }
      };
      ProgressManager.getInstance().runProcessWithProgressSynchronously(command, SvnBundle.message("progress.title.browser.delete"), false, project);
      if (!exception.isNull()) {
        Messages.showErrorDialog(exception.get().getMessage(), SvnBundle.message("message.text.error"));
      }
      return exception.isNull();
    }
  }

  private static class AfterDeletionSelectionInstaller implements Expander {
    private final RepositoryTreeNode myParentNode;
    private final String myDeletedNodeName;
    private final boolean myIsFolder;
    private final RepositoryBrowserComponent myBrowserComponent;

    private AfterDeletionSelectionInstaller(final RepositoryTreeNode deletedNode, final RepositoryBrowserComponent browserComponent) {
      myBrowserComponent = browserComponent;
      myParentNode = (RepositoryTreeNode) deletedNode.getParent();
      myDeletedNodeName = deletedNode.toString();
      myIsFolder = ! deletedNode.isLeaf();
    }

    @Override
    public void onBeforeRefresh(final RepositoryTreeNode node) {
    }

    @Override
    public void onAfterRefresh(final RepositoryTreeNode node) {
      TreeNode nodeToSelect = myParentNode.getNextChildByKey(myDeletedNodeName, myIsFolder);
      nodeToSelect = (nodeToSelect == null) ? myParentNode : nodeToSelect;
      myBrowserComponent.setSelectedNode(nodeToSelect);
    }
  }

  protected class ImportAction extends DumbAwareAction {
    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setVisible(showImportAction());
      e.getPresentation().setText(SvnBundle.messagePointer("repository.browser.import.action"));
      setEnabled(e, getRepositoryBrowser().getSelectedNode(),
                 ProjectLevelVcsManager.getInstance(myProject).isBackgroundVcsOperationRunning());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      // get directory, then import.
      doImport();
    }
  }

  protected class ExportAction extends DumbAwareAction {
    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setText("_Export...");
      e.getPresentation().setEnabled(getRepositoryBrowser().getSelectedNode() != null);
    }
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final RepositoryTreeNode selectedNode = getSelectedNode();
      if (selectedNode == null) {
        return;
      }
      Url url = selectedNode.getURL();
      final File dir = selectFile("Destination directory", "Select export destination directory");
      if (dir == null) {
        return;
      }
      Project p = e.getData(CommonDataKeys.PROJECT);
      ExportOptionsDialog dialog = new ExportOptionsDialog(p, url, dir);
      if (dialog.showAndGet()) {
        SvnCheckoutProvider.doExport(myProject, dir, url, dialog.getDepth(),
                                     dialog.isIgnoreExternals(), dialog.isForce(), dialog.getEOLStyle());
      }
    }
  }

  protected class CheckoutAction extends DumbAwareAction {
    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setText("_Checkout...", true);
      setEnabled(e, getRepositoryBrowser().getSelectedNode());
    }
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final RepositoryTreeNode selectedNode = getSelectedNode();
      if (! ModalityState.NON_MODAL.equals(ModalityState.current())) {
        doCancelAction();
      }
      doCheckout(ProjectLevelVcsManager.getInstance(myProject).getCompositeCheckoutListener(), selectedNode);
    }
  }

  private static void setEnabled(@NotNull AnActionEvent e, @Nullable RepositoryTreeNode node) {
    setEnabled(e, node, false);
  }

  private static void setEnabled(@NotNull AnActionEvent e, @Nullable RepositoryTreeNode node, boolean isRunning) {
    e.getPresentation().setEnabled(node != null && (node.getSVNDirEntry() == null || node.getSVNDirEntry().isDirectory()) && !isRunning);
  }

  protected class BrowseCommittedChangesAction extends DumbAwareAction {
    public BrowseCommittedChangesAction() {
      super(SvnBundle.messagePointer("repository.browser.browse.changes.action"),
            SvnBundle.messagePointer("repository.browser.browse.changes.description"), null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      RepositoryTreeNode node = getSelectedNode();
      if (node == null) {
        return;
      }
      Url url = node.getURL();
      AbstractVcsHelper.getInstance(myProject).showCommittedChangesBrowser(
        myVCS.getCommittedChangesProvider(), new SvnRepositoryLocation(url), "Changes in " + url.toString(), null);
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
      e.getPresentation().setEnabled(getRepositoryBrowser().getSelectedNode() != null);
    }
  }

  protected class DetailsAction extends DumbAwareToggleAction {

    private boolean myIsSelected;

    @Override
    public void update(@NotNull final AnActionEvent e) {
      e.getPresentation().setDescription(SvnBundle.messagePointer("repository.browser.details.action"));
      e.getPresentation().setText(SvnBundle.messagePointer("repository.browser.details.action"));
      e.getPresentation().setIcon(AllIcons.Actions.Annotate);
      super.update(e);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myIsSelected;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myIsSelected = state;
      SvnRepositoryTreeCellRenderer r = new SvnRepositoryTreeCellRenderer();
      r.setShowDetails(state);
      getRepositoryBrowser().getRepositoryTree().setCellRenderer(r);
    }
  }

  @Nullable
  private File selectFile(String title, String description) {
    FileChooserDescriptor fcd = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    fcd.setShowFileSystemRoots(true);
    fcd.setTitle(title);
    fcd.setDescription(description);
    fcd.setHideIgnored(false);
    VirtualFile file = FileChooser.chooseFile(fcd, myProject, null);
    if (file == null) {
      return null;
    }
    final String path = file.getPath();
    if (path.endsWith(":")) {   // workaround for VFS oddities with drive root (IDEADEV-20870)
      return new File(path + "/");
    }
    return new File(path);
  }

  protected static void doMkdir(final Url url, final String comment, final Project project) {
    final Ref<Exception> exception = new Ref<>();
    Runnable command = () -> {
      ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
      if (progress != null) {
        progress.setText(SvnBundle.message("progress.text.browser.creating", url.toString()));
      }
      SvnVcs vcs = SvnVcs.getInstance(project);
      Target target = Target.on(url);
      try {
        vcs.getFactoryFromSettings().createBrowseClient().createDirectory(target, comment, false);
      }
      catch (VcsException e) {
        exception.set(e);
      }
    };
    ProgressManager.getInstance().runProcessWithProgressSynchronously(command, SvnBundle.message("progress.text.create.remote.folder"), false, project);
    if (!exception.isNull()) {
      Messages.showErrorDialog(exception.get().getMessage(), SvnBundle.message("message.text.error"));
    }
  }

  private void doCopy(final Url src, final Url dst, final boolean move, final String comment) {
    final Ref<Exception> exception = new Ref<>();
    Runnable command = () -> {
      ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
      if (progress != null) {
        progress.setText(
          (move ? SvnBundle.message("progress.text.browser.moving", src) : SvnBundle.message("progress.text.browser.copying", src)));
        progress.setText2(SvnBundle.message("progress.text.browser.remote.destination", dst));
      }
      SvnVcs vcs = SvnVcs.getInstance(myProject);
      try {
        vcs.getFactoryFromSettings().createCopyMoveClient().copy(Target.on(src), Target.on(dst), Revision.HEAD, true,
                                                                 move, comment, null);
      }
      catch (VcsException e) {
        exception.set(e);
      }
    };
    String progressTitle = move ? SvnBundle.message("progress.title.browser.move") : SvnBundle.message("progress.title.browser.copy");
    ProgressManager.getInstance().runProcessWithProgressSynchronously(command, progressTitle, false, myProject);
    if (!exception.isNull()) {
      Messages.showErrorDialog(exception.get().getMessage(), SvnBundle.message("message.text.error"));
    }
  }

  protected void doCheckout(@Nullable final CheckoutProvider.Listener listener, final RepositoryTreeNode selectedNode) {
    if (selectedNode == null) {
      return;
    }
    Url url = selectedNode.getURL();

    String relativePath = "";
    final DirectoryEntry entry = selectedNode.getSVNDirEntry();
    if (entry != null) {
      if (entry.getRepositoryRoot() != null) {
        if (! entry.getRepositoryRoot().equals(url)) {
          relativePath = getRelativeUrl(entry.getRepositoryRoot(), url);
        }
      } else {
        relativePath = entry.getRelativePath();
      }
    } else {
      relativePath = url.getPath();
    }

    File dir = selectFile(SvnBundle.message("svn.checkout.destination.directory.title"),
                          SvnBundle.message("svn.checkout.destination.directory.description"));
    if (dir == null) {
      return;
    }

    CheckoutOptionsDialog dialog = new CheckoutOptionsDialog(myProject, url, dir, SvnUtil.getVirtualFile(dir.getAbsolutePath()), relativePath);
    dialog.show();
    dir = dialog.getTarget();
    if (dialog.isOK() && dir != null) {
      final Revision revision;
        try {
          revision =  dialog.getRevision();
        } catch (ConfigurationException e) {
          Messages.showErrorDialog(SvnBundle.message("message.text.cannot.checkout", e.getMessage()), SvnBundle.message("message.title.check.out"));
          return;
        }

      SvnCheckoutProvider.doCheckout(myProject, dir, url, revision, dialog.getDepth(), dialog.isIgnoreExternals(), listener);
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
    Url url = selectedNode.getURL();
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

  private void doUnifiedDiff(File targetFile, Url sourceURL, Url targetURL) {
    OutputStream os = null;
    try {
      os = new BufferedOutputStream(new FileOutputStream(targetFile));
      myVCS.getFactoryFromSettings().createDiffClient().unifiedDiff(Target.on(sourceURL, Revision.HEAD),
                                                                    Target.on(targetURL, Revision.HEAD), os);
    }
    catch (IOException | VcsException e) {
      LOG.info(e);
    }
    finally {
      if (os != null) {
        try {
          os.close();
        } catch (IOException e) {
          LOG.info(e);
        }
      }
    }
  }

  private void doGraphicalDiff(Url sourceURL, Url targetURL) throws VcsException {
    List<Change> changes =
      myVCS.getFactoryFromSettings().createDiffClient().compare(Target.on(sourceURL), Target.on(targetURL));

    showDiffEditorResults(changes, sourceURL.getTail(), targetURL.getTail());
  }

  private void showDiffEditorResults(final Collection<Change> changes, String sourceTitle, String targetTitle) {
    final String title = SvnBundle.message("repository.browser.compare.title", sourceTitle, targetTitle);
    SwingUtilities.invokeLater(() -> {
      final ChangeListViewerDialog dlg = new ChangeListViewerDialog(getRepositoryBrowser(), myProject, changes);
      dlg.markChangesInAir(true);
      dlg.setTitle(title);
      dlg.show();
    });
  }
}
