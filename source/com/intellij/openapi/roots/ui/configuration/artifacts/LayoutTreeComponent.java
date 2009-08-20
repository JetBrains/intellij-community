package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDTarget;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingNodeSource;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingTreeNodeFactory;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPropertiesPanel;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.treeStructure.SimpleTreeBuilder;
import com.intellij.ui.treeStructure.SimpleTreeStructure;
import com.intellij.ui.treeStructure.WeightBasedComparator;
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
import java.util.*;
import java.util.List;

public class LayoutTreeComponent implements DnDTarget, Disposable {
  @NonNls private static final String EMPTY_CARD = "<empty>";
  @NonNls private static final String PROPERTIES_CARD = "properties";
  private final ArtifactEditorImpl myArtifactsEditor;
  private LayoutTree myTree;
  private JPanel myTreePanel;
  private final ComplexElementSubstitutionParameters mySubstitutionParameters;
  private ArtifactEditorContext myContext;
  private final Artifact myOriginalArtifact;
  private SelectedElementInfo<?> mySelectedElementInfo = new SelectedElementInfo<PackagingElement<?>>(null);
  private JPanel myPropertiesPanelWrapper;
  private JPanel myPropertiesPanel;
  private SimpleTreeBuilder myBuilder;

  public LayoutTreeComponent(ArtifactEditorImpl artifactsEditor, ComplexElementSubstitutionParameters substitutionParameters,
                               ArtifactEditorContext context, Artifact originalArtifact) {
    myArtifactsEditor = artifactsEditor;
    mySubstitutionParameters = substitutionParameters;
    myContext = context;
    myOriginalArtifact = originalArtifact;
    myTree = new LayoutTree(myArtifactsEditor);
    myBuilder = new SimpleTreeBuilder(myTree, myTree.getBuilderModel(), new LayoutTreeStructure(), new WeightBasedComparator(true));
    Disposer.register(this, myTree);
    Disposer.register(this, myBuilder);

    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        updatePropertiesPanel();
      }
    });
    createPropertiesPanel();
    myTreePanel = new JPanel(new BorderLayout());
    myTreePanel.add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);
    myTreePanel.add(myPropertiesPanelWrapper, BorderLayout.SOUTH);
    DnDManager.getInstance().registerTarget(this, myTree);
  }

  @Nullable
  private static PackagingElementNode getNode(Object value) {
    if (!(value instanceof DefaultMutableTreeNode)) return null;
    final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
    return userObject instanceof PackagingElementNode ? (PackagingElementNode)userObject : null;
  }

  private void createPropertiesPanel() {
    myPropertiesPanel = new JPanel(new BorderLayout());
    final JPanel emptyPanel = new JPanel();

    myPropertiesPanelWrapper = new JPanel(new CardLayout());
    myPropertiesPanelWrapper.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    myPropertiesPanelWrapper.add(EMPTY_CARD, emptyPanel);
    myPropertiesPanelWrapper.add(PROPERTIES_CARD, myPropertiesPanel);
  }

  public Artifact getArtifact() {
    return myArtifactsEditor.getArtifact();
  }

  public LayoutTree getLayoutTree() {
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

  public void saveElementProperties() {
    mySelectedElementInfo.save();
  }

  public void rebuildTree() {
    myBuilder.updateFromRoot(true);
    updatePropertiesPanel();
    myArtifactsEditor.queueValidation();
  }

  public LayoutTreeSelection getSelection() {
    return myTree.getSelection();
  }

  public void addNewPackagingElement(@NotNull PackagingElementType<?> type) {
    final PackagingElementNode<?> parentNode = getParentNode(myTree.getSelection());
    final PackagingElement<?> element = parentNode.getElementIfSingle();
    if (!checkCanAdd(type, element, parentNode)) return;

    ensureRootIsWritable();
    CompositePackagingElement<?> parent = element instanceof CompositePackagingElement<?> ? (CompositePackagingElement<?>)element
                                                                                             : getArtifact().getRootElement();

    final List<? extends PackagingElement<?>> children = type.createWithDialog(myContext, getArtifact(), parent);
    for (PackagingElement<?> child : children) {
      parent.addOrFindChild(child);
    }
    updateAndSelect(parentNode, children);
  }

  public boolean checkCanAdd(@Nullable PackagingElementType<?> type, PackagingElement<?> parentElement, PackagingElementNode<?> parentNode) {
    final Collection<PackagingNodeSource> nodeSources = parentNode.getNodeSources();
    final String elementType = type != null ? type.getPresentableName() : "new element";
    if (parentElement == null || nodeSources.size() > 1) {
      Messages.showErrorDialog(myArtifactsEditor.getMainComponent(), "Cannot add " +
                                                                     elementType + ": the selected node is consisting of several different elements.");
      return false;
    }

    if (nodeSources.size() == 1) {
      final PackagingNodeSource source = nodeSources.iterator().next();
      final Artifact artifact = source.getSourceArtifact();
      if (artifact != null) {
        final int answer = Messages.showYesNoDialog(myArtifactsEditor.getMainComponent(),
                                                    "The selected node comes from '" + artifact.getName() +
                                                    "' artifact. Do you want to add " + elementType + " into it?",
                                                    "Add Elements", null);
        if (answer != 0) {
          return false;
        }
      }
      else {
        Messages.showErrorDialog(myArtifactsEditor.getMainComponent(), "Cannot add " +
                                                                       elementType + ": the selected node comes from '" + source.getPresentableName() + "'");
        return false;
      }
    }
    return true;
  }

  public void updateAndSelect(PackagingElementNode<?> node, final List<? extends PackagingElement<?>> toSelect) {
    myArtifactsEditor.queueValidation();
    final DefaultMutableTreeNode treeNode = TreeUtil.findNodeWithObject(myTree.getRootNode(), node);
    myBuilder.addSubtreeToUpdate(treeNode, new Runnable() {
      public void run() {
        List<PackagingElementNode<?>> nodes = myTree.findNodes(toSelect);
        myBuilder.select(nodes.toArray(new Object[nodes.size()]), null);
      }
    });
  }

  public void ensureRootIsWritable() {
    myContext.ensureRootIsWritable(myOriginalArtifact);
  }

  public void removeSelectedElements() {
    final LayoutTreeSelection selection = myTree.getSelection();
    if (!checkCanRemove(selection.getNodes())) return;
    ensureRootIsWritable();

    removeNodes(selection.getNodes());
    myArtifactsEditor.rebuildTries();
  }

  public void removeNodes(final List<PackagingElementNode<?>> nodes) {
    Set<PackagingElement<?>> parents = new HashSet<PackagingElement<?>>();
    for (PackagingElementNode<?> node : nodes) {
      final List<? extends PackagingElement<?>> toDelete = node.getPackagingElements();
      for (PackagingElement<?> element : toDelete) {
        final CompositePackagingElement<?> parent = node.getParentElement(element);
        if (parent != null) {
          parents.add(parent);
          parent.removeChild(element);
        }
      }
    }
    final List<PackagingElementNode<?>> parentNodes = myTree.findNodes(parents);
    for (PackagingElementNode<?> parentNode : parentNodes) {
      myTree.addSubtreeToUpdate(parentNode);
    }
  }

  public boolean checkCanRemove(final List<PackagingElementNode<?>> nodes) {
    Set<Artifact> parentArtifacts = new HashSet<Artifact>();
    for (PackagingElementNode<?> node : nodes) {
      final Collection<PackagingNodeSource> sources = node.getNodeSources();
      for (PackagingNodeSource source : sources) {
        final Artifact artifact = source.getSourceArtifact();
        if (artifact != null) {
          parentArtifacts.add(artifact);
        }
        else {
          Messages.showErrorDialog(myArtifactsEditor.getMainComponent(), "'" + node.getElementPresentation().getPresentableName() + "' comes from '" + source.getPresentableName() + "' so it cannot be removed itself");
          return false;
        }
      }
    }
    if (!parentArtifacts.isEmpty()) {
      final int answer = Messages.showYesNoDialog(myArtifactsEditor.getMainComponent(),
                                                  "Some elements come from included artifacts. Do you want to remove it?",
                                                  "Remove Elements", null);
      if (answer != 0) return false;
    }
    return true;
  }

  private PackagingElementNode<?> getParentNode(final LayoutTreeSelection selection) {
    final PackagingElementNode<?> node = selection.getNodeIfSingle();
    if (node != null) {
      if (node.getElementIfSingle() instanceof CompositePackagingElement) {
        return node;
      }
      final PackagingElementNode<?> parent = node.getParentNode();
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
        if (node != null && draggingObject.canDropInto(node)) {
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
      if (targetElement == null || targetNode == null || !draggingObject.checkCanDrop()) return;
      if (!checkCanAdd(null, targetElement, targetNode)) {
        return;
      }
      ensureRootIsWritable();
      draggingObject.beforeDrop();
      List<PackagingElement<?>> toSelect = new ArrayList<PackagingElement<?>>();
      for (PackagingElement<?> element : draggingObject.createPackagingElements(myContext)) {
        toSelect.add(element);
        targetElement.addOrFindChild(element);
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

  public void setRootElement(CompositePackagingElement<?> rootElement) {
    myContext.getModifiableArtifactModel().getOrCreateModifiableArtifact(myOriginalArtifact).setRootElement(rootElement);
    rebuildTree();
    myArtifactsEditor.getSourceItemsTree().rebuildTree();
  }

  public CompositePackagingElement<?> getRootElement() {
    return myContext.getRootElement(myOriginalArtifact);
  }

  public void initTree() {
    myBuilder.initRootNode();
    mySelectedElementInfo.showPropertiesPanel();
  }

  public void putIntoDefaultLocations(@NotNull List<? extends PackagingSourceItem> items) {
    ensureRootIsWritable();

    final CompositePackagingElement<?> rootElement = getArtifact().getRootElement();
    final ArtifactType artifactType = getArtifact().getArtifactType();
    List<PackagingElement<?>> toSelect = new ArrayList<PackagingElement<?>>();
    for (PackagingSourceItem item : items) {
      final String path = artifactType.getDefaultPathFor(item);
      if (path != null) {
        final CompositePackagingElement<?> directory = PackagingElementFactory.getInstance().getOrCreateDirectory(rootElement, path);
        final List<? extends PackagingElement<?>> elements = item.createElements(myContext);
        toSelect.addAll(directory.addOrFindChildren(elements));
      }
    }
    myArtifactsEditor.getSourceItemsTree().rebuildTree();
    updateAndSelect(myTree.getRootPackagingNode(), toSelect);
  }

  public boolean isPropertiesModified() {
    final PackagingElementPropertiesPanel panel = mySelectedElementInfo.myCurrentPanel;
    return panel != null && panel.isModified();
  }

  public void resetElementProperties() {
    final PackagingElementPropertiesPanel panel = mySelectedElementInfo.myCurrentPanel;
    if (panel != null) {
      panel.reset();
    }
  }

  private class SelectedElementInfo<E extends PackagingElement<?>> {
    private final E myElement;
    private PackagingElementPropertiesPanel myCurrentPanel;

    private SelectedElementInfo(@Nullable E element) {
      myElement = element;
      if (myElement != null) {
        //noinspection unchecked
        myCurrentPanel = element.getType().createElementPropertiesPanel(myElement, myContext);
        myPropertiesPanel.removeAll();
        if (myCurrentPanel != null) {
          myPropertiesPanel.add(BorderLayout.CENTER, myCurrentPanel.createComponent());
          myCurrentPanel.reset();
        }
      }
    }

    public void save() {
      if (myCurrentPanel != null && myCurrentPanel.isModified()) {
        ensureRootIsWritable();
        myCurrentPanel.apply();
      }
    }

    public void showPropertiesPanel() {
      final CardLayout cardLayout = (CardLayout)myPropertiesPanelWrapper.getLayout();
      if (myCurrentPanel != null) {
        cardLayout.show(myPropertiesPanelWrapper, PROPERTIES_CARD);
      }
      else {
        cardLayout.show(myPropertiesPanelWrapper, EMPTY_CARD);
      }
    }
  }

  private class LayoutTreeStructure extends SimpleTreeStructure {
    @Override
    public Object getRootElement() {
      return PackagingTreeNodeFactory.createRootNode(myArtifactsEditor, myContext, mySubstitutionParameters, getArtifact().getArtifactType());
    }
  }
}