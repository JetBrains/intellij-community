package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.dnd.DnDAction;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDSource;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorImpl;
import com.intellij.openapi.roots.ui.configuration.artifacts.SimpleDnDAwareTree;
import com.intellij.openapi.roots.ui.configuration.artifacts.SourceItemsDraggingObject;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.treeStructure.SimpleTreeBuilder;
import com.intellij.ui.treeStructure.SimpleTreeStructure;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.WeightBasedComparator;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class SourceItemsTree implements DnDSource, Disposable{
  private SimpleDnDAwareTree myTree;
  private final PackagingEditorContext myEditorContext;
  private final ArtifactEditorImpl myArtifactsEditor;
  private SimpleTreeBuilder myBuilder;

  public SourceItemsTree(ArtifactEditorContext editorContext, ArtifactEditorImpl artifactsEditor) {
    myEditorContext = editorContext;
    myArtifactsEditor = artifactsEditor;
    myTree = new SimpleDnDAwareTree();
    myBuilder = new SimpleTreeBuilder(myTree, myTree.getBuilderModel(), new SourceItemsTreeStructure(editorContext, artifactsEditor), new WeightBasedComparator(true));
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    Disposer.register(this, myBuilder);
    DnDManager.getInstance().registerSource(this, myTree);
    PopupHandler.installPopupHandler(myTree, createPopupGroup(), ActionPlaces.UNKNOWN, ActionManager.getInstance());
  }

  private ActionGroup createPopupGroup() {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new PutSourceItemIntoDefaultLocationAction(this, myArtifactsEditor));
    group.add(new PutSourceItemIntoParentAndLinkViaManifestAction(this, myArtifactsEditor));

    DefaultTreeExpander expander = new DefaultTreeExpander(myTree);
    final CommonActionsManager commonActionsManager = CommonActionsManager.getInstance();
    group.addAction(commonActionsManager.createExpandAllAction(expander, myTree));
    group.addAction(commonActionsManager.createCollapseAllAction(expander, myTree));
    return group;
  }

  public void rebuildTree() {
    myBuilder.updateFromRoot(true);
  }

  public void initTree() {
    myBuilder.initRootNode();
  }

  public Tree getTree() {
    return myTree;
  }

  public void dispose() {
    DnDManager.getInstance().unregisterSource(this, myTree);
  }

  private DefaultMutableTreeNode[] getSelectedNodes() {
    return myTree.getSelectedNodes(DefaultMutableTreeNode.class, null);
  }

  public boolean canStartDragging(DnDAction action, Point dragOrigin) {
    return !getSelectedItems().isEmpty();
  }

  public DnDDragStartBean startDragging(DnDAction action, Point dragOrigin) {
    List<PackagingSourceItem> items = getSelectedItems();
    return new DnDDragStartBean(new SourceItemsDraggingObject(items.toArray(new PackagingSourceItem[items.size()])));
  }

  public List<PackagingSourceItem> getSelectedItems() {
    final DefaultMutableTreeNode[] nodes = getSelectedNodes();
    List<PackagingSourceItem> items = new ArrayList<PackagingSourceItem>();
    for (DefaultMutableTreeNode node : nodes) {
      final Object userObject = node.getUserObject();
      if (userObject instanceof SourceItemNode) {
        final PackagingSourceItem sourceItem = ((SourceItemNode)userObject).getSourceItem();
        if (sourceItem != null && sourceItem.isProvideElements()) {
          items.add(sourceItem);
        }
      }
    }
    return items;
  }

  public Pair<Image, Point> createDraggedImage(DnDAction action, Point dragOrigin) {
    final DefaultMutableTreeNode[] nodes = getSelectedNodes();
    if (nodes.length == 1) {
      return DnDAwareTree.getDragImage(myTree, TreeUtil.getPathFromRoot(nodes[0]), dragOrigin);
    }
    return DnDAwareTree.getDragImage(myTree, ProjectBundle.message("drag.n.drop.text.0.packaging.elements", nodes.length), dragOrigin);
  }

  public void dragDropEnd() {
  }

  public void dropActionChanged(int gestureModifiers) {
  }

  private static class SourceItemsTreeStructure extends SimpleTreeStructure {
    private final ArtifactEditorContext myEditorContext;
    private final ArtifactEditorImpl myArtifactsEditor;
    private SourceItemsTreeRoot myRoot;

    public SourceItemsTreeStructure(ArtifactEditorContext editorContext, ArtifactEditorImpl artifactsEditor) {
      myEditorContext = editorContext;
      myArtifactsEditor = artifactsEditor;
    }

    @Override
    public Object getRootElement() {
      if (myRoot == null) {
        myRoot = new SourceItemsTreeRoot(myEditorContext, myArtifactsEditor);
      }
      return myRoot;
    }
  }
}
