/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.changes.committed.CommittedChangeListRenderer;
import com.intellij.openapi.vcs.changes.ui.ChangeListViewerDialog;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ContentsUtil;
import org.jetbrains.annotations.Nls;
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

import static com.intellij.openapi.util.text.StringUtil.notNullize;

public class SvnMergeSourceDetails extends MasterDetailsComponent {
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
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS);
    final ContentManager contentManager = toolWindow.getContentManager();

    final MyDialog dialog = new MyDialog(project, revision, file);
    // TODO: Temporary memory leak fix - rewrite this part not to create dialog if only createCenterPanel(), but not show() is invoked
    Disposer.register(project, dialog.getDisposable());

    Content content = ContentFactory.SERVICE.getInstance().createContent(dialog.createCenterPanel(),
        SvnBundle.message("merge.source.details.title", (file == null) ? revision.getURL().toDecodedString() : file.getName(), revision.getRevisionNumber().asString()), true);
    ContentsUtil.addOrReplaceContent(contentManager, content, true);

    toolWindow.activate(null);
    } else {
      new MyDialog(project, revision, file).show();
    }
  }

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
    private final static String ourDots = "(...)";

    public void customizeCellRenderer(final JTree tree,
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
      } else {
        final MyNode myNode = (MyNode)value;
        final MyNamedConfigurable configurable = (MyNamedConfigurable) myNode.getConfigurable();
        revision = configurable.getRevision();
      }

      final String revisonNumber = revision.getRevisionNumber().asString();
      String description = CommittedChangeListRenderer.getDescriptionOfChangeList(revision.getCommitMessage());
      int width = metrics.stringWidth(description);
      int dotsWidth = metrics.stringWidth(ourDots);
      boolean descriptionTruncated = false;
      if (ourMaxWidth < width) {
        description = CommittedChangeListRenderer.truncateDescription(description, metrics, ourMaxWidth - dotsWidth);
        descriptionTruncated = true;
      }
      if (descriptionTruncated) {
        description += ourDots;
      }

      final String date = CommittedChangeListRenderer.getDateOfChangeList(revision.getRevisionDate());

      append(revisonNumber + " ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      append(description + " ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
      append(notNullize(revision.getAuthor()), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      append(", " + date, SimpleTextAttributes.REGULAR_ATTRIBUTES);
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

  private static class MyNamedConfigurable extends NamedConfigurable<SvnFileRevision> {
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

    public void setDisplayName(final String name) {
    }

    public SvnFileRevision getEditableObject() {
      return myRevision;
    }

    public String getBannerSlogan() {
      return myRevision.getRevisionNumber().asString();
    }

    private SvnChangeList getList() {
      SvnChangeList list = myListsMap.get(myRevision);
      if (list == null) {
        list = (SvnChangeList)SvnVcs.getInstance(myProject).loadRevisions(myFile, myRevision.getRevisionNumber());
        myListsMap.put(myRevision.getRevision().getNumber(), list);
      }
      return list;
    }

    public JComponent createOptionsPanel() {
      if (myPanel == null) {
        final SvnChangeList list = getList();
        if (list == null) {
          myPanel = new JPanel();
        } else {
          ChangeListViewerDialog dialog = new ChangeListViewerDialog(myProject, list);
          // TODO: Temporary memory leak fix - rewrite this part not to create dialog if only createCenterPanel(), but not show() is invoked
          Disposer.register(myProject, dialog.getDisposable());
          myPanel = dialog.createCenterPanel();
        }
      }
      return myPanel;
    }

    @Nls
    public String getDisplayName() {
      return getBannerSlogan();  
    }

    @Override
    public String getHelpTopic() {
      return null;
    }

    public boolean isModified() {
      return false;
    }

    public void apply() {
    }

    public void reset() {
    }

    public void disposeUIResources() {
    }

    public SvnFileRevision getRevision() {
      return myRevision;
    }
  }

  private static class MyDialog extends DialogWrapper {
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

    public JComponent createCenterPanel() {
      final JComponent component = new SvnMergeSourceDetails(myProject, myRevision, myFile).createComponent();
      component.setMinimumSize(new Dimension(300, 200));
      return component;
    }
  }
}
