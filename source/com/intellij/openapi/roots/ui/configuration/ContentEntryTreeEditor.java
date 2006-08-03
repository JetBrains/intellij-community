package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.util.projectWizard.ToolbarPanel;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.actions.NewFolderAction;
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl;
import com.intellij.openapi.fileChooser.impl.FileTreeBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.roots.ui.configuration.actions.IconWithTextAction;
import com.intellij.openapi.roots.ui.configuration.actions.ToggleExcludedStateAction;
import com.intellij.openapi.roots.ui.configuration.actions.ToggleSourcesStateAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.TreeToolTipHandler;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.Comparator;

/**
 * @author Eugene Zhuravlev
 * Date: Oct 9, 2003
 * Time: 1:19:47 PM
 */
public class ContentEntryTreeEditor {
  private final Project myProject;
  protected Tree myTree;
  private FileSystemTreeImpl myFileSystemTree;
  private JPanel myTreePanel;
  private final DefaultMutableTreeNode EMPTY_TREE_ROOT = new DefaultMutableTreeNode(ProjectBundle.message("module.paths.empty.node"));
  protected DefaultActionGroup myEditingActionsGroup;
  private ContentEntryEditor myContentEntryEditor;
  private final MyContentEntryEditorListener myContentEntryEditorListener = new MyContentEntryEditorListener();
  private final FileChooserDescriptor myDescriptor;
  private final ToggleExcludedStateAction myToggleExcludedAction;
  private final ToggleSourcesStateAction myMarkSourcesAction;
  private final ToggleSourcesStateAction myMarkTestsAction;

