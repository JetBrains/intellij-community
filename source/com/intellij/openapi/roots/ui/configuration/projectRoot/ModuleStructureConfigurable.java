/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.facet.Facet;
import com.intellij.facet.impl.ProjectFacetsConfigurator;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.ModuleGroupUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.ProjectConfigurable;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.navigation.Place;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.*;

/**
 * User: anna
 * Date: 02-Jun-2006
 */
@State(
  name = "ModuleStructureConfigurable.UI",
  storages = {
    @Storage(
      id ="other",
      file = "$WORKSPACE_FILE$"
    )}
)
public class ModuleStructureConfigurable extends BaseStructureConfigurable implements Place.Navigator {

  private static final Icon COMPACT_EMPTY_MIDDLE_PACKAGES_ICON = IconLoader.getIcon("/objectBrowser/compactEmptyPackages.png");
  private static final Icon ICON = IconLoader.getIcon("/modules/modules.png");

  private boolean myPlainMode;

  private final ModuleManager myModuleManager;

  private ProjectConfigurable myProjectConfigurable;

  private FacetEditorFacadeImpl myFacetEditorFacade = new FacetEditorFacadeImpl(this, TREE_UPDATER);
  @NonNls public static final String MODULE_TREE_OBJECT = "moduleTreeElement";
  private boolean myAutoScrollEnabled = true;

  public ModuleStructureConfigurable(Project project, ModuleManager manager) {
    super(project);
    myModuleManager = manager;
  }


  protected void initTree() {
    super.initTree();
    myTree.setRootVisible(false);
  }

  protected ArrayList<AnAction> getAdditionalActions() {
    final ArrayList<AnAction> result = new ArrayList<AnAction>();
    result.add(ActionManager.getInstance().getAction(IdeActions.GROUP_MOVE_MODULE_TO_GROUP));
    return result;
  }

  private void reloadTree() {
    myRoot.removeAllChildren();

    createProjectNodes();

    ((DefaultTreeModel)myTree.getModel()).reload();


    myUiDisposed = false;
  }

  protected void updateSelection(@Nullable final NamedConfigurable configurable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final String selectedTab = ModuleEditor.getSelectedTab();
    updateSelection(configurable, selectedTab);

    if (configurable != null) {
      myHistory.pushPlaceForElement(MODULE_TREE_OBJECT, configurable.getEditableObject());
    }
  }

  public ActionCallback navigateTo(@Nullable final Place place) {
    if (place == null) return new ActionCallback.Done();

    final Object object = place.getPath(MODULE_TREE_OBJECT);
    if (object == null) return new ActionCallback.Done();

    final MyNode node = findNodeByObject(myRoot, object);
    if (node == null) return new ActionCallback.Done();

    final NamedConfigurable config = node.getConfigurable();

    final ActionCallback result = new ActionCallback().doWhenDone(new Runnable() {
      public void run() {
        myAutoScrollEnabled = true;
      }
    });

    myAutoScrollEnabled = false;
    myAutoScrollHandler.cancelAllRequests();
    selectNodeInTree(node).doWhenDone(new Runnable() {
      public void run() {
        updateSelection(config);
        Place.goFurther(config, place).markDone(result);
      }
    });

    return result;
  }

  protected boolean isAutoScrollEnabled() {
    return myAutoScrollEnabled;
  }

  public void queryPlace(@NotNull final Place place) {
    if (myCurrentConfigurable != null) {
      place.putPath(MODULE_TREE_OBJECT, myCurrentConfigurable.getEditableObject());
      Place.queryFurther(myCurrentConfigurable, place);
    }
  }

  private void updateSelection(final NamedConfigurable configurable, final String selectedTab) {
    super.updateSelection(configurable);
    updateTabSelection(configurable, selectedTab);
  }

  private void updateTabSelection(final NamedConfigurable configurable, final String selectedTab) {
    if (configurable instanceof ModuleConfigurable){
      final ModuleConfigurable moduleConfigurable = (ModuleConfigurable)configurable;
      final ModuleEditor editor = moduleConfigurable.getModuleEditor();
      editor.init(selectedTab, getDetailsComponent().getChooseView(), myHistory);
    } else {
      getDetailsComponent().getChooseView().clear();
    }
  }



