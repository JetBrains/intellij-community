package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.deployment.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.FindUsagesInProjectStructureActionBase;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * @author nik
 */
public class PackagingEditorImpl implements PackagingEditor {
  private static final Convertor<TreePath, String> SPEED_SEARCH_CONVERTOR = new Convertor<TreePath, String>() {
    public String convert(final TreePath path) {
      Object o = path.getLastPathComponent();
      if (o instanceof PackagingTreeNode) {
        return ((PackagingTreeNode)o).getSearchName();
      }
      return "";
    }
  };
  private final PackagingConfiguration myOriginalConfiguration;
  private final PackagingConfiguration myModifiedConfiguration;
  private final ModulesProvider myModulesProvider;
  private final FacetsProvider myFacetsProvider;
  private final PackagingEditorPolicy myPolicy;
  private Tree myTree;
  private RootNode myRoot;
  private DefaultTreeModel myTreeModel;
  private final PackagingTreeBuilder myBuilder;
  private JPanel myMainPanel;
  private JPanel myTreePanel;
  private JButton myAddButton;
  private JButton myRemoveButton;
  private JButton myEditButton;
  private JCheckBox myShowIncludedCheckBox;
  private JCheckBox myShowLibraryFilesCheckBox;
  private PackagingArtifact myRootArtifact;
  private Project myProject;
  private PackagingTreeParameters myTreeParameters;