  public ContentEntryTreeEditor(Project project) {
    myProject = project;
    myTree = new Tree();
    myTree.setRootVisible(true);
    myTree.setShowsRootHandles(true);

    myEditingActionsGroup = new DefaultActionGroup();

    myMarkSourcesAction = new ToggleSourcesStateAction(myTree, this, false);
    myMarkSourcesAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.ALT_MASK)), myTree);

    myToggleExcludedAction = new ToggleExcludedStateAction(myTree, this);
    myToggleExcludedAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.ALT_MASK)), myTree);

    myMarkTestsAction = new ToggleSourcesStateAction(myTree, this, true);
    myMarkTestsAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.ALT_MASK)), myTree);

    TreeToolTipHandler.install(myTree);
    TreeUtil.installActions(myTree);
    new TreeSpeedSearch(myTree);

    myTreePanel = new JPanel(new BorderLayout());
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
    scrollPane.setPreferredSize(new Dimension(300, 300));
    myTreePanel.add(new ToolbarPanel(scrollPane, myEditingActionsGroup), BorderLayout.CENTER);

    myTreePanel.setVisible(false);
    myDescriptor = new FileChooserDescriptor(false, true, false, false, false, false);
    myDescriptor.setShowFileSystemRoots(false);
  }

  protected void createEditingActions() {
    myEditingActionsGroup.add(myToggleExcludedAction);
    myEditingActionsGroup.add(myMarkSourcesAction);
    myEditingActionsGroup.add(myMarkTestsAction);
  }

  protected TreeCellRenderer getContentEntryCellRenderer() {
    return new ContentEntryTreeCellRenderer(this);
  }

  /**
   * @param contentEntryEditor : null means to clear the editor
   */
  public void setContentEntryEditor(ContentEntryEditor contentEntryEditor) {
    if (myContentEntryEditor != null && myContentEntryEditor.equals(contentEntryEditor)) {
      return;
    }
    if (myFileSystemTree != null) {
      myFileSystemTree.dispose();
      myFileSystemTree = null;
    }
    if (myContentEntryEditor != null) {
      myContentEntryEditor.removeContentEntryEditorListener(myContentEntryEditorListener);
      myContentEntryEditor = null;
    }
    if (contentEntryEditor == null) {
      ((DefaultTreeModel)myTree.getModel()).setRoot(EMPTY_TREE_ROOT);
      myTreePanel.setVisible(false);
      return;
    }
    myTreePanel.setVisible(true);
    myContentEntryEditor = contentEntryEditor;
    myContentEntryEditor.addContentEntryEditorListener(myContentEntryEditorListener);
    final VirtualFile file = contentEntryEditor.getContentEntry().getFile();
    myDescriptor.setRoot(file);
    if (file != null) {
      myDescriptor.setTitle(file.getPresentableUrl());
    }
    else {
      final String url = contentEntryEditor.getContentEntry().getUrl();
      myDescriptor.setTitle(VirtualFileManager.extractPath(url).replace('/', File.separatorChar));
    }
    myFileSystemTree = new FileSystemTreeImpl(myProject, myDescriptor, myTree, getContentEntryCellRenderer()) {
      protected AbstractTreeBuilder createTreeBuilder(JTree tree, DefaultTreeModel treeModel, AbstractTreeStructure treeStructure, Comparator<NodeDescriptor> comparator, FileChooserDescriptor descriptor) {
        return new MyFileTreeBuilder(tree, treeModel, treeStructure, comparator, descriptor);
      }
    };
    final com.intellij.openapi.fileChooser.actions.NewFolderAction newFolderAction = new MyNewFolderAction(myFileSystemTree);
    DefaultActionGroup mousePopupGroup = new DefaultActionGroup();
    mousePopupGroup.add(myEditingActionsGroup);
    mousePopupGroup.addSeparator();
    mousePopupGroup.add(newFolderAction);
    myFileSystemTree.registerMouseListener(mousePopupGroup);

    myFileSystemTree.updateTree();
    if (file != null) {
      select(file);
    }
  }

  public ContentEntryEditor getContentEntryEditor() {
    return myContentEntryEditor;
  }

  public JComponent createComponent() {
    createEditingActions();
    return myTreePanel;
  }

  public void select(VirtualFile file) {
    if (myFileSystemTree != null) {
      myFileSystemTree.select(file);
    }
  }

  public void requestFocus() {
    myTree.requestFocus();
  }

  public void update() {
    if (myFileSystemTree != null) {
      myFileSystemTree.updateTree();
      final DefaultTreeModel model = (DefaultTreeModel)myTree.getModel();
      final int visibleRowCount = myTree.getVisibleRowCount();
      for (int row = 0; row < visibleRowCount; row++) {
        final TreePath pathForRow = myTree.getPathForRow(row);
        if (pathForRow != null) {
          final TreeNode node = (TreeNode)pathForRow.getLastPathComponent();
          if (node != null) {
            model.nodeChanged(node);
          }
        }
      }
    }
  }

  private class MyContentEntryEditorListener extends ContentEntryEditorListenerAdapter {
    public void sourceFolderAdded(ContentEntryEditor editor, SourceFolder folder) {
      update();
    }

    public void sourceFolderRemoved(ContentEntryEditor editor, VirtualFile file, boolean isTestSource) {
      update();
    }

    public void folderExcluded(ContentEntryEditor editor, VirtualFile file) {
      update();
    }

    public void folderIncluded(ContentEntryEditor editor, VirtualFile file) {
      update();
    }

    public void packagePrefixSet(ContentEntryEditor editor, SourceFolder folder) {
      update();
    }
  }

  private static class MyNewFolderAction extends NewFolderAction implements CustomComponentAction {
    public MyNewFolderAction(FileSystemTreeImpl fileSystemTree) {
      super(fileSystemTree);
    }

    public JComponent createCustomComponent(Presentation presentation) {
      return IconWithTextAction.createCustomComponentImpl(this, presentation);
    }
  }

  private static class MyFileTreeBuilder extends FileTreeBuilder {
    public MyFileTreeBuilder(JTree tree, DefaultTreeModel treeModel, AbstractTreeStructure treeStructure, Comparator<NodeDescriptor> comparator, FileChooserDescriptor descriptor) {
      super(tree, treeModel, treeStructure, comparator, descriptor);
    }

    protected boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
      return false; // need this in order to not show plus for empty directories
    }
  }
}
