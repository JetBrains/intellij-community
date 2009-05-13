package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDTarget;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingTreeNodeFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ModifiableArtifact;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingElementPropertiesPanel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.treeStructure.*;
import com.intellij.util.Function;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LayoutTreeComponent implements DnDTarget, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeComponent");
  @NonNls private static final String EMPTY_CARD = "<empty>";
  private final ArtifactsEditorImpl myArtifactsEditor;
  private LayoutTree myTree;
  private JPanel myTreePanel;
  private final ComplexElementSubstitutionParameters mySubstitutionParameters;
  private PackagingEditorContext myContext;
  private ArtifactRootElement myModifiableRoot;
  private final Artifact myOriginalArtifact;
  private SelectedElementInfo<?> mySelectedElementInfo = new SelectedElementInfo<PackagingElement<?>>(null);
  private Map<String, PackagingElementPropertiesPanel<?>> myPropertiesPanels = new HashMap<String, PackagingElementPropertiesPanel<?>>();
  private JPanel myPropertiesPanel;
  private SimpleTreeBuilder myBuilder;

  public LayoutTreeComponent(ArtifactsEditorImpl artifactsEditor, ComplexElementSubstitutionParameters substitutionParameters,
                               PackagingEditorContext context, Artifact originalArtifact) {
    myArtifactsEditor = artifactsEditor;
    mySubstitutionParameters = substitutionParameters;
    myContext = context;
    myOriginalArtifact = originalArtifact;
    myTree = new LayoutTree();
    myBuilder = new SimpleTreeBuilder(myTree, myTree.getBuilderModel(), new PackagingElementsTreeStructure(), new WeightBasedComparator(true));
    Disposer.register(this, myTree);
    Disposer.register(this, myBuilder);

    myTree.setRootVisible(true);
    myTree.setShowsRootHandles(false);
    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        updatePropertiesPanel();
      }
    });
    myPropertiesPanel = createPropertiesPanel();
    myTreePanel = new JPanel(new BorderLayout());
    myTreePanel.add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);
    myTreePanel.add(myPropertiesPanel, BorderLayout.SOUTH);
    DnDManager.getInstance().registerTarget(this, myTree);
  }

  @Nullable
  private static PackagingElementNode getNode(Object value) {
    if (!(value instanceof DefaultMutableTreeNode)) return null;
    final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
    return userObject instanceof PackagingElementNode ? (PackagingElementNode)userObject : null;
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
    final PackagingElement<?> selected = getSelection().getElementIfSingle();
    if (Comparing.equal(selected, mySelectedElementInfo.myElement)) {
      return;
    }
    mySelectedElementInfo.save();
    mySelectedElementInfo = new SelectedElementInfo<PackagingElement<?>>(selected);
    mySelectedElementInfo.showPropertiesPanel();
  }

  public void rebuildTree() {
    myBuilder.updateFromRoot(true);
    updatePropertiesPanel();
  }

  public LayoutTreeSelection getSelection() {
    return myTree.getSelection();
  }

  public void addNewPackagingElement(@NotNull PackagingElementType<?> type) {
    final PackagingElementNode<?> parentNode = getParentNode(myTree.getSelection());
    final PackagingElement<?> element = parentNode.getElementIfSingle();
    ensureRootIsWritable();
    CompositePackagingElement<?> parent = element instanceof CompositePackagingElement<?> ? (CompositePackagingElement<?>)element
                                                                                             : getArtifact().getRootElement();

    final List<? extends PackagingElement<?>> children = type.createWithDialog(myContext, getArtifact(), parent);
    for (PackagingElement<?> child : children) {
      parent.addChild(child);
    }
    updateAndSelect(parentNode, children);
  }

  public void updateAndSelect(PackagingElementNode<?> node, final List<? extends PackagingElement<?>> toSelect) {
    final DefaultMutableTreeNode treeNode = TreeUtil.findNodeWithObject(myTree.getRootNode(), node);
    myBuilder.addSubtreeToUpdate(treeNode, new Runnable() {
      public void run() {
        List<SimpleNode> nodes = myTree.findNodes(toSelect);
        myBuilder.select(nodes.toArray(new Object[nodes.size()]), null);
      }
    });
  }

  public Function<PackagingElement<?>, PackagingElement<?>> ensureRootIsWritable() {
    final ModifiableArtifact artifact = myContext.getModifiableArtifactModel().getOrCreateModifiableArtifact(myOriginalArtifact);
    artifact.setRootElement(getRootElement());
    return Function.ID;
  }

  public void removeSelectedElements() {
    final LayoutTreeSelection selection = myTree.getSelection();
    for (PackagingElementNode<?> node : selection.getSelectedNodes()) {
      final PackagingElementNode<?> parent = selection.getParentNode(node);
      if (parent != null) {
        final Function<PackagingElement<?>, PackagingElement<?>> old2New = ensureRootIsWritable();
        final List<? extends PackagingElement<?>> toDelete = node.getPackagingElements();
        final List<? extends PackagingElement<?>> parentElements = parent.getPackagingElements();
        for (PackagingElement<?> oldParent : parentElements) {
          final PackagingElement<?> newParent = old2New.fun(oldParent);
          if (newParent instanceof CompositePackagingElement<?>) {
            for (PackagingElement<?> element : toDelete) {
              //todo[nik] improve
              ((CompositePackagingElement<?>)newParent).removeChild(element);
            }
          }
        }
      }
    }
    myArtifactsEditor.rebuildTries();
  }

  private PackagingElementNode<?> getParentNode(final LayoutTreeSelection selection) {
    final PackagingElementNode<?> node = selection.getNodeIfSingle();
    if (node != null) {
      if (node.getElementIfSingle() instanceof CompositePackagingElement) {
        return node;
      }
      final PackagingElementNode<?> parent = selection.getParentNode(node);
      if (parent != null) {
        return parent;
      }
    }
    return myTree.getRootPackagingNode();
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
      final DefaultMutableTreeNode parent = findParentCompositeElementNode(aEvent.getRelativePoint().getPoint(myTree));
      if (parent != null) {
        final PackagingElementDraggingObject draggingObject = (PackagingElementDraggingObject)object;
        final PackagingElementNode node = getNode(parent);
        if (node != null) {
          final PackagingElement element = node.getElementIfSingle();
          if (element instanceof CompositePackagingElement) {
            draggingObject.setTargetNode(node);
            draggingObject.setTargetElement((CompositePackagingElement<?>)element);
            final Rectangle bounds = myTree.getPathBounds(TreeUtil.getPathFromRoot(parent));
            aEvent.setHighlighting(new RelativeRectangle(myTree, bounds), DnDEvent.DropTargetHighlightingType.RECTANGLE);
            aEvent.setDropPossible(true, null);
          }
        }
      }
    }
    return false;
  }

  public void drop(DnDEvent aEvent) {
    final Object object = aEvent.getAttachedObject();
    if (object instanceof PackagingElementDraggingObject) {
      final PackagingElementDraggingObject draggingObject = (PackagingElementDraggingObject)object;
      final PackagingElementNode<?> targetNode = draggingObject.getTargetNode();
      final CompositePackagingElement<?> targetElement = draggingObject.getTargetElement();
      if (targetElement == null || targetNode == null) return;
      CompositePackagingElement<?> target = (CompositePackagingElement<?>)ensureRootIsWritable().fun(targetElement);
      if (target == null) {
        //todo[nik] target from included content
        return;
      }
      List<PackagingElement<?>> toSelect = new ArrayList<PackagingElement<?>>();
      for (PackagingElement<?> element : draggingObject.createPackagingElements()) {
        toSelect.add(element);
        target.addChild(element);
      }
      updateAndSelect(targetNode, toSelect);
      myArtifactsEditor.getSourceItemsTree().rebuildTree();
    }
  }

  @Nullable
  private DefaultMutableTreeNode findParentCompositeElementNode(Point point) {
    TreePath path = myTree.getPathForLocation(point.x, point.y);
    while (path != null) {
      final PackagingElement<?> element = myTree.getElementByPath(path);
      if (element instanceof CompositePackagingElement) {
        return (DefaultMutableTreeNode)path.getLastPathComponent();
      }
      path = path.getParentPath();
    }
    return null;
  }

  public void cleanUpOnLeave() {
  }

  public void updateDraggedImage(Image image, Point dropPoint, Point imageOffset) {
  }

  public void rename(TreePath path) {
    myTree.startEditingAtPath(path);
  }

  public boolean isEditing() {
    return myTree.isEditing();
  }

  public ArtifactRootElement<?> getRootElement() {
    if (myModifiableRoot == null) {
      myModifiableRoot = ArtifactUtil.copyFromRoot(myOriginalArtifact.getRootElement(), null);
    }
    return myModifiableRoot;
  }

  public void initTree() {
    myBuilder.initRootNode();
    updatePropertiesPanel();
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

  private class PackagingElementsTreeStructure extends SimpleTreeStructure {
    @Override
    public Object getRootElement() {
      return PackagingTreeNodeFactory.createRootNode(myArtifactsEditor, myContext, mySubstitutionParameters);
    }
  }
}