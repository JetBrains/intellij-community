package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.deployment.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
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
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TreeSpeedSearch;
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
  private PackagingArtifact myRootArtifact;
  private Project myProject;

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
          StringBuilder ownersBuffer = new StringBuilder();
          for (PackagingArtifact owner : owners) {
            if (ownersBuffer.length() > 0) {
              ownersBuffer.append(", ");
            }
            ownersBuffer.append(owner.getDisplayName());
          }

          String message;
          if (owners.size() == 1 && toRemove.isEmpty()) {
            PackagingArtifact artifact = owners.iterator().next();
            message = ProjectBundle.message("message.text.packaging.selected.item.belongs.to.0.do.you.want.to.exlude.1.from.2",
                                            artifact.getDisplayName(), artifact.getDisplayName(), myRootArtifact.getDisplayName());
          }
          else {
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
      PackagingArtifact owner = treeNode.getOwner();
      if (owner != null && owner.getContainerElement() != null) {
        owners.add(owner);
      }
      else if (containerElement != null) {
        elements.add(containerElement);
      }
    }
    return Pair.create(elements, owners);
  }

  public void addModules(final List<Module> modules) {
    if (modules.isEmpty()) return;

    saveData();
    modules.removeAll(Arrays.asList(myModifiedConfiguration.getContainingIdeaModules()));
    for (Module module : modules) {
      ModuleLink element = DeploymentUtil.getInstance().createModuleLink(module, myPolicy.getModule());
      addElement(element);
    }
    rebuildTree();
  }


  private void editElement(final @NotNull ContainerElement element) {
    saveData();
    boolean ok = PackagingElementPropertiesComponent.showDialog(element, myMainPanel, myPolicy);
    if (ok) {
      rebuildTree();
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

  public void addLibraries(final List<Library> libraries) {
    if (libraries.isEmpty()) return;

    saveData();
    libraries.removeAll(Arrays.asList(myModifiedConfiguration.getContainingLibraries()));
    for (Library library : libraries) {
      LibraryLink libraryLink = DeploymentUtil.getInstance().createLibraryLink(library, myPolicy.getModule());
      addElement(libraryLink);
    }
    rebuildTree();
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

  public void rebuildTree() {
    myRoot.removeAllChildren();
    myRootArtifact = myBuilder.createRootArtifact();
    PackagingArtifactNode root = PackagingTreeNodeFactory.createArtifactNode(myRootArtifact, myRoot, null);
    for (ContainerElement element : getPackagedElements()) {
      myBuilder.createNodes(root, element, null);
    }
    myTreeModel.nodeStructureChanged(myRoot);
    TreeUtil.expandAll(myTree);
  }

  public void saveData() {

  }

  public void moduleStateChanged() {

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
    myRoot = new RootNode();
    myTreeModel = new DefaultTreeModel(myRoot);
    myTree = new Tree(myTreeModel);
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
    myTree.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(final MouseEvent e) {
        if (e.getClickCount() == 2) {
          PackagingTreeNode[] nodes = myTree.getSelectedNodes(PackagingTreeNode.class, null);
          if (nodes.length == 1) {
            PackagingTreeNode node = nodes[0];
            if (node.getChildren().isEmpty()) {
              ContainerElement element = node.getContainerElement();
              if (element != null && node.getOwner() == null) {
                editElement(element);
              }
            }
          }
        }
      }
    });

    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new MyNavigateAction());
    actionGroup.add(new MyFindUsagesAction());
    PopupHandler.installPopupHandler(myTree, actionGroup, ActionPlaces.UNKNOWN, ActionManager.getInstance());
    rebuildTree();
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

  private static class RootNode extends PackagingTreeNode {
    private RootNode() {
      super(null);
    }

    @NotNull
    protected String getOutputFileName() {
      return "";
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

  private static class PackagingTreeCellRenderer extends ColoredTreeCellRenderer {
    public void customizeCellRenderer(final JTree tree,
                                      final Object value,
                                      final boolean selected,
                                      final boolean expanded,
                                      final boolean leaf,
                                      final int row,
                                      final boolean hasFocus) {
      ((PackagingTreeNode)value).render(this);
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
        treeNodes[0].navigate(ModuleStructureConfigurable.getInstance(myProject));
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
