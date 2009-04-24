package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDTarget;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.ui.configuration.packaging.PackagingTreeParameters;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.Comparing;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ModifiableArtifact;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.packaging.ui.PackagingElementPropertiesPanel;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Function;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public class PackagingElementsTree implements DnDTarget, Disposable {
  private static final Convertor<TreePath, String> SPEED_SEARCH_CONVERTOR = new Convertor<TreePath, String>() {
    public String convert(final TreePath path) {
      Object o = path.getLastPathComponent();
      if (o instanceof ArtifactsTreeNode) {
        return ((PackagingElementNode)o).getPresentation().getSearchName();
      }
      return "";
    }
  };
  @NonNls private static final String EMPTY_CARD = "<empty>";
  private final ArtifactsEditorImpl myArtifactsEditor;
  private Tree myTree;
  private JPanel myTreePanel;
  private final PackagingTreeParameters myTreeParameters;
  private PackagingEditorContext myContext;
  private final Artifact myOriginalArtifact;
  private PackagingElementNode myRoot;
  private DefaultTreeModel myTreeModel;
  private SelectedElementInfo<?> mySelectedElementInfo = new SelectedElementInfo<PackagingElement<?>>(null);
  private Map<String, PackagingElementPropertiesPanel<?>> myPropertiesPanels = new HashMap<String, PackagingElementPropertiesPanel<?>>();
  private JPanel myPropertiesPanel;

  public PackagingElementsTree(ArtifactsEditorImpl artifactsEditor, PackagingTreeParameters treeParameters,
                               PackagingEditorContext context, Artifact originalArtifact) {
    myArtifactsEditor = artifactsEditor;
    myTreeParameters = treeParameters;
    myContext = context;
    myOriginalArtifact = originalArtifact;
    myRoot = new PackagingElementNode(getArtifact().getRootElement(), myContext);
    myTreeModel = new DefaultTreeModel(myRoot);
    myTree = new Tree(myTreeModel) {
      @Override
      public String getToolTipText(final MouseEvent event) {
        TreePath path = myTree.getPathForLocation(event.getX(), event.getY());
        if (path != null) {
          return ((PackagingElementNode)path.getLastPathComponent()).getPresentation().getTooltipText();
        }
        return super.getToolTipText();
      }
    };
    myTree.setRootVisible(true);
    myTree.setShowsRootHandles(false);
    myTree.setCellRenderer(new ArtifactsTreeCellRenderer());
    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        updatePropertiesPanel();
      }
    });
    myPropertiesPanel = createPropertiesPanel();
    myTreePanel = new JPanel(new BorderLayout());
    myTreePanel.add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);
    myTreePanel.add(myPropertiesPanel, BorderLayout.SOUTH);
    new TreeSpeedSearch(myTree, SPEED_SEARCH_CONVERTOR, true);
    myTree.addMouseListener(new ArtifactsTreeMouseListener());
    DnDManager.getInstance().registerTarget(this, myTree);
  }

  private JPanel createPropertiesPanel() {
    final JPanel panel = new JPanel(new CardLayout());
    for (PackagingElementType<?> type : PackagingElementFactory.getInstance().getAllElementTypes()) {
      final PackagingElementPropertiesPanel<? extends PackagingElement<?>> propertiesPanel = type.createElementPropertiesPanel();
      if (propertiesPanel != null) {
        myPropertiesPanels.put(type.getId(), propertiesPanel);
        panel.add(type.getId(), propertiesPanel.getComponent());
      }
    }
    panel.add(EMPTY_CARD, new JPanel());
    return panel;
  }

  public Artifact getArtifact() {
    return myArtifactsEditor.getArtifact();
  }

  public Tree getTree() {
    return myTree;
  }

  public void updatePropertiesPanel() {
    final List<? extends PackagingElement> elements = getSelectedElements();
    if (elements.size() == 1 && Comparing.equal(elements.get(0), mySelectedElementInfo.myElement)) {
      return;
    }
    mySelectedElementInfo.save();
    mySelectedElementInfo = new SelectedElementInfo<PackagingElement<?>>(elements.size() == 1 ? elements.get(0) : null);
    mySelectedElementInfo.showPropertiesPanel();
  }

  public void rebuildTree() {
    ArtifactsTreeState state = ArtifactsTreeState.saveState(myTree);
    myRoot = new PackagingElementNode(myArtifactsEditor.getArtifact().getRootElement(), myContext);
    myTreeModel.setRoot(myRoot);

    addNodes(myRoot, myArtifactsEditor.getArtifact().getRootElement().getChildren());

    TreeUtil.sort(myTreeModel, new Comparator<PackagingElementNode>() {
      public int compare(final PackagingElementNode node1, final PackagingElementNode node2) {
        double weight1 = node1.getPresentation().getWeight();
        double weight2 = node2.getPresentation().getWeight();
        if (weight1 < weight2) return -1;
        if (weight1 > weight2) return 1;

        return node1.getPresentation().getPresentableName().compareToIgnoreCase(node2.getPresentation().getPresentableName());
      }
    });
    state.restoreState(myTree);
    updatePropertiesPanel();
  }

  private void addNodes(ArtifactsTreeNode parent, final Collection<? extends PackagingElement<?>> elements) {
    for (PackagingElement<?> child : elements) {
      addNode(parent, child);
    }
  }

  private void addNode(ArtifactsTreeNode parent, PackagingElement<?> child) {
    if (child instanceof ComplexPackagingElement && myTreeParameters.isShowIncludedContent()) {
      final Collection<? extends PackagingElement<?>> substitution = ((ComplexPackagingElement<?>)child).getSubstitution(myContext);
      addNodes(parent, substitution);
      return;
    }

    final PackagingElementNode newNode = findNodeToMergeOrCreate(parent, child);
    if (child instanceof CompositePackagingElement) {
      addNodes(newNode, ((CompositePackagingElement<?>)child).getChildren());
    }
  }

  public PackagingElementNode getRoot() {
    return myRoot;
  }

  private PackagingElementNode findNodeToMergeOrCreate(ArtifactsTreeNode parent, PackagingElement<?> element) {
    for (ArtifactsTreeNode node : parent.getChildren()) {
      if (node instanceof PackagingElementNode) {
        final PackagingElementNode packagingElementNode = (PackagingElementNode)node;
        final PackagingElement<?> packagingElement = packagingElementNode.getPackagingElement();
        if (packagingElement instanceof CompositePackagingElement && ((CompositePackagingElement<?>)packagingElement).canBeMergedWith(element)) {
          return packagingElementNode;
        }
      }
    }
    final PackagingElementNode newNode = new PackagingElementNode(element, myContext);
    parent.add(newNode);
    return newNode;
  }

  public List<? extends PackagingElement> getSelectedElements() {
    final List<PackagingElement> elements = new ArrayList<PackagingElement>();
    for (PackagingElementNode elementNode : getSelectedNodes()) {
      elements.add(elementNode.getPackagingElement());
    }
    return elements;
  }

  public PackagingElementNode[] getSelectedNodes() {
    return myTree.getSelectedNodes(PackagingElementNode.class, null);
  }


  public void addNewPackagingElement(@Nullable CompositePackagingElementType<?> parentType, @NotNull PackagingElementType<?> type) {
    final CompositePackagingElement<?> oldParent = getParent();
    CompositePackagingElement<?> parent = (CompositePackagingElement<?>)ensureRootIsWritable().fun(oldParent);
    if (parentType != null) {
      final CompositePackagingElement<?> element = parentType.createComposite(myContext, parent);
      if (element == null) return;
      parent.addChild(element);
      parent = element;
    }

    final List<? extends PackagingElement<?>> children = type.createWithDialog(myContext, getArtifact(), parent);
    for (PackagingElement<?> child : children) {
      parent.addChild(child);
    }
    myArtifactsEditor.rebuildTries();
    selectElements(children);
  }

  private void selectElements(List<? extends PackagingElement<?>> elements) {
    myTree.getSelectionModel().clearSelection();
    for (PackagingElement<?> element : elements) {
      final PackagingElementNode node = findNode(element);
      if (node != null) {
        myTree.getSelectionModel().addSelectionPath(TreeUtil.getPathFromRoot(node));
      }
    }
  }

  public Function<PackagingElement<?>, PackagingElement<?>> ensureRootIsWritable() {
    final ModifiableArtifact artifact = myContext.getModifiableArtifactModel().getOrCreateModifiableArtifact(myOriginalArtifact);
    if (artifact.getRootElement() == myOriginalArtifact.getRootElement()) {
      final HashMap<PackagingElement<?>, PackagingElement<?>> old2New = new HashMap<PackagingElement<?>, PackagingElement<?>>();
      ArtifactUtil.copyRoot(artifact, old2New);
      return new Function<PackagingElement<?>, PackagingElement<?>>() {
        public PackagingElement<?> fun(PackagingElement<?> packagingElement) {
          return old2New.get(packagingElement);
        }
      };
    }
    return Function.ID;
  }

  private PackagingElementNode findNode(final PackagingElement<?> element) {
    final Ref<PackagingElementNode> ref = Ref.create(null);
    TreeUtil.traverseDepth(myRoot, new TreeUtil.Traverse() {
      public boolean accept(Object node) {
        if (node instanceof PackagingElementNode) {
          PackagingElementNode packagingElementNode = (PackagingElementNode)node;
          if (element.equals(packagingElementNode.getPackagingElement())) {
            ref.set(packagingElementNode);
            return false;
          }
        }
        return true;
      }
    });
    return ref.get();
  }

  public void removeSelectedElements() {
    final PackagingElementNode[] nodes = myTree.getSelectedNodes(PackagingElementNode.class, null);
    for (PackagingElementNode node : nodes) {
      final TreeNode parent = node.getParent();
      if (parent instanceof PackagingElementNode) {
        final PackagingElement<?> element = ((PackagingElementNode)parent).getPackagingElement();
        if (element instanceof CompositePackagingElement<?>) {
          ((CompositePackagingElement<?>)element).removeChild(node.getPackagingElement());
        }
      }
    }
    myArtifactsEditor.rebuildTries();
  }

  private CompositePackagingElement<?> getParent() {
    final PackagingElementNode[] nodes = myTree.getSelectedNodes(PackagingElementNode.class, null);
    if (nodes.length == 1) {
      final PackagingElement<?> packagingElement = nodes[0].getPackagingElement();
      if (packagingElement instanceof CompositePackagingElement) {
        return (CompositePackagingElement<?>)packagingElement;
      }
    }
    return (ArtifactRootElement<?>)myRoot.getPackagingElement();
  }

  public JPanel getTreePanel() {
    return myTreePanel;
  }

  public void dispose() {
    DnDManager.getInstance().unregisterTarget(this, myTree);
  }

  public boolean update(DnDEvent aEvent) {
    aEvent.setDropPossible(false, null);
    aEvent.hideHighlighter();
    final Object object = aEvent.getAttachedObject();
    if (object instanceof PackagingElementDraggingObject) {
      final PackagingElementNode parent = findParentCompositeElementNode(aEvent.getRelativePoint().getPoint(myTree));
      if (parent != null) {
        final PackagingElementDraggingObject draggingObject = (PackagingElementDraggingObject)object;
        draggingObject.setTarget((CompositePackagingElement<?>)parent.getPackagingElement());
        final Rectangle bounds = myTree.getPathBounds(TreeUtil.getPathFromRoot(parent));
        aEvent.setHighlighting(new RelativeRectangle(myTree, bounds), DnDEvent.DropTargetHighlightingType.RECTANGLE);
        aEvent.setDropPossible(true, null);
      }
    }
    return true;
  }

  public void drop(DnDEvent aEvent) {
    final Object object = aEvent.getAttachedObject();
    if (object instanceof PackagingElementDraggingObject) {
      final PackagingElementDraggingObject draggingObject = (PackagingElementDraggingObject)object;
      CompositePackagingElement<?> target = (CompositePackagingElement<?>)ensureRootIsWritable().fun(draggingObject.getTarget());
      for (PackagingSourceItem item : draggingObject.getSourceItems()) {
        final PackagingElement element = item.createElement();
        target.addChild(element);
      }
      myArtifactsEditor.rebuildTries();
    }
  }

  @Nullable
  private PackagingElementNode findParentCompositeElementNode(Point point) {
    final TreePath path = myTree.getPathForLocation(point.x, point.y);
    if (path == null) return null;
    Object node = path.getLastPathComponent();
    while (node instanceof PackagingElementNode) {
      final PackagingElementNode packagingElementNode = (PackagingElementNode)node;
      final PackagingElement<?> element = packagingElementNode.getPackagingElement();
      if (element instanceof CompositePackagingElement) {
        return packagingElementNode;
      }
      node = packagingElementNode.getParent();
    }
    return null;
  }

  public void cleanUpOnLeave() {
  }

  public void updateDraggedImage(Image image, Point dropPoint, Point imageOffset) {
  }

  private class ArtifactsTreeMouseListener extends MouseAdapter {
    @Override
    public void mouseClicked(final MouseEvent e) {
      if (e.getClickCount() == 2) {
        PackagingElementNode[] nodes = myTree.getSelectedNodes(PackagingElementNode.class, null);
        if (nodes.length == 1) {
          PackagingElementNode node = nodes[0];
          if (node.getChildCount() == 0) {
          }
        }
      }
    }
  }

  private static class ArtifactsTreeCellRenderer extends ColoredTreeCellRenderer {
    public void customizeCellRenderer(final JTree tree,
                                      final Object value,
                                      final boolean selected,
                                      final boolean expanded,
                                      final boolean leaf,
                                      final int row,
                                      final boolean hasFocus) {
      if (value instanceof PackagingElementNode) {
        PackagingElementNode node = (PackagingElementNode)value;
        node.getPresentation().render(this);
      }
      setEnabled(tree.isEnabled());
    }
  }

  private class SelectedElementInfo<E extends PackagingElement<?>> {
    private final E myElement;
    private PackagingElementPropertiesPanel<E> myCurrentPanel;

    private SelectedElementInfo(@Nullable E element) {
      myElement = element;
      if (myElement != null) {
        //noinspection unchecked
        myCurrentPanel = (PackagingElementPropertiesPanel<E>)myPropertiesPanels.get(element.getType().getId());
        if (myCurrentPanel != null && !myCurrentPanel.isAvailable(element)) {
          myCurrentPanel = null;
        }
        if (myCurrentPanel != null) {
          myCurrentPanel.loadFrom(element);
        }
      }
    }

    public void save() {
      if (myCurrentPanel != null) {
        myCurrentPanel.saveTo(myElement);
      }
    }

    public void showPropertiesPanel() {
      final CardLayout cardLayout = (CardLayout)myPropertiesPanel.getLayout();
      if (myCurrentPanel != null) {
        cardLayout.show(myPropertiesPanel, myElement.getType().getId());
      }
      else {
        cardLayout.show(myPropertiesPanel, EMPTY_CARD);
      }
    }
  }
}