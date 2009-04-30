package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.ide.dnd.DnDAction;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDSource;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Pair;
import com.intellij.packaging.ui.PackagingDragAndDropSourceItemsProvider;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.packaging.ui.PackagingSourceItemsGroup;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.Collection;

/**
 * @author nik
 */
public class SourceItemsTree implements DnDSource, Disposable{
  private DnDAwareTree myTree;
  private DefaultMutableTreeNode myRoot;
  private final PackagingEditorContext myEditorContext;
  private final ArtifactsEditorImpl myArtifactsEditor;
  private DefaultTreeModel myTreeModel;

  public SourceItemsTree(PackagingEditorContext editorContext, ArtifactsEditorImpl artifactsEditor) {
    myEditorContext = editorContext;
    myArtifactsEditor = artifactsEditor;
    myTree = new DnDAwareTree();
    myRoot = new DefaultMutableTreeNode();
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTreeModel = new DefaultTreeModel(myRoot);
    myTree.setModel(myTreeModel);
    myTree.setCellRenderer(new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        if (value instanceof SourceItemGroupNode) {
          ((SourceItemGroupNode)value).getGroup().render(this);
        }
        else if (value instanceof SourceItemNode) {
          ((SourceItemNode)value).getSourceItem().render(this);
        }
      }
    });
    DnDManager.getInstance().registerSource(this, myTree);
  }

  public void rebuildTree() {
    myRoot.removeAllChildren();
    addChildren(null, myRoot);
    myTreeModel.nodeStructureChanged(myRoot);
    TreeUtil.expandAll(myTree);
  }

  private void addChildren(final PackagingSourceItemsGroup parent, final DefaultMutableTreeNode node) {
    final PackagingDragAndDropSourceItemsProvider[] providers = Extensions.getExtensions(PackagingDragAndDropSourceItemsProvider.EP_NAME);
    for (PackagingDragAndDropSourceItemsProvider provider : providers) {
      final Collection<? extends PackagingSourceItemsGroup> items = provider.getSourceItems(myEditorContext, myArtifactsEditor.getArtifact(),
                                                                                            parent);
      for (PackagingSourceItemsGroup item : items) {
        if (item instanceof PackagingSourceItem) {
          node.add(new SourceItemNode((PackagingSourceItem)item));
        }
        else {
          final DefaultMutableTreeNode child = new SourceItemGroupNode(item);
          addChildren(item, child);
          if (child.getChildCount() > 0) {
            node.add(child);
          }
        }
      }
    }
  }

  public Tree getTree() {
    return myTree;
  }

  public void dispose() {
    DnDManager.getInstance().unregisterSource(this, myTree);
  }

  private SourceItemNode[] getNodesToDrag() {
    return myTree.getSelectedNodes(SourceItemNode.class, null);
  }

  public boolean canStartDragging(DnDAction action, Point dragOrigin) {
    return getNodesToDrag().length > 0;
  }

  public DnDDragStartBean startDragging(DnDAction action, Point dragOrigin) {
    final SourceItemNode[] nodes = getNodesToDrag();
    return new DnDDragStartBean(new PackagingElementDraggingObject(ContainerUtil.map2Array(nodes, PackagingSourceItem.class, new Function<SourceItemNode, PackagingSourceItem>() {
      public PackagingSourceItem fun(SourceItemNode sourceItemNode) {
        return sourceItemNode.getSourceItem();
      }
    })));
  }

  public Pair<Image, Point> createDraggedImage(DnDAction action, Point dragOrigin) {
    final SourceItemNode[] nodes = getNodesToDrag();
    if (nodes.length == 1) {
      return DnDAwareTree.getDragImage(myTree, TreeUtil.getPathFromRoot(nodes[0]), dragOrigin);
    }
    return DnDAwareTree.getDragImage(myTree, nodes.length + " elements", dragOrigin);
  }

  public void dragDropEnd() {
  }

  public void dropActionChanged(int gestureModifiers) {
  }

  public static class SourceItemGroupNode extends DefaultMutableTreeNode {
    private final PackagingSourceItemsGroup myGroup;

    public SourceItemGroupNode(PackagingSourceItemsGroup group) {
      myGroup = group;
    }

    public PackagingSourceItemsGroup getGroup() {
      return myGroup;
    }
  }

  public static class SourceItemNode extends DefaultMutableTreeNode {
    private final PackagingSourceItem mySourceItem;

    public SourceItemNode(PackagingSourceItem sourceItem) {
      mySourceItem = sourceItem;
    }

    public PackagingSourceItem getSourceItem() {
      return mySourceItem;
    }
  }
}