  private void createProjectNodes() {
    final Map<ModuleGroup, MyNode> moduleGroup2NodeMap = new HashMap<ModuleGroup, MyNode>();
    final Module[] modules = myModuleManager.getModules();
    for (final Module module : modules) {
      ModuleConfigurable configurable = new ModuleConfigurable(myContext.myModulesConfigurator, module, TREE_UPDATER);
      final MyNode moduleNode = new MyNode(configurable);
      //myFacetEditorFacade.addFacetsNodes(module, moduleNode);
      final String[] groupPath = myPlainMode ? null : myContext.myModulesConfigurator.getModuleModel().getModuleGroupPath(module);
      if (groupPath == null || groupPath.length == 0){
        addNode(moduleNode, myRoot);
      } else {
        final MyNode moduleGroupNode = ModuleGroupUtil
          .buildModuleGroupPath(new ModuleGroup(groupPath), myRoot, moduleGroup2NodeMap,
                                new Consumer<ModuleGroupUtil.ParentChildRelation<MyNode>>() {
                                  public void consume(final ModuleGroupUtil.ParentChildRelation<MyNode> parentChildRelation) {
                                    addNode(parentChildRelation.getChild(), parentChildRelation.getParent());
                                  }
                                },
                                new Function<ModuleGroup, MyNode>() {
                                  public MyNode fun(final ModuleGroup moduleGroup) {
                                    final NamedConfigurable moduleGroupConfigurable = new ModuleGroupConfigurable(moduleGroup);
                                    return new MyNode(moduleGroupConfigurable, true);
                                  }
                                });
        addNode(moduleNode, moduleGroupNode);
      }
    }
    if (myProject.isDefault()) {  //do not add modules node in case of template project
      myRoot.removeAllChildren();
    }

    final LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject);
    final LibrariesModifiableModel projectLibrariesProvider = new LibrariesModifiableModel(table);
    //myLevel2Providers.put(LibraryTablesRegistrar.PROJECT_LEVEL, projectLibrariesProvider);
    //
    //myProjectNode.add(myLevel2Nodes.get(LibraryTablesRegistrar.PROJECT_LEVEL));
  }

  public boolean updateProjectTree(final Module[] modules, final ModuleGroup group) {
    if (myRoot.getChildCount() == 0) return false; //isn't visible
    final MyNode [] nodes = new MyNode[modules.length];
    int i = 0;
    for (Module module : modules) {
      MyNode node = findModuleNode(module);
      LOG.assertTrue(node != null, "Module " + module.getName() + " is not in project.");
      node.removeFromParent();
      nodes[i ++] = node;
    }
    for (final MyNode moduleNode : nodes) {
      final String[] groupPath = myPlainMode
                                 ? null
                                 : group != null ? group.getGroupPath() : null;
      if (groupPath == null || groupPath.length == 0){
        addNode(moduleNode, myRoot);
      } else {
        final MyNode moduleGroupNode = ModuleGroupUtil
          .updateModuleGroupPath(new ModuleGroup(groupPath), myRoot, new Function<ModuleGroup, MyNode>() {
            @Nullable
            public MyNode fun(final ModuleGroup group) {
              return findNodeByObject(myRoot, group);
            }
          }, new Consumer<ModuleGroupUtil.ParentChildRelation<MyNode>>() {
            public void consume(final ModuleGroupUtil.ParentChildRelation<MyNode> parentChildRelation) {
              addNode(parentChildRelation.getChild(), parentChildRelation.getParent());
            }
          }, new Function<ModuleGroup, MyNode>() {
            public MyNode fun(final ModuleGroup moduleGroup) {
              final NamedConfigurable moduleGroupConfigurable = new ModuleGroupConfigurable(moduleGroup);
              return new MyNode(moduleGroupConfigurable, true);
            }
          });
        addNode(moduleNode, moduleGroupNode);
      }
    }
    ((DefaultTreeModel)myTree.getModel()).reload(myRoot);
    return true;
  }

  protected void addNode(MyNode nodeToAdd, MyNode parent) {
    parent.add(nodeToAdd);
    TreeUtil.sort(parent, new Comparator() {
      public int compare(final Object o1, final Object o2) {
        final MyNode node1 = (MyNode)o1;
        final MyNode node2 = (MyNode)o2;
        final Object editableObject1 = node1.getConfigurable().getEditableObject();
        final Object editableObject2 = node2.getConfigurable().getEditableObject();
        if (editableObject1.getClass() == editableObject2.getClass()) {
          return node1.getDisplayName().compareToIgnoreCase(node2.getDisplayName());
        }

        if (editableObject2 instanceof Module && editableObject1 instanceof ModuleGroup) return -1;
        if (editableObject1 instanceof Module && editableObject2 instanceof ModuleGroup) return 1;

        if (editableObject2 instanceof Module && editableObject1 instanceof String) return 1;
        if (editableObject1 instanceof Module && editableObject2 instanceof String) return -1;

        if (editableObject2 instanceof ModuleGroup && editableObject1 instanceof String) return 1;
        if (editableObject1 instanceof ModuleGroup && editableObject2 instanceof String) return -1;

        return 0;
      }
    });
    ((DefaultTreeModel)myTree.getModel()).reload(parent);
  }

  


  public void init(final StructureConfigrableContext context) {
    super.init(context);

    addItemsChangeListener(new ItemsChangeListener() {
      public void itemChanged(@Nullable Object deletedItem) {
        if (deletedItem instanceof Library) {
          final Library library = (Library)deletedItem;
          final MyNode node = findNodeByObject(myRoot, library);
          if (node != null) {
            final TreeNode parent = node.getParent();
            node.removeFromParent();
            ((DefaultTreeModel)myTree.getModel()).reload(parent);
          }
          myContext.invalidateModules(myContext.myLibraryDependencyCache.get(library));
        }
      }

      public void itemsExternallyChanged() {
        //do nothing
      }
    });
  }

  public void reset() {
    super.reset();

    myProjectConfigurable = myContext.myModulesConfigurator.getModulesConfigurable();

    reloadTree();

    super.reset();

    myContext.reset();
  }


  public void apply() throws ConfigurationException {
    final Set<MyNode> roots = new HashSet<MyNode>();
    roots.add(myRoot);
    if (!canApply(roots, ProjectBundle.message("rename.message.prefix.module"), ProjectBundle.message("rename.module.title"))) return;
    if (isInitialized(myProjectConfigurable) && myProjectConfigurable.isModified()) myProjectConfigurable.apply(); //do not reorder


    if (myContext.myModulesConfigurator.isModified()) myContext.myModulesConfigurator.apply();

    //cleanup
    myContext.myUpdateDependenciesAlarm.cancelAllRequests();
    myContext.myUpdateDependenciesAlarm.addRequest(new Runnable(){
      public void run() {
        SwingUtilities.invokeLater(new Runnable(){
          public void run() {
            if (myUiDisposed) return;
            dispose();
            reset();
          }
        });
      }
    }, 0);
  }

  public boolean isModified() {
    return myContext.myModulesConfigurator.isModified();
  }

  public void disposeUIResources() {
    super.disposeUIResources();
    myContext.myModulesConfigurator.disposeUIResources();
    ModuleStructureConfigurable.super.disposeUIResources();
  }

  public void dispose() {}


  public JComponent createComponent() {
    return new MyDataProviderWrapper(super.createComponent());
  }

  protected void processRemovedItems() {
    // do nothing
  }

  protected boolean wasObjectStored(Object editableObject) {
    return false;
  }

  public String getDisplayName() {
    return ProjectBundle.message("project.roots.display.name");
  }

  public Icon getIcon() {
    return ICON;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "root.settings";
  }



  public static ModuleStructureConfigurable getInstance(final Project project) {
    return ShowSettingsUtil.getInstance().findProjectConfigurable(project, ModuleStructureConfigurable.class);
  }

  public void setStartModuleWizard(final boolean show) {
    myContext.myModulesConfigurator.getModulesConfigurable().setStartModuleWizardOnShow(show);
  }



  public Project getProject() {
    return myProject;
  }


  public void selectFacetTab(@NotNull final Facet facet, @Nullable final String tabName) {
    NamedConfigurable confugurable = getSelectedConfugurable();
    if (confugurable instanceof ModuleConfigurable) {
      final ModuleEditor moduleEditor = ((ModuleConfigurable)confugurable).getModuleEditor();
      moduleEditor.navigateTo(
        new Place().putPath(ModuleEditor.MODULE_VIEW_KEY, facet.getName())
      ).doWhenDone(new Runnable() {
        public void run() {
          if (tabName != null) {
            moduleEditor.getOrCreateFacetEditor(facet).setSelectedTabName(tabName);
          }
        }
      });
    }

    final MyNode node = findNodeByObject(myRoot, facet);
    if (node != null) {
      selectNodeInTree(node);
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          FacetConfigurable moduleConfigurable = (FacetConfigurable)node.getConfigurable();
          moduleConfigurable.getEditor().setSelectedTabName(tabName);
        }
      });
    }
  }

  public void selectModuleTab(@NotNull final String moduleName, final String tabName) {
    final MyNode node = findModuleNode(ModuleManager.getInstance(myProject).findModuleByName(moduleName));
    if (node != null) {
      selectNodeInTree(node);
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          ModuleConfigurable moduleConfigurable = (ModuleConfigurable)node.getConfigurable();
          moduleConfigurable.getModuleEditor().setSelectedTabName(tabName);
        }
      });
    }
  }



  public Module[] getModules() {
    return myContext.myModulesConfigurator != null ? myContext.myModulesConfigurator.getModuleModel().getModules() : myModuleManager.getModules();
  }

  public void addLibraryOrderEntry(final Module module, final Library library) {
    final ModuleEditor moduleEditor = myContext.myModulesConfigurator.getModuleEditor(module);
    LOG.assertTrue(moduleEditor != null, "Current module editor was not initialized");
    final ModifiableRootModel modelProxy = moduleEditor.getModifiableRootModelProxy();
    final OrderEntry[] entries = modelProxy.getOrderEntries();
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry && Comparing.strEqual(entry.getPresentableName(), library.getName())) {
        if (Messages.showYesNoDialog(myWholePanel,
                                     ProjectBundle.message("project.roots.replace.library.entry.message", entry.getPresentableName()),
                                     ProjectBundle.message("project.roots.replace.library.entry.title"),
                                     Messages.getInformationIcon()) == DialogWrapper.OK_EXIT_CODE) {
          modelProxy.removeOrderEntry(entry);
          break;
        }
      }
    }
    modelProxy.addLibraryEntry(library);
    Set<String> modules = myContext.myLibraryDependencyCache.get(library);
    if (modules == null) {
      modules = new HashSet<String>();
      myContext.myLibraryDependencyCache.put(library, modules);
    }
    modules.add(module.getName());
    myTree.repaint();
  }

  @Nullable
  public MyNode findModuleNode(final Module module) {
    return findNodeByObject(myRoot, module);
  }

  public FacetEditorFacadeImpl getFacetEditorFacade() {
    return myFacetEditorFacade;
  }

  public ProjectFacetsConfigurator getFacetConfigurator() {
    return myContext.myModulesConfigurator.getFacetsConfigurator();
  }

  public void addModule() {
    final Module module = myContext.myModulesConfigurator.addModule(myTree);
    if (module != null) {
      final MasterDetailsComponent.MyNode node = new MasterDetailsComponent.MyNode(new ModuleConfigurable(myContext.myModulesConfigurator, module, TREE_UPDATER));
      final TreePath selectionPath = myTree.getSelectionPath();
      MyNode parent = null;
      if (selectionPath != null) {
        MyNode selected = (MyNode)selectionPath.getLastPathComponent();
        final Object o = selected.getConfigurable().getEditableObject();
        if (o instanceof ModuleGroup) {
          myContext.myModulesConfigurator.getModuleModel().setModuleGroupPath(module, ((ModuleGroup)o).getGroupPath());
          parent = selected;
        } else if (o instanceof Module) { //create near selected
          final ModifiableModuleModel modifiableModuleModel = myContext.myModulesConfigurator.getModuleModel();
          final String[] groupPath = modifiableModuleModel.getModuleGroupPath((Module)o);
          if (groupPath != null) {
            modifiableModuleModel.setModuleGroupPath(module, groupPath);
            parent = findNodeByObject(myRoot, new ModuleGroup(groupPath));
          }
        }
      }
      if (parent == null) parent = myRoot;
      addNode(node, parent);
      //myFacetEditorFacade.addFacetsNodes(module, node);
      ((DefaultTreeModel)myTree.getModel()).reload(parent);
      selectNodeInTree(node);
      myContext.myValidityCache.clear(); //missing modules added
    }
  }

  @Nullable
  public Module getSelectedModule() {
    final Object selectedObject = getSelectedObject();
    if (selectedObject instanceof Module) {
      return (Module)selectedObject;
    }
    if (selectedObject instanceof Library) {
      if (((Library)selectedObject).getTable() == null) {
        final MyNode node = (MyNode)myTree.getSelectionPath().getLastPathComponent();
        return (Module)((MyNode)node.getParent()).getConfigurable().getEditableObject();
      }
    }
    return null;
  }

  @NonNls
  public String getId() {
    return "project.structure";
  }

  public boolean clearSearch() {
    return false;
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }


  public String getCompilerOutputUrl() {
    return isInitialized(myProjectConfigurable) ? myProjectConfigurable.getCompilerOutputUrl() : ProjectRootManager.getInstance(myProject).getCompilerOutputUrl();
  }

  @Nullable
  public Module getModule(final String moduleName) {
    if (moduleName == null) return null;
    return (myContext != null && myContext.myModulesConfigurator != null) ? myContext.myModulesConfigurator.getModule(moduleName) : myModuleManager.findModuleByName(moduleName);
  }

  public StructureConfigrableContext getContext() {
    return myContext;
  }


  private class MyDataProviderWrapper extends JPanel implements DataProvider {
    public MyDataProviderWrapper(final JComponent component) {
      super(new BorderLayout());
      add(component, BorderLayout.CENTER);
    }

    @Nullable
    public Object getData(@NonNls String dataId) {
      if (DataConstants.MODULE_CONTEXT_ARRAY.equals(dataId)){
        final TreePath[] paths = myTree.getSelectionPaths();
        if (paths != null) {
          ArrayList<Module> modules = new ArrayList<Module>();
          for (TreePath path : paths) {
            MyNode node = (MyNode)path.getLastPathComponent();
            final NamedConfigurable configurable = node.getConfigurable();
            LOG.assertTrue(configurable != null, "already disposed");
            final Object o = configurable.getEditableObject();
            if (o instanceof Module) {
              modules.add((Module)o);
            }
          }
          return !modules.isEmpty() ? modules.toArray(new Module[modules.size()]) : null;
        }
      }
      if (DataConstants.MODULE_CONTEXT.equals(dataId)){
        return getSelectedModule();
      }
      if (DataConstantsEx.MODIFIABLE_MODULE_MODEL.equals(dataId)){
        return myContext.myModulesConfigurator.getModuleModel();
      }

      return null;
    }
  }


  private class MyGroupAction extends ToggleAction {

    public MyGroupAction() {
      super("", "", COMPACT_EMPTY_MIDDLE_PACKAGES_ICON);
    }

    public void update(final AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      String text = ProjectBundle.message("project.roots.plain.mode.action.text.disabled");
      if (myPlainMode){
        text = ProjectBundle.message("project.roots.plain.mode.action.text.enabled");
      }
      presentation.setText(text);
      presentation.setDescription(text);

      if (myContext.myModulesConfigurator != null) {
        presentation.setVisible(myContext.myModulesConfigurator.getModuleModel().hasModuleGroups());
      }
    }

    public boolean isSelected(AnActionEvent e) {
      return myPlainMode;
    }

    public void setSelected(AnActionEvent e, boolean state) {
      myPlainMode = state;
      DefaultMutableTreeNode selection = null;
      final TreePath selectionPath = myTree.getSelectionPath();
      if (selectionPath != null){
        selection = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
      }
      final ModifiableModuleModel model = myContext.myModulesConfigurator.getModuleModel();
      final Module[] modules = model.getModules();
      for (Module module : modules) {
        final String[] groupPath = model.getModuleGroupPath(module);
        updateProjectTree(new Module[]{module}, groupPath != null ? new ModuleGroup(groupPath) : null);
      }
      if (state) {
        removeModuleGroups();
      }
      if (selection != null){
        TreeUtil.selectInTree(selection, true, myTree);
      }
    }

    private void removeModuleGroups() {
      for(int i = myRoot.getChildCount() - 1; i >=0; i--){
        final MyNode node = (MyNode)myRoot.getChildAt(i);
        if (node.getConfigurable().getEditableObject() instanceof ModuleGroup){
          node.removeFromParent();
        }
      }
      ((DefaultTreeModel)myTree.getModel()).reload(myRoot);
    }
  }

  protected AbstractAddGroup createAddAction() {
    return new AbstractAddGroup(ProjectBundle.message("add.new.module.text.full")) {
      public AnAction[] getChildren(@Nullable final AnActionEvent e) {
        return new AnAction[] {
          new AnAction(ProjectBundle.message("add.new.module.text.full")) {
            public void actionPerformed(final AnActionEvent e) {
              addModule();
            }
          }
        };
      }
    };
  }

  protected boolean removeModule(final Module module) {
    if (!myContext.myModulesConfigurator.deleteModule(module)) {
      //wait for confirmation
      return true;
    }
    myContext.myValidityCache.remove(module);
    myContext.invalidateModules(myContext.myModulesDependencyCache.get(module));
    myContext.myModulesDependencyCache.remove(module);
    return false;
  }

  protected
  @Nullable
  String getEmptySelectionString() {
    return "Select a module to view or edit its details here";
  }
}
