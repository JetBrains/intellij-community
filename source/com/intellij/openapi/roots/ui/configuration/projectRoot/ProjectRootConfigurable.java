/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.CommonBundle;
import com.intellij.javaee.serverInstances.ApplicationServersManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryTableEditor;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.Icons;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

/**
 * User: anna
 * Date: 02-Jun-2006
 */
public class ProjectRootConfigurable extends MasterDetailsComponent implements ProjectComponent {
  private static final Icon ICON = IconLoader.getIcon("/modules/modules.png");

  private MyNode myJdksNode;

  private MyNode myGlobalLibrariesNode;
  private LibrariesModifiableModel myGlobalLibrariesProvider;

  private LibrariesModifiableModel myProjectLibrariesProvider;

  private Map<Module, LibrariesModifiableModel> myModule2LibrariesMap = new HashMap<Module, LibrariesModifiableModel>();

  private MyNode myProjectNode;
  private MyNode myProjectLibrariesNode;
  private Project myProject;

  private ModuleManager myModuleManager;
  private ModulesConfigurator myModulesConfigurator;
  private ProjectJdksModel myJdksTreeModel = new ProjectJdksModel(this);

  private MyNode myApplicationServerLibrariesNode;
  private LibrariesModifiableModel myApplicationServerLibrariesProvider;

