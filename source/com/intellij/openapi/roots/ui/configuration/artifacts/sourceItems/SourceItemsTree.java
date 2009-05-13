package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.ide.dnd.DnDAction;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDSource;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactsEditorImpl;
import com.intellij.openapi.roots.ui.configuration.artifacts.SimpleDnDAwareTree;
import com.intellij.openapi.roots.ui.configuration.artifacts.SourceItemsDraggingObject;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
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
  private final ArtifactsEditorImpl myArtifactsEditor;
  private SimpleTreeBuilder myBuilder;

  public SourceItemsTree(PackagingEditorContext editorContext, ArtifactsEditorImpl artifactsEditor) {
    myEditorContext = editorContext;
    myArtifactsEditor = artifactsEditor;
    myTree = new SimpleDnDAwareTree();
    myBuilder = new SimpleTreeBuilder(myTree, myTree.getBuilderModel(), new SourceItemsTreeStructure(editorContext, artifactsEditor), new WeightBasedComparator(true));
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    Disposer.register(this, myBuilder);
    DnDManager.getInstance().registerSource(this, myTree);
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

  private DefaultMutableTreeNode[] getNodesToDrag() {
    return myTree.getSelectedNodes(DefaultMutableTreeNode.class, null);
  }

  public boolean canStartDragging(DnDAction action, Point dragOrigin) {
    return getNodesToDrag().length > 0;
  }

  public DnDDragStartBean startDragging(DnDAction action, Point dragOrigin) {
    final DefaultMutableTreeNode[] nodes = getNodesToDrag();
    List<PackagingSourceItem> items = new ArrayList<PackagingSourceItem>();
    for (DefaultMutableTreeNode node : nodes) {
      final Object userObject = node.getUserObject();
      if (userObject instanceof SourceItemNode) {
        items.add(((SourceItemNode)userObject).getSourceItem());
      }
    }
    return new DnDDragStartBean(new SourceItemsDraggingObject(items.toArray(new PackagingSourceItem[items.size()])));
  }

  public Pair<Image, Point> createDraggedImage(DnDAction action, Point dragOrigin) {
    final DefaultMutableTreeNode[] nodes = getNodesToDrag();
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
    private final PackagingEditorContext myEditorContext;
    private final ArtifactsEditorImpl myArtifactsEditor;

    public SourceItemsTreeStructure(PackagingEditorContext editorContext, ArtifactsEditorImpl artifactsEditor) {
      myEditorContext = editorContext;
      myArtifactsEditor = artifactsEditor;
    }

    @Override
    public Object getRootElement() {
      return new SourceItemsTreeRoot(myEditorContext, myArtifactsEditor);
    }
  }
}