  public PackagingEditorImpl(final PackagingConfiguration originalConfiguration,
                             final PackagingConfiguration modifiedConfiguration,
                             final ModulesProvider modulesProvider, final FacetsProvider facetsProvider, final PackagingEditorPolicy policy,
                             final PackagingTreeBuilder builder) {
    myOriginalConfiguration = originalConfiguration;
    myModifiedConfiguration = modifiedConfiguration;
    myModulesProvider = modulesProvider;
    myFacetsProvider = facetsProvider;
    myPolicy = policy;
    myBuilder = builder;
    myProject = myPolicy.getModule().getProject();
    setTreeParameters(myBuilder.getDefaultParameters());

    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        JBPopupFactory.getInstance().createListPopup(createAddActionsPopup()).showUnderneathOf(myAddButton);
      }
    });
    myEditButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        ContainerElement element = getSelectedElement();
        if (element != null) {
          editElement(element);
        }
      }
    });
    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        Pair<List<ContainerElement>, Set<PackagingArtifact>> pair = getSelectedElements();
        List<ContainerElement> toRemove = pair.getFirst();
        Set<PackagingArtifact> owners = pair.getSecond();

        if (!owners.isEmpty()) {
          String message;
          if (owners.size() == 1 && toRemove.isEmpty()) {
            PackagingArtifact artifact = owners.iterator().next();
            message = ProjectBundle.message("message.text.packaging.selected.item.belongs.to.0.do.you.want.to.exlude.1.from.2",
                                            artifact.getDisplayName(), artifact.getDisplayName(), myRootArtifact.getDisplayName());
          }
          else {
            StringBuilder ownersBuffer = new StringBuilder();
            for (PackagingArtifact owner : owners) {
              if (ownersBuffer.length() > 0) {
                ownersBuffer.append(", ");
              }
              ownersBuffer.append(owner.getDisplayName());
            }
            message = ProjectBundle .message("message.text.packaging.do.you.want.to.exlude.0.from.1", ownersBuffer, myRootArtifact.getDisplayName());
          }
          int answer = Messages.showYesNoDialog(myMainPanel, message, ProjectBundle.message("dialog.title.packaging.remove.included"), null);
          if (answer != 0) {
            return;
          }
        }

        saveData();
        for (ContainerElement containerElement : toRemove) {
          myModifiedConfiguration.removeContainerElement(containerElement);
        }
        for (PackagingArtifact owner : owners) {
          ContainerElement element = owner.getContainerElement();
          if (element != null) {
            myModifiedConfiguration.removeContainerElement(element);
          }
        }
        rebuildTree();
      }
    });
    ActionListener actionListener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        myTreeParameters = new PackagingTreeParameters(myShowIncludedCheckBox.isSelected(), myShowLibraryFilesCheckBox.isSelected());
        myBuilder.updateParameters(myTreeParameters);
        rebuildTree();
      }
    };
    myShowIncludedCheckBox.addActionListener(actionListener);
    myShowLibraryFilesCheckBox.addActionListener(actionListener);
  }

  @Nullable
  private ContainerElement getSelectedElement() {
    Pair<List<ContainerElement>, Set<PackagingArtifact>> pair = getSelectedElements();
    List<ContainerElement> elements = pair.getFirst();
    Set<PackagingArtifact> artifacts = pair.getSecond();
    if (elements.size() == 1) {
      return elements.get(0);
    }
    else if (artifacts.size() == 1){
      ContainerElement element = artifacts.iterator().next().getContainerElement();
      if (element != null) {
        return element;
      }
    }
    return null;
  }

  private Pair<List<ContainerElement>, Set<PackagingArtifact>> getSelectedElements() {
    PackagingTreeNode[] treeNodes = myTree.getSelectedNodes(PackagingTreeNode.class, null);
    Set<PackagingArtifact> owners = new HashSet<PackagingArtifact>();
    List<ContainerElement> elements = new ArrayList<ContainerElement>();
    for (PackagingTreeNode treeNode : treeNodes) {
      ContainerElement containerElement = treeNode.getContainerElement();
      if (containerElement == null) continue;
      
      PackagingArtifact owner = treeNode.getOwner();
      if (owner != null && owner.getContainerElement() != null) {
        owners.add(owner);
      }
      else {
        elements.add(containerElement);
      }
    }
    return Pair.create(elements, owners);
  }

  public void addModules(final List<Module> modules) {
    if (modules.isEmpty()) return;

    saveData();
    modules.removeAll(Arrays.asList(myModifiedConfiguration.getContainingIdeaModules()));
    ContainerElement last = null;
    for (Module module : modules) {
      ModuleLink element = DeploymentUtil.getInstance().createModuleLink(module, myPolicy.getModule());
      addElement(element);
      last = element;
    }
    rebuildTree();
    if (last != null) {
      selectElement(last, false);
    }
  }


  private void editElement(final @NotNull ContainerElement element) {
    saveData();
    boolean ok = PackagingElementPropertiesComponent.showDialog(element, myMainPanel, myPolicy);
    if (ok) {
      rebuildTree();
      selectElement(element, false);
    }
  }

  public void addElement(final ContainerElement element) {
    if (myPolicy.isAllowedToPackage(element)) {
      myPolicy.setDefaultAttributes(element);
      PackagingMethod method = element.getPackagingMethod();
      if (method == PackagingMethod.DO_NOT_PACKAGE) {
        //todo[nik] is it correct?
        PackagingMethod[] methods = myPolicy.getAllowedPackagingMethods(element);
        element.setPackagingMethod(methods[0]);
        element.setURI(myPolicy.suggestDefaultRelativePath(element));
      }
      myModifiedConfiguration.addOrReplaceElement(element);
    }
  }

  public void selectElement(@NotNull final ContainerElement toSelect, final boolean requestFocus) {
    PackagingTreeNode node = findNodeByElement(toSelect);
    if (node != null) {
      TreeUtil.selectNode(myTree, node);
      if (requestFocus) {
        IdeFocusManager.getInstance(myProject).requestFocus(myTree, true);
      }
    }
  }

  public void processNewOrderEntries(final Set<OrderEntry> newEntries) {
    List<Library> libraries = new ArrayList<Library>();
    for (OrderEntry entry : newEntries) {
      if (entry instanceof LibraryOrderEntry) {
        libraries.add(((LibraryOrderEntry)entry).getLibrary());
      }
    }
    myPolicy.processNewLibraries(this, libraries);
  }

  @Nullable
  private PackagingTreeNode findNodeByElement(final ContainerElement toSelect) {
    final Ref<PackagingTreeNode> ref = Ref.create(null);
    TreeUtil.traverseDepth(myRoot, new TreeUtil.Traverse() {
      public boolean accept(final Object node) {
        if (node instanceof PackagingTreeNode) {
          PackagingTreeNode packagingNode = (PackagingTreeNode)node;
          ContainerElement element = packagingNode.getContainerElement();
          if (toSelect.equals(element)) {
            ref.set(packagingNode);
            return false;
          }
        }
        return true;
      }
    });
    return ref.get();
  }

  public Tree getTree() {
    return myTree;
  }

  public void addLibraries(final List<Library> libraries) {
    if (libraries.isEmpty()) return;

    saveData();
    libraries.removeAll(Arrays.asList(myModifiedConfiguration.getContainingLibraries()));
    ContainerElement last = null;
    for (Library library : libraries) {
      LibraryLink libraryLink = DeploymentUtil.getInstance().createLibraryLink(library, myPolicy.getModule());
      addElement(libraryLink);
      last = libraryLink;
    }
    rebuildTree();
    if (last != null) {
      selectElement(last, false);
    }
  }

  private BaseListPopupStep<AddPackagingElementAction> createAddActionsPopup() {
    return new BaseListPopupStep<AddPackagingElementAction>(null, myPolicy.getAddActions()) {
      @Override
      public PopupStep onChosen(final AddPackagingElementAction selectedValue, final boolean finalChoice) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            selectedValue.perform(PackagingEditorImpl.this);
          }
        }, ModalityState.stateForComponent(myMainPanel));
        return FINAL_CHOICE;
      }

      @Override
      public boolean isSelectable(final AddPackagingElementAction value) {
        return value.isEnabled(PackagingEditorImpl.this);
      }

      @Override
      public Icon getIconFor(final AddPackagingElementAction aValue) {
        return aValue.getIcon();
      }

      @NotNull
      @Override
      public String getTextFor(final AddPackagingElementAction value) {
        return value.getText();
      }
    };
  }

  public void setTreeParameters(PackagingTreeParameters parameters) {
    myShowIncludedCheckBox.setSelected(parameters.isShowIncludedContent());
    myShowLibraryFilesCheckBox.setSelected(parameters.isShowLibraryFiles());
    myTreeParameters = parameters;
  }

  public void rebuildTree() {
    PackagingTreeState state = PackagingTreeState.saveState(myTree);
    myRoot.removeAllChildren();
    myRootArtifact = myBuilder.createRootArtifact();
    PackagingArtifactNode root = PackagingTreeNodeFactory.createArtifactNode(myRootArtifact, myRoot, null);
    for (ContainerElement element : getPackagedElements()) {
      myBuilder.createNodes(root, element, null, myTreeParameters);
    }
    myTreeModel.nodeStructureChanged(myRoot);
    TreeUtil.sort(myTreeModel, new Comparator<PackagingTreeNode>() {
      public int compare(final PackagingTreeNode node1, final PackagingTreeNode node2) {
        double weight1 = node1.getWeight();
        double weight2 = node2.getWeight();
        if (weight1 < weight2) return -1;
        if (weight1 > weight2) return 1;

        return node1.compareTo(node2);
      }
    });
    state.restoreState(myTree);
  }

  public void saveData() {

  }

  public void moduleStateChanged() {
    if (myPolicy.removeObsoleteElements(this)) {
      rebuildTree();
    }
  }

  public PackagingConfiguration getModifiedConfiguration() {
    return myModifiedConfiguration;
  }

  public ContainerElement[] getModifiedElements() {
    return myModifiedConfiguration.getElements(myModulesProvider, myFacetsProvider, true, true, true);
  }

  public boolean isModified() {
    final ContainerElement[] elements1 = getPackagedElements();
    final ContainerElement[] elements2 = myOriginalConfiguration.getElements(myModulesProvider, myFacetsProvider, true, true, false);
    return !Comparing.haveEqualElements(elements1, elements2);
  }

  private ContainerElement[] getPackagedElements() {
    return myModifiedConfiguration.getElements(myModulesProvider, myFacetsProvider, true, true, false);
  }

  public void reset() {
    final ContainerElement[] elements = myOriginalConfiguration.getElements(myModulesProvider, myFacetsProvider, true, true, true);
    ContainerElement[] newElements = new ContainerElement[elements.length];
    for (int i = 0; i < elements.length; i++) {
      newElements[i] = elements[i].clone();
    }
    myModifiedConfiguration.setElements(newElements);
    if (myTree != null) {
      rebuildTree();
    }
  }

  public JComponent createMainComponent() {
    myMainPanel.setMinimumSize(new Dimension(-1, 250));
    myRoot = new RootNode();
    myTreeModel = new DefaultTreeModel(myRoot);
    myTree = new Tree(myTreeModel) {
      @Override
      public String getToolTipText(final MouseEvent event) {
        TreePath path = myTree.getPathForLocation(event.getX(), event.getY());
        if (path != null) {
          return ((PackagingTreeNode)path.getLastPathComponent()).getTooltipText();
        }
        return super.getToolTipText();
      }
    };
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.setCellRenderer(new PackagingTreeCellRenderer());
    myTreePanel.add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);
    new TreeSpeedSearch(myTree, SPEED_SEARCH_CONVERTOR, true);
    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(final TreeSelectionEvent e) {
        updateButtons();
      }
    });
    myTree.addMouseListener(new PackagingTreeMouseListener());

    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new MyNavigateAction());
    actionGroup.add(new MyFindUsagesAction());

    actionGroup.add(Separator.getInstance());
    CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    DefaultTreeExpander treeExpander = new DefaultTreeExpander(myTree);
    actionGroup.add(actionsManager.createExpandAllAction(treeExpander, myTree));
    actionGroup.add(actionsManager.createCollapseAllAction(treeExpander, myTree));

    PopupHandler.installPopupHandler(myTree, actionGroup, ActionPlaces.UNKNOWN, ActionManager.getInstance());
    TreeToolTipHandler.install(myTree);
    ToolTipManager.sharedInstance().registerComponent(myTree);
    rebuildTree();
    TreeUtil.expandAll(myTree);
    updateButtons();
    return myMainPanel;
  }

  private void updateButtons() {
    Pair<List<ContainerElement>, Set<PackagingArtifact>> pair = getSelectedElements();
    List<ContainerElement> elements = pair.getFirst();
    Set<PackagingArtifact> artifacts = pair.getSecond();
    myRemoveButton.setEnabled(!elements.isEmpty() || !artifacts.isEmpty());
    ContainerElement selectedElement = getSelectedElement();
    myEditButton.setEnabled(selectedElement != null && PackagingElementPropertiesComponent.isEnabled(selectedElement, myPolicy));
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  public PackagingTreeNode getRoot() {
    return myRoot;
  }

  private void navigate(PackagingTreeNode treeNode) {
    treeNode.navigate(ModuleStructureConfigurable.getInstance(myProject));
  }

  private static class RootNode extends PackagingTreeNode {
    private RootNode() {
      super(null);
    }

    @NotNull
    public String getOutputFileName() {
      return "";
    }

    public double getWeight() {
      return 0;
    }

    public void render(final ColoredTreeCellRenderer renderer) {
    }

    public boolean canNavigate() {
      return false;
    }

    public void navigate(final ModuleStructureConfigurable configurable) {
    }

    public Object getSourceObject() {
      return null;
    }
  }

  private class PackagingTreeMouseListener extends MouseAdapter {
    @Override
    public void mouseClicked(final MouseEvent e) {
      if (e.getClickCount() == 2) {
        PackagingTreeNode[] nodes = myTree.getSelectedNodes(PackagingTreeNode.class, null);
        if (nodes.length == 1) {
          PackagingTreeNode node = nodes[0];
          if (node.getChildren().isEmpty()) {
            ContainerElement element = node.getContainerElement();
            if (element != null) {
              if (node.getOwner() == null) {
                editElement(element);
              }
              else {
                navigate(node);
              }
            }
          }
        }
      }
    }
  }

  private static class PackagingTreeCellRenderer extends ColoredTreeCellRenderer {
    public void customizeCellRenderer(final JTree tree,
                                      final Object value,
                                      final boolean selected,
                                      final boolean expanded,
                                      final boolean leaf,
                                      final int row,
                                      final boolean hasFocus) {
      PackagingTreeNode node = (PackagingTreeNode)value;
      node.render(this);
    }
  }

  private class MyNavigateAction extends AnAction {
    private MyNavigateAction() {
      super(ProjectBundle.message("action.name.facet.navigate"));
      registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE).getShortcutSet(), myTree);
    }

    public void update(final AnActionEvent e) {
      PackagingTreeNode[] treeNodes = myTree.getSelectedNodes(PackagingTreeNode.class, null);
      e.getPresentation().setEnabled(treeNodes.length == 1 && treeNodes[0].canNavigate());
    }

    public void actionPerformed(final AnActionEvent e) {
      PackagingTreeNode[] treeNodes = myTree.getSelectedNodes(PackagingTreeNode.class, null);
      if (treeNodes.length == 1) {
        navigate(treeNodes[0]);
      }
    }
  }

  private class MyFindUsagesAction extends FindUsagesInProjectStructureActionBase {
    public MyFindUsagesAction() {
      super(myTree, myProject);
    }

    protected boolean isEnabled() {
      PackagingTreeNode[] treeNodes = myTree.getSelectedNodes(PackagingTreeNode.class, null);
      return treeNodes.length == 1 && treeNodes[0].getSourceObject() != null;
    }

    protected Object getSelectedObject() {
      PackagingTreeNode[] treeNodes = myTree.getSelectedNodes(PackagingTreeNode.class, null);
      return treeNodes.length == 1 ? treeNodes[0].getSourceObject() : null;
    }

    protected RelativePoint getPointToShowResults() {
      final int selectedRow = myTree.getSelectionRows()[0];
      final Rectangle rowBounds = myTree.getRowBounds(selectedRow);
      final Point location = rowBounds.getLocation();
      location.y += rowBounds.height;
      return new RelativePoint(myTree, location);
    }
  }
}