  public ProjectRootConfigurable(Project project, ModuleManager manager) {
    myProject = project;
    myModuleManager = manager;
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
        }
      }

      public void itemsExternallyChanged() {
        //do nothing
      }
    });
    initTree();
  }


  protected void initTree() {
    super.initTree();
    new TreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      public String convert(final TreePath treePath) {
        return ((MyNode)treePath.getLastPathComponent()).getDisplayName();
      }
    });
  }

  protected void reloadTree() {

    myRoot.removeAllChildren();

    myGlobalLibrariesNode = createLibrariesNode(LibraryTablesRegistrar.getInstance().getLibraryTable(), myGlobalLibrariesProvider, getGlobalLibrariesProvider());

    myApplicationServerLibrariesNode = createLibrariesNode(ApplicationServersManager.getInstance().getLibraryTable(), myApplicationServerLibrariesProvider, getApplicationServerLibrariesProvider());

    createProjectJdks();

    createProjectNodes();

    ((DefaultTreeModel)myTree.getModel()).reload();
  }

  private MyNode createLibrariesNode(final LibraryTable table,
                                     LibrariesModifiableModel provider,
                                     final LibraryTableModifiableModelProvider modelProvider) {
    provider = new LibrariesModifiableModel(table.getModifiableModel());
    LibrariesConfigurable librariesConfigurable = new LibrariesConfigurable(table.getTableLevel(), provider);
    MyNode node = new MyNode(librariesConfigurable, false);
    final Library[] libraries = provider.getLibraries();
    for (Library library : libraries) {
      addNode(new MyNode(new LibraryConfigurable(modelProvider, library, myProject), true), node);
    }
    myRoot.add(node);
    return node;
  }

  private void createProjectJdks() {
    myJdksNode = new MyNode(new JdksConfigurable(myJdksTreeModel), false);
    final TreeMap<ProjectJdk, ProjectJdk> sdks = myJdksTreeModel.getProjectJdks();
    for (ProjectJdk sdk : sdks.keySet()) {
      final JdkConfigurable configurable = new JdkConfigurable((ProjectJdkImpl)sdks.get(sdk), myJdksTreeModel);
      addNode(new MyNode(configurable, true), myJdksNode);
    }
    myRoot.add(myJdksNode);
  }

  private void createProjectNodes() {
    myProjectNode = new MyNode(myModulesConfigurator, false);
    final Module[] modules = myModuleManager.getModules();
    for (final Module module : modules) {
      ModuleConfigurable configurable = new ModuleConfigurable(myModulesConfigurator, module);
      final MyNode moduleNode = new MyNode(configurable, true);
      createModuleLibraries(module, moduleNode);
      addNode(moduleNode, myProjectNode);
    }

    final LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject);
    myProjectLibrariesProvider = new LibrariesModifiableModel(table.getModifiableModel());
    final LibrariesConfigurable librariesConfigurable = new LibrariesConfigurable(table.getTableLevel(), myProjectLibrariesProvider);

    myProjectLibrariesNode = new MyNode(librariesConfigurable, false);
    final Library[] libraries = myProjectLibrariesProvider.getLibraries();
    for (Library library1 : libraries) {
      addNode(new MyNode(new LibraryConfigurable(getProjectLibrariesProvider(), library1, myProject), true), myProjectLibrariesNode);
    }
    myProjectNode.add(myProjectLibrariesNode);

    myRoot.add(myProjectNode);
  }

  private void createModuleLibraries(final Module module, final MyNode moduleNode) {
    final LibraryTableModifiableModelProvider libraryTableModelProvider = new LibraryTableModifiableModelProvider() {
      public LibraryTable.ModifiableModel getModifiableModel() {
        return myModule2LibrariesMap.get(module);
      }

      public String getTableLevel() {
        return LibraryTableImplUtil.MODULE_LEVEL;
      }
    };

    final OrderEntry[] entries = myModulesConfigurator.getModuleEditor(module).getModifiableRootModel().getOrderEntries();
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry) {
        final LibraryOrderEntry orderEntry = (LibraryOrderEntry)entry;
        if (orderEntry.isModuleLevel()) {
          final Library library = orderEntry.getLibrary();
          final LibraryConfigurable libraryConfigurable =
            new LibraryConfigurable(libraryTableModelProvider, library, orderEntry.getPresentableName(), myProject);
          addNode(new MyNode(libraryConfigurable, true), moduleNode);
        }
      }
    }
  }


  public ProjectJdksModel getProjectJdksModel() {
    return myJdksTreeModel;
  }

  public LibraryTableModifiableModelProvider getGlobalLibrariesProvider() {
    return new LibraryTableModifiableModelProvider() {
      public LibraryTable.ModifiableModel getModifiableModel() {
        return myGlobalLibrariesProvider;
      }

      public String getTableLevel() {
        return LibraryTablesRegistrar.APPLICATION_LEVEL;
      }
    };
  }

  public LibraryTableModifiableModelProvider getProjectLibrariesProvider() {
    return new LibraryTableModifiableModelProvider() {
      public LibraryTable.ModifiableModel getModifiableModel() {
        return myProjectLibrariesProvider;
      }

      public String getTableLevel() {
        return LibraryTablesRegistrar.PROJECT_LEVEL;
      }
    };
  }

  public void reset() {
    myJdksTreeModel.reset();
    myModulesConfigurator = new ModulesConfigurator(myProject, this);
    myModulesConfigurator.reset();
    final LibraryTablesRegistrar tablesRegistrar = LibraryTablesRegistrar.getInstance();
    myProjectLibrariesProvider = new LibrariesModifiableModel(tablesRegistrar.getLibraryTable(myProject).getModifiableModel());
    myGlobalLibrariesProvider = new LibrariesModifiableModel(tablesRegistrar.getLibraryTable().getModifiableModel());
    myApplicationServerLibrariesProvider =
      new LibrariesModifiableModel(ApplicationServersManager.getInstance().getLibraryTable().getModifiableModel());
    final Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      final ModifiableRootModel modelProxy = myModulesConfigurator.getModuleEditor(module).getModifiableRootModel();
      myModule2LibrariesMap.put(module, new LibrariesModifiableModel(modelProxy.getModuleLibraryTable().getModifiableModel()));
    }
    reloadTree();
    super.reset();
  }


  public void apply() throws ConfigurationException {
    for (int i = 0; i < myJdksNode.getChildCount(); i++) {
      final NamedConfigurable configurable = ((MyNode)myJdksNode.getChildAt(i)).getConfigurable();
      if (configurable.isModified()) {
        configurable.apply();
      }
    }
    myJdksTreeModel.apply();
    myJdksTreeModel.setProjectJdk(ProjectRootManager.getInstance(myProject).getProjectJdk());
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        myProjectLibrariesProvider.deferredCommit();
        myGlobalLibrariesProvider.deferredCommit();
        myApplicationServerLibrariesProvider.deferredCommit();
        for (LibrariesModifiableModel model : myModule2LibrariesMap.values()) {
          model.deferredCommit();
        }
      }
    });

    myModulesConfigurator.apply();

    reset();
  }

  public boolean isModified() {
    boolean isModified = myModulesConfigurator.isModified();
    //isModified |= super.isModified();
    for (LibrariesModifiableModel model : myModule2LibrariesMap.values()) {
      final Library[] libraries = model.getLibraries();
      for (Library library : libraries) {
        if (model.hasLibraryEditor(library) && model.getLibraryEditor(library).hasChanges()) return true;
      }
    }
    for (int i = 0; i < myJdksNode.getChildCount(); i++) {
      final NamedConfigurable configurable = ((MyNode)myJdksNode.getChildAt(i)).getConfigurable();
      if (configurable.isModified()) {
        return true;
      }
    }
    isModified |= myJdksTreeModel.isModified();
    isModified |= myGlobalLibrariesProvider.isChanged();
    isModified |= myApplicationServerLibrariesProvider.isChanged();
    isModified |= myProjectLibrariesProvider.isChanged();
    return isModified;
  }

  public void disposeUIResources() {
    myJdksTreeModel.disposeUIResources();
    myModulesConfigurator.disposeUIResources();
    myModule2LibrariesMap.clear();
    myProjectLibrariesProvider = null;
    myGlobalLibrariesProvider = null;
    myApplicationServerLibrariesProvider = null;
    super.disposeUIResources();
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
    final TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath != null) {
      MyNode node = (MyNode)selectionPath.getLastPathComponent();
      return node.getConfigurable().getHelpTopic();
    }
    return null;
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }


  protected ArrayList<AnAction> createActions() {
    final ArrayList<AnAction> result = new ArrayList<AnAction>();
    result.add(new MyAddAction());
    result.add(new MyRemoveAction(new Condition<Object>() {
      public boolean value(final Object object) {
        if (object instanceof MyNode) {
          final NamedConfigurable namedConfigurable = ((MyNode)object).getConfigurable();
          final Object editableObject = namedConfigurable.getEditableObject();
          if (editableObject instanceof ProjectJdk ||
            editableObject instanceof Module) return true;
          if (editableObject instanceof LibraryImpl){
            final LibraryImpl library = (LibraryImpl)editableObject;
            return library.getTable() != null;
          }
        }
        return false;
      }
    }));
    return result;
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectRootMasterDetailsConfigurable";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public static ProjectRootConfigurable getInstance(final Project project) {
    return project.getComponent(ProjectRootConfigurable.class);
  }

  public void createNode(final NamedConfigurable configurable, final MyNode parentNode) {
    final MyNode node = new MyNode(configurable, true);
    addNode(node, parentNode);
    selectNodeInTree(node);
  }

  public MyNode createLibraryNode(Library library) {
    final String level = library.getTable().getTableLevel();
    if (level == LibraryTablesRegistrar.APPLICATION_LEVEL) {
      final LibraryConfigurable configurable = new LibraryConfigurable(getGlobalLibrariesProvider(), library, myProject);
      final MyNode node = new MyNode(configurable, true);
      addNode(node, myGlobalLibrariesNode);
      return node;
    }
    else if (level == LibraryTablesRegistrar.PROJECT_LEVEL) {
      final LibraryConfigurable configurable = new LibraryConfigurable(getProjectLibrariesProvider(), library, myProject);
      final MyNode node = new MyNode(configurable, true);
      addNode(node, myProjectLibrariesNode);
      return node;
    }
    else {
      final LibraryConfigurable configurable = new LibraryConfigurable(getApplicationServerLibrariesProvider(), library, myProject);
      final MyNode node = new MyNode(configurable, true);
      addNode(node, myApplicationServerLibrariesNode);
      return node;
    }
  }

  public ProjectJdk getSelectedJdk() {
    final TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath != null) {
      MyNode node = (MyNode)selectionPath.getLastPathComponent();
      final Object editableObject = node.getConfigurable().getEditableObject();
      if (editableObject instanceof ProjectJdk) {
        return (ProjectJdk)editableObject;
      }
    }
    return null;
  }

  public void setStartModuleWizard(final boolean show) {
    myModulesConfigurator.setStartModuleWizardOnShow(show);
  }

  public LibraryTableModifiableModelProvider getApplicationServerLibrariesProvider() {
    return new LibraryTableModifiableModelProvider() {
      public LibraryTable.ModifiableModel getModifiableModel() {
        return myApplicationServerLibrariesProvider;
      }

      public String getTableLevel() {
        return ApplicationServersManager.APPLICATION_SERVER_MODULE_LIBRARIES;
      }
    };
  }

  public DefaultMutableTreeNode createLibraryNode(final LibraryOrderEntry library, final ModifiableRootModel model) {
    final LibraryConfigurable configurable = new LibraryConfigurable(new LibraryTableModifiableModelProvider() {
      public LibraryTable.ModifiableModel getModifiableModel() {
        final LibraryTable.ModifiableModel modifiableModel = model.getModuleLibraryTable().getModifiableModel();
        myModule2LibrariesMap.put(model.getModule(), new LibrariesModifiableModel(modifiableModel));
        return modifiableModel;
      }

      public String getTableLevel() {
        return LibraryTableImplUtil.MODULE_LEVEL;
      }
    }, library.getLibrary(), library.getPresentableName(), myProject);
    final MyNode node = new MyNode(configurable, true);
    addNode(node, findNodeByObject(myProjectNode, model.getModule()));
    return node;
  }

  public void deleteLibraryNode(LibraryOrderEntry libraryOrderEntry) {
    final MyNode node = findNodeByName(myProjectNode, libraryOrderEntry.getPresentableName());
    if (node != null) {
      final TreeNode parent = node.getParent();
      node.removeFromParent();
      ((DefaultTreeModel)myTree.getModel()).reload(parent);
      final Module module = libraryOrderEntry.getOwnerModule();
      myModule2LibrariesMap.get(module).removeLibrary(libraryOrderEntry.getLibrary());
    }
  }

  public Project getProject() {
    return myProject;
  }

  public Library getLibrary(final Library library) {
    final String level = library.getTable().getTableLevel();
    if (level == LibraryTablesRegistrar.PROJECT_LEVEL) {
      return findLibraryModel(library, myProjectLibrariesProvider);
    }
    else if (level == LibraryTablesRegistrar.APPLICATION_LEVEL) {
      return findLibraryModel(library, myGlobalLibrariesProvider);
    }
    return findLibraryModel(library, myApplicationServerLibrariesProvider);
  }

  private static Library findLibraryModel(final Library library, LibrariesModifiableModel tableModel) {
    if (tableModel == null) return library;
    if (tableModel.wasLibraryRemoved(library)) return null;
    return tableModel.hasLibraryEditor(library) ? (Library)tableModel.getLibraryEditor(library).getModel() : library;
  }

  public void selectModuleTab(final String moduleName, final String tabName) {
    final MyNode node = findNodeByObject(myProjectNode, ModuleManager.getInstance(myProject).findModuleByName(moduleName));
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

  private class MyRemoveAction extends MyDeleteAction {
    public MyRemoveAction(final Condition<Object> availableCondition) {
      super(availableCondition);
    }

    public void actionPerformed(AnActionEvent e) {
      final TreePath selectionPath = myTree.getSelectionPath();
      final MyNode node = (MyNode)selectionPath.getLastPathComponent();
      final NamedConfigurable configurable = node.getConfigurable();
      final Object editableObject = configurable.getEditableObject();
      super.actionPerformed(e);
      if (editableObject instanceof ProjectJdk) {
        myJdksTreeModel.removeJdk((ProjectJdk)editableObject);
      }
      else if (editableObject instanceof Module) {
        myModulesConfigurator.deleteModule((Module)editableObject);
      }
      else if (editableObject instanceof Library) {
        final Library library = (Library)editableObject;
        final String level = library.getTable().getTableLevel();
        if (level == LibraryTablesRegistrar.APPLICATION_LEVEL) {
          myGlobalLibrariesProvider.removeLibrary(library);
        }
        else if (level == LibraryTablesRegistrar.PROJECT_LEVEL) {
          myProjectLibrariesProvider.removeLibrary(library);
        }
        else {
          myApplicationServerLibrariesProvider.removeLibrary(library);
        }
      }
    }
  }

  private PopupStep createJdksStep(AnActionEvent e) {
    DefaultActionGroup group = new DefaultActionGroup();
    myJdksTreeModel.createAddActions(group, myTree, new Condition<ProjectJdk>() {
      public boolean value(final ProjectJdk jdk) {
        createNode(new JdkConfigurable((ProjectJdkImpl)jdk, myJdksTreeModel), myJdksNode);
        return false;
      }
    });
    final JBPopupFactory popupFactory = JBPopupFactory.getInstance();
    return popupFactory.createActionsStep(group, e.getDataContext(), false, false, ProjectBundle.message("add.new.jdk.title"), myTree, true);    
  }

  private PopupStep createLibrariesStep(AnActionEvent e) {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new AnAction(ProjectBundle.message("add.new.global.library.text")) {
      public void actionPerformed(AnActionEvent e) {
        LibraryTableEditor.editLibraryTable(getGlobalLibrariesProvider(), myProject).createAddLibraryAction(true, myWholePanel).actionPerformed(null);
      }
    });
    group.add(new AnAction(ProjectBundle.message("add.new.application.server.library.text")) {
      public void actionPerformed(AnActionEvent e) {
        LibraryTableEditor.editLibraryTable(getApplicationServerLibrariesProvider(), myProject).createAddLibraryAction(true, myWholePanel)
          .actionPerformed(null);
      }
    });
    group.add(new AnAction(ProjectBundle.message("add.new.project.library.text")) {
      public void actionPerformed(AnActionEvent e) {
        LibraryTableEditor.editLibraryTable(getProjectLibrariesProvider(), myProject).createAddLibraryAction(true, myWholePanel).actionPerformed(null);
      }
    });
    final JBPopupFactory popupFactory = JBPopupFactory.getInstance();
    return popupFactory.createActionsStep(group, e.getDataContext(), false, false, ProjectBundle.message("add.new.library.title"), myTree, true);
  }

  private class MyAddAction extends AnAction {

    public MyAddAction() {
      super(CommonBundle.message("button.add"), CommonBundle.message("button.add"), Icons.ADD_ICON);
    }

    public void actionPerformed(final AnActionEvent e) {
      JBPopupFactory jbPopupFactory = JBPopupFactory.getInstance();
      List<String> actions = new ArrayList<String>();
      final String libraryChoice = ProjectBundle.message("add.new.library.text");
      actions.add(libraryChoice);
      final String jdkChoice = ProjectBundle.message("add.new.jdk.text");
      actions.add(jdkChoice);
      final String moduleChoice = ProjectBundle.message("add.new.module.text");
      actions.add(moduleChoice);
      List<Icon> icons = new ArrayList<Icon>();
      final ListPopup listPopup = jbPopupFactory.createWizardStep(new BaseListPopupStep<String>(ProjectBundle.message("add.action.name"), actions, icons) {
        public boolean hasSubstep(final String selectedValue) {
          return selectedValue.compareTo(moduleChoice) != 0;
        }

        public PopupStep onChosen(final String selectedValue, final boolean finalChoice) {
          if (selectedValue.compareTo(libraryChoice) == 0) {
            return createLibrariesStep(e);
          }
          else if (selectedValue.compareTo(jdkChoice) == 0) {
            return createJdksStep(e);
          }
          final Module module = myModulesConfigurator.addModule(myTree);
          if (module != null) {
            final MyNode node = new MyNode(new ModuleConfigurable(myModulesConfigurator, module), true);
            myProjectNode.add(node);
            TreeUtil.sort(myProjectNode, new Comparator() {
              public int compare(final Object o1, final Object o2) {
                final MyNode node1 = (MyNode)o1;
                final MyNode node2 = (MyNode)o2;
                if (node1.getConfigurable()instanceof ModuleConfigurable) return -1;
                if (node2.getConfigurable()instanceof ModuleConfigurable) return 1;
                return node1.getDisplayName().compareTo(node2.getDisplayName());
              }
            });
            ((DefaultTreeModel)myTree.getModel()).reload(myProjectNode);
            selectNodeInTree(node);
          }
          return PopupStep.FINAL_CHOICE;
        }
      });
      listPopup.showUnderneathOf(myNorthPanel);
    }

  }
}
