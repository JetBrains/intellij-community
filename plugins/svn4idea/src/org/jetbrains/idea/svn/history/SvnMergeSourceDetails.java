// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vcs.changes.ui.CommittedChangeListPanel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ContentsUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.util.text.StringUtil.ELLIPSIS;
import static com.intellij.openapi.util.text.StringUtil.notNullize;
import static com.intellij.openapi.vcs.changes.committed.CommittedChangeListRenderer.getDescriptionOfChangeList;
import static com.intellij.openapi.vcs.changes.committed.CommittedChangeListRenderer.truncateDescription;
import static com.intellij.util.text.DateFormatUtil.formatPrettyDateTime;

public final class SvnMergeSourceDetails extends MasterDetailsComponent {
  private final Project myProject;
  private final SvnFileRevision myRevision;
  private final VirtualFile myFile;
  private final Map<Long, SvnChangeList> myListsMap;

  private SvnMergeSourceDetails(final Project project, final SvnFileRevision revision, final VirtualFile file) {
    myProject = project;
    myRevision = revision;
    myFile = file;
    myListsMap = new HashMap<>();
    initTree();
    fillTree();

    getSplitter().setProportion(0.5f);
  }

  public static void showMe(final Project project, final SvnFileRevision revision, final VirtualFile file) {
    if (ModalityState.NON_MODAL.equals(ModalityState.current())) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
    final ContentManager contentManager = toolWindow.getContentManager();

    final MyDialog dialog = new MyDialog(project, revision, file);
    // TODO: Temporary memory leak fix - rewrite this part not to create dialog if only createCenterPanel(), but not show() is invoked
    Disposer.register(project, dialog.getDisposable());

    Content content = ContentFactory.getInstance().createContent(dialog.createCenterPanel(),
        SvnBundle.message("merge.source.details.title", (file == null) ? revision.getURL().toDecodedString() : file.getName(), revision.getRevisionNumber().asString()), true);
    ContentsUtil.addOrReplaceContent(contentManager, content, true);

    toolWindow.activate(null);
    } else {
      new MyDialog(project, revision, file).show();
    }
  }

  @Override
  @Nls
  public String getDisplayName() {
    return null;
  }

  @Override
  public String getHelpTopic() {
    return null;
  }

  private void addRecursively(final SvnFileRevision revision, final MyNode node, final List<TreePath> nodesToExpand) {
    final MyNode current = new MyNode(new MyNamedConfigurable(revision, myFile, myProject, myListsMap));
    node.add(current);
    final TreeNode[] path = ((DefaultTreeModel)myTree.getModel()).getPathToRoot(node);
    nodesToExpand.add(new TreePath(path));
    final List<SvnFileRevision> mergeSources = revision.getMergeSources();
    for (SvnFileRevision source : mergeSources) {
      addRecursively(source, current, nodesToExpand);
    }
  }

  private class MyTreeCellRenderer extends ColoredTreeCellRenderer {
    private final static int ourMaxWidth = 100;

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      final Object value,
                                      final boolean selected,
                                      final boolean expanded,
                                      final boolean leaf,
                                      final int row,
                                      final boolean hasFocus) {
      final FontMetrics metrics = tree.getFontMetrics(tree.getFont());
      final SvnFileRevision revision;
      if (value instanceof MyRootNode) {
        revision = myRevision;
      }
      else {
        final MyNode myNode = (MyNode)value;
        final MyNamedConfigurable configurable = (MyNamedConfigurable)myNode.getConfigurable();
        revision = configurable.getRevision();
      }

      String description = getDescriptionOfChangeList(revision.getCommitMessage());
      if (metrics.stringWidth(description) > ourMaxWidth) {
        description = truncateDescription(description, metrics, ourMaxWidth - metrics.stringWidth(getTruncatedSuffix()));
        description += getTruncatedSuffix();
      }

      append(revision.getRevisionNumber().asString() + " ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      append(description + " ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
      append(notNullize(revision.getAuthor()), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      append(", " + formatPrettyDateTime(revision.getRevisionDate()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }

    private @Nls @NotNull String getTruncatedSuffix() {
      return "(" + ELLIPSIS + ")";
    }
  }

  private void fillTree() {
    myTree.setCellRenderer(new MyTreeCellRenderer());
    myRoot.removeAllChildren();

    final List<TreePath> nodesToExpand = new ArrayList<>();
    addRecursively(myRevision, myRoot, nodesToExpand);

    ((DefaultTreeModel) myTree.getModel()).reload(myRoot);
    for (TreePath treePath : nodesToExpand) {
      myTree.expandPath(treePath);
    }
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
  }

  private static final class MyNamedConfigurable extends NamedConfigurable<SvnFileRevision> {
    private final SvnFileRevision myRevision;
    private final VirtualFile myFile;
    private final Project myProject;
    private final Map<Long, SvnChangeList> myListsMap;
    private JComponent myPanel;

    private MyNamedConfigurable(final SvnFileRevision revision, final VirtualFile file, final Project project,
                                final Map<Long, SvnChangeList> listsMap) {
      myRevision = revision;
      myFile = file;
      myProject = project;
      myListsMap = listsMap;
    }

    @Override
    public void setDisplayName(final String name) {
    }

    @Override
    public SvnFileRevision getEditableObject() {
      return myRevision;
    }

    @Override
    public String getBannerSlogan() {
      return myRevision.getRevisionNumber().asString();
    }

    private SvnChangeList getList() {
      SvnChangeList list = myListsMap.get(myRevision.getRevision().getNumber());
      if (list == null) {
        list = (SvnChangeList)SvnVcs.getInstance(myProject).loadRevisions(myFile, myRevision.getRevisionNumber());
        myListsMap.put(myRevision.getRevision().getNumber(), list);
      }
      return list;
    }

    @Override
    public JComponent createOptionsPanel() {
      if (myPanel == null) {
        final SvnChangeList list = getList();
        if (list == null) {
          myPanel = new JPanel();
        } else {
          CommittedChangeListPanel panel = new CommittedChangeListPanel(myProject);
          panel.setChangeList(list);
          myPanel = panel;
        }
      }
      return myPanel;
    }

    @Override
    @Nls
    public String getDisplayName() {
      return getBannerSlogan();
    }

    @Override
    public String getHelpTopic() {
      return null;
    }

    @Override
    public boolean isModified() {
      return false;
    }

    @Override
    public void apply() {
    }

    public SvnFileRevision getRevision() {
      return myRevision;
    }
  }

  private static final class MyDialog extends DialogWrapper {
    private final Project myProject;
    private final SvnFileRevision myRevision;
    private final VirtualFile myFile;

    private MyDialog(final Project project, final SvnFileRevision revision, final VirtualFile file) {
      super(project, true);
      myProject = project;
      myRevision = revision;
      myFile = file;
      setTitle(SvnBundle.message("merge.source.details.title", (myFile == null) ? myRevision.getURL().toDecodedString() : myFile.getName(), myRevision.getRevisionNumber().asString()));
      init();
    }

    @Override
    public JComponent createCenterPanel() {
      final JComponent component = new SvnMergeSourceDetails(myProject, myRevision, myFile).createComponent();
      component.setMinimumSize(new Dimension(300, 200));
      return component;
    }
  }
}
