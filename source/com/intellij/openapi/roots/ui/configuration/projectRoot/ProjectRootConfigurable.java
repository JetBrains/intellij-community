/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.facet.Facet;
import com.intellij.facet.impl.ProjectFacetsConfigurator;
import com.intellij.find.FindBundle;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.impl.convert.ProjectFileVersion;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.ModuleGroupUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablePresentation;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ProjectConfigurable;
import com.intellij.openapi.roots.ui.configuration.actions.BackAction;
import com.intellij.openapi.roots.ui.configuration.actions.ForwardAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.*;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.TreeToolTipHandler;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.THashMap;
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
import java.util.List;

/**
 * User: anna
 * Date: 02-Jun-2006
 */
@State(
  name = "ProjectRootConfigurable.UI",
  storages = {
    @Storage(
      id ="other",
      file = "$WORKSPACE_FILE$"
    )}
)
public class ProjectRootConfigurable extends MasterDetailsComponent implements SearchableConfigurable, HistoryAware.Facade {
  private static final Icon COMPACT_EMPTY_MIDDLE_PACKAGES_ICON = IconLoader.getIcon("/objectBrowser/compactEmptyPackages.png");
  private static final Icon ICON = IconLoader.getIcon("/modules/modules.png");
  private static final Icon FIND_ICON = IconLoader.getIcon("/actions/find.png");

  public static final DataKey<ProjectRootConfigurable> KEY = DataKey.create("ProjectRootConfigurable"); 

  private boolean myPlainMode;

  private MyNode myJdksNode;

  private final Map<String, LibrariesModifiableModel> myLevel2Providers = new THashMap<String, LibrariesModifiableModel>();
  private final Map<String, MyNode> myLevel2Nodes = new THashMap<String, MyNode>();

  private MyNode myModulesNode;

  private MyNode myProjectNode;
  private MyNode myGlobalPartNode;

  private final Project myProject;

  private final ModuleManager myModuleManager;
  private ModulesConfigurator myModulesConfigurator;
  private ProjectConfigurable myProjectConfigurable;
  private final ProjectJdksModel myJdksTreeModel = new ProjectJdksModel();

  private History myHistory = new History(); 

  SdkModel.Listener myListener = new SdkModel.Listener() {
    public void sdkAdded(Sdk sdk) {
    }

    public void beforeSdkRemove(Sdk sdk) {
    }

    public void sdkChanged(Sdk sdk, String previousName) {
      updateName();
    }

    public void sdkHomeSelected(Sdk sdk, String newSdkHome) {
      updateName();
    }

    private void updateName() {
      final TreePath path = myTree.getSelectionPath();
      if (path != null) {
        final NamedConfigurable configurable = ((MyNode)path.getLastPathComponent()).getConfigurable();
        if (configurable != null && configurable instanceof JdkConfigurable) {
          configurable.updateName();
        }
      }
    }
  };

  private boolean myHistoryNavigatedNow;

  private boolean myDisposed = true;

  private FacetEditorFacadeImpl myFacetEditorFacade = new FacetEditorFacadeImpl(this, TREE_UPDATER);

  private final Map<Library, Set<String>> myLibraryDependencyCache = new HashMap<Library, Set<String>>();
  private final Map<ProjectJdk, Set<String>> myJdkDependencyCache = new HashMap<ProjectJdk, Set<String>>();
  private final Map<Module, Map<String, Set<String>>> myValidityCache = new HashMap<Module, Map<String, Set<String>>>();
  private final Map<Library, Boolean> myLibraryPathValidityCache = new HashMap<Library, Boolean>(); //can be invalidated on startup only
  private final Map<Module, Set<String>> myModulesDependencyCache = new HashMap<Module, Set<String>>();

  private final Alarm myUpdateDependenciesAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

  private final Alarm myReloadProjectAlarm = new Alarm();

  @NonNls private static final String DELETED_LIBRARIES = "lib";
  private static final String NO_JDK = ProjectBundle.message("project.roots.module.jdk.problem.message");

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
          invalidateModules(myLibraryDependencyCache.get(library));
        }
      }

      public void itemsExternallyChanged() {
        //do nothing
      }
    });
    initTree();
    myJdksTreeModel.addListener(myListener);
  }


  protected void initTree() {
    super.initTree();
    new TreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      public String convert(final TreePath treePath) {
        return ((MyNode)treePath.getLastPathComponent()).getDisplayName();
      }
    }, true);
    TreeToolTipHandler.install(myTree);
    ToolTipManager.sharedInstance().registerComponent(myTree);
    myTree.setCellRenderer(new ColoredTreeCellRenderer(){
      public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (value instanceof MyNode) {
          final MyNode node = (MyNode)value;
          final String displayName = node.getDisplayName();
          final Icon icon = node.getConfigurable().getIcon();
          setIcon(icon);
          setToolTipText(null);
          setFont(UIUtil.getTreeFont());
          if (node.isDisplayInBold()){
            append(displayName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          } else {
            final Object object = node.getConfigurable().getEditableObject();
            final boolean unused = isUnused(object, node);
            final boolean invalid = isInvalid(object);
            if (unused || invalid){
              Color fg = unused
                         ? UIUtil.getTextInactiveTextColor()
                         : selected && hasFocus ? UIUtil.getTreeSelectionForeground() : UIUtil.getTreeForeground();
              append(displayName, new SimpleTextAttributes(invalid ? SimpleTextAttributes.STYLE_WAVED : SimpleTextAttributes.STYLE_PLAIN,
                                                           fg,
                                                           Color.red));
              setToolTipText(composeTooltipMessage(invalid, object, displayName, unused));
            }
            else {
              append(displayName, selected && hasFocus ? SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
          }
        }
      }
    });
  }

  private String composeTooltipMessage(final boolean invalid, final Object object, final String displayName, final boolean unused) {
    final StringBuilder buf = StringBuilderSpinAllocator.alloc();
    try {
      if (invalid) {
        if (object instanceof Module) {
          final Module module = (Module)object;
          final Map<String, Set<String>> problems = myValidityCache.get(module);
          if (problems.containsKey(NO_JDK)){
            buf.append(NO_JDK).append("\n");
          }
          final Set<String> deletedLibraries = problems.get(DELETED_LIBRARIES);
          if (deletedLibraries != null) {
            buf.append(ProjectBundle.message("project.roots.library.problem.message", deletedLibraries.size()));
            for (String problem : deletedLibraries) {
              if (deletedLibraries.size() > 1) {
                buf.append(" - ");
              }
              buf.append("\'").append(problem).append("\'").append("\n");
            }
          }
        } else {
          buf.append(ProjectBundle.message("project.roots.tooltip.library.misconfigured", displayName)).append("\n");
        }
      }
      if (unused) {
        buf.append(ProjectBundle.message("project.roots.tooltip.unused", displayName));
      }
      return buf.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(buf);
    }
  }

  private boolean isInvalid(final Object object) {
    if (object instanceof Module){
      final Module module = (Module)object;
      if (myValidityCache.containsKey(module)) return myValidityCache.get(module) != null;
      myUpdateDependenciesAlarm.addRequest(new Runnable(){
        public void run() {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              updateModuleValidityCache(module);
            }
          });
        }
      }, 0);
    } else if (object instanceof LibraryEx) {
      final LibraryEx library = (LibraryEx)object;
      if (myLibraryPathValidityCache.containsKey(library)) return myLibraryPathValidityCache.get(library).booleanValue();
      myUpdateDependenciesAlarm.addRequest(new Runnable(){
        public void run() {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              updateLibraryValidityCache(library);
            }
          });
        }
      }, 0);
    }
    return false;
  }

  private void updateLibraryValidityCache(final LibraryEx library) {
    if (myLibraryPathValidityCache.containsKey(library)) return; //do not check twice
    boolean valid = library.allPathsValid(OrderRootType.CLASSES) && library.allPathsValid(OrderRootType.JAVADOC) && library.allPathsValid(OrderRootType.SOURCES);
    myLibraryPathValidityCache.put(library, valid ? Boolean.FALSE : Boolean.TRUE);
    if (valid) return;
    SwingUtilities.invokeLater(new Runnable(){
      public void run() {
        if (!myDisposed){
          myTree.repaint();
        }
      }
    });
  }

  private void updateModuleValidityCache(final Module module) {
    if (myValidityCache.containsKey(module)) return; //do not check twice
    final OrderEntry[] entries = myModulesConfigurator.getRootModel(module).getOrderEntries();
    Map<String, Set<String>> problems = null;
    for (OrderEntry entry : entries) {
      if (myDisposed) return;
      if (!entry.isValid()){
        if (problems == null) {
          problems = new HashMap<String, Set<String>>();
        }
        if (entry instanceof JdkOrderEntry && ((JdkOrderEntry)entry).getJdkName() == null) {
          problems.put(NO_JDK, null);
        } else {
          Set<String> deletedLibraries = problems.get(DELETED_LIBRARIES);
          if (deletedLibraries == null){
            deletedLibraries = new HashSet<String>();
            problems.put(DELETED_LIBRARIES, deletedLibraries);
          }
          deletedLibraries.add(entry.getPresentableName());
        }
      }
    }
    myValidityCache.put(module, problems);
    if (problems != null) {
      SwingUtilities.invokeLater(new Runnable(){
        public void run() {
          if (!myDisposed){
            myTree.repaint();
          }
        }
      });
    }
  }

  private boolean isUnused(final Object object, MyNode node) {
    if (object == null) return false;
    if (object instanceof Module){
      getCachedDependencies(object, node, false);
      return false;
    }
    if (object instanceof ProjectJdk) {
      return false;
    }
    if (object instanceof Library) {
      final LibraryTable libraryTable = ((Library)object).getTable();
      if (libraryTable == null || libraryTable.getTableLevel() != LibraryTablesRegistrar.PROJECT_LEVEL) {
        return false;
      }
    }
    final Set<String> dependencies = getCachedDependencies(object, node, false);
    return dependencies != null && dependencies.isEmpty();
  }

  protected ArrayList<AnAction> getAdditionalActions() {
    final ArrayList<AnAction> result = new ArrayList<AnAction>();
    result.add(ActionManager.getInstance().getAction(IdeActions.GROUP_MOVE_MODULE_TO_GROUP));
    return result;
  }

  private void reloadTree() {

    myRoot.removeAllChildren();

    myGlobalPartNode = new MyNode(new GlobalResourcesConfigurable(), true);

    myLevel2Nodes.clear();
    final LibraryTablesRegistrar registrar = LibraryTablesRegistrar.getInstance();
    for (final String s : myLevel2Providers.keySet()) {
      final LibrariesModifiableModel provider = myLevel2Providers.get(s);
      myLevel2Nodes.put(s, createLibrariesNode(registrar.getLibraryTableByLevel(s, myProject), provider, createModifiableModelProvider(s,
                                                                                                                                       false)));
    }
    myGlobalPartNode.add(myLevel2Nodes.get(LibraryTablesRegistrar.APPLICATION_LEVEL));
    for (final LibraryTable table : registrar.getCustomLibraryTables()) {
      myGlobalPartNode.add(myLevel2Nodes.get(table.getTableLevel()));
    }

    myProjectNode = new MyNode(myProjectConfigurable, true);
    myRoot.add(myProjectNode);
    createProjectNodes();

    myRoot.add(myGlobalPartNode);
    createProjectJdks();

    ((DefaultTreeModel)myTree.getModel()).reload();

    myDisposed = false;
  }

  protected void updateSelection(@NotNull final NamedConfigurable configurable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    
    final String selectedTab = ModuleEditor.getSelectedTab();

    if (!myHistoryNavigatedNow) {
      getHistory().pushPlace(new Place(new Object[] {configurable, selectedTab}) {
        public void goThere() {
          selectInTree(configurable).doWhenDone(new Runnable() {
            public void run() {
              updateTabSelection(configurable, selectedTab);
            }
          });
        }
      });
    }

    updateSelection(configurable, selectedTab);
  }

  protected boolean isAutoScrollEnabled() {
    return !myHistoryNavigatedNow;
  }

  private void updateSelection(final NamedConfigurable configurable, final String selectedTab) {
    if (configurable instanceof HistoryAware.Configurable) {
      ((HistoryAware.Configurable)configurable).setHistoryFacade(this);
    }

    super.updateSelection(configurable);

    updateTabSelection(configurable, selectedTab);
  }

  public ActionCallback selectInTree(final NamedConfigurable configurable) {
    myHistoryNavigatedNow = true;

    final ActionCallback callback = new ActionCallback() {
      protected void onConsumed() {
        myHistoryNavigatedNow = false;
      }
    };

    final MyNode toSelect = findNodeByObject(myRoot, configurable.getEditableObject());

    selectNodeInTree(toSelect, false).doWhenDone(new Runnable() {
      public void run() {
        updateSelection(configurable);
        callback.setDone();
      }
    });

    return callback;
  }

  private void updateTabSelection(final NamedConfigurable configurable, final String selectedTab) {
    if (configurable instanceof ModuleConfigurable){
      final ModuleConfigurable moduleConfigurable = (ModuleConfigurable)configurable;
      moduleConfigurable.getModuleEditor().setSelectedTabName(selectedTab);
    }
  }

  private MyNode createLibrariesNode(final LibraryTable table,
                                     LibrariesModifiableModel provider,
                                     final LibraryTableModifiableModelProvider modelProvider) {
    provider = new LibrariesModifiableModel(table);
    LibrariesConfigurable librariesConfigurable = new LibrariesConfigurable(table);
    MyNode node = new MyNode(librariesConfigurable, true);
    final Library[] libraries = provider.getLibraries();
    for (Library library : libraries) {
      addNode(new MyNode(new LibraryConfigurable(modelProvider, library, myProject, TREE_UPDATER)), node);
    }
    return node;
  }

  private void createProjectJdks() {
    myJdksNode = new MyNode(new JdksConfigurable(myJdksTreeModel), true);
    final TreeMap<ProjectJdk, ProjectJdk> sdks = myJdksTreeModel.getProjectJdks();
    for (ProjectJdk sdk : sdks.keySet()) {
      final JdkConfigurable configurable = new JdkConfigurable((ProjectJdkImpl)sdks.get(sdk), myJdksTreeModel, TREE_UPDATER);
      addNode(new MyNode(configurable), myJdksNode);
    }
    myGlobalPartNode.add(myJdksNode);
  }

  private void createProjectNodes() {
    myModulesNode = new MyNode(new ModulesConfigurable(myModuleManager), true);
    final Map<ModuleGroup, MyNode> moduleGroup2NodeMap = new HashMap<ModuleGroup, MyNode>();
    final Module[] modules = myModuleManager.getModules();
    for (final Module module : modules) {
      ModuleConfigurable configurable = new ModuleConfigurable(myModulesConfigurator, module, TREE_UPDATER);
      final MyNode moduleNode = new MyNode(configurable);
      myFacetEditorFacade.addFacetsNodes(module, moduleNode);
      final String[] groupPath = myPlainMode ? null : myModulesConfigurator.getModuleModel().getModuleGroupPath(module);
      if (groupPath == null || groupPath.length == 0){
        addNode(moduleNode, myModulesNode);
      } else {
        final MyNode moduleGroupNode = ModuleGroupUtil
          .buildModuleGroupPath(new ModuleGroup(groupPath), myModulesNode, moduleGroup2NodeMap,
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
    if (!myProject.isDefault()) {  //do not add modules node in case of template project
      myProjectNode.add(myModulesNode);
    }

    final LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject);
    final LibrariesModifiableModel projectLibrariesProvider = new LibrariesModifiableModel(table);
    myLevel2Providers.put(LibraryTablesRegistrar.PROJECT_LEVEL, projectLibrariesProvider);

    myProjectNode.add(myLevel2Nodes.get(LibraryTablesRegistrar.PROJECT_LEVEL));
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
        addNode(moduleNode, myModulesNode);
      } else {
        final MyNode moduleGroupNode = ModuleGroupUtil
          .updateModuleGroupPath(new ModuleGroup(groupPath), myModulesNode, new Function<ModuleGroup, MyNode>() {
            @Nullable
            public MyNode fun(final ModuleGroup group) {
              return findNodeByObject(myModulesNode, group);
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
    ((DefaultTreeModel)myTree.getModel()).reload(myModulesNode);
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

  

  public ProjectJdksModel getProjectJdksModel() {
    return myJdksTreeModel;
  }

  public LibraryTableModifiableModelProvider getGlobalLibrariesProvider(final boolean tableEditable) {
    return createModifiableModelProvider(LibraryTablesRegistrar.APPLICATION_LEVEL, tableEditable);
  }

  public LibraryTableModifiableModelProvider createModifiableModelProvider(final String level, final boolean isTableEditable) {
    final LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(level, myProject);
    return new LibraryTableModifiableModelProvider() {
        public LibraryTable.ModifiableModel getModifiableModel() {
          return myLevel2Providers.get(level);
        }

        public String getTableLevel() {
          return table.getTableLevel();
        }

        public LibraryTablePresentation getLibraryTablePresentation() {
          return table.getPresentation();
        }

        public boolean isLibraryTableEditable() {
          return isTableEditable && table.isEditable();
        }
      };
  }

  public LibraryTableModifiableModelProvider getProjectLibrariesProvider(final boolean tableEditable) {
    return createModifiableModelProvider(LibraryTablesRegistrar.PROJECT_LEVEL, tableEditable);
  }

  public void reset() {
    myJdksTreeModel.reset(myProject);
    myModulesConfigurator = new ModulesConfigurator(myProject, this);
    myModulesConfigurator.resetModuleEditors();
    myProjectConfigurable = myModulesConfigurator.getModulesConfigurable();
    final LibraryTablesRegistrar tablesRegistrar = LibraryTablesRegistrar.getInstance();

    myLevel2Providers.clear();
    myLevel2Providers.put(LibraryTablesRegistrar.APPLICATION_LEVEL, new LibrariesModifiableModel(tablesRegistrar.getLibraryTable()));
    myLevel2Providers.put(LibraryTablesRegistrar.PROJECT_LEVEL, new LibrariesModifiableModel(tablesRegistrar.getLibraryTable(myProject)));
    for (final LibraryTable table : tablesRegistrar.getCustomLibraryTables()) {
      myLevel2Providers.put(table.getTableLevel(), new LibrariesModifiableModel(table));
    }

    myHistory.clear();

    reloadTree();
    super.reset();
  }


  public void apply() throws ConfigurationException {
    final Set<MyNode> roots = new HashSet<MyNode>();
    roots.add(myModulesNode);
    if (!canApply(roots, ProjectBundle.message("rename.message.prefix.module"), ProjectBundle.message("rename.module.title"))) return;
    boolean modifiedJdks = false;
    for (int i = 0; i < myJdksNode.getChildCount(); i++) {
      final NamedConfigurable configurable = ((MyNode)myJdksNode.getChildAt(i)).getConfigurable();
      if (configurable.isModified()) {
        configurable.apply();
        modifiedJdks = true;
      }
    }

    if (myJdksTreeModel.isModified() || modifiedJdks) myJdksTreeModel.apply(this);
    myJdksTreeModel.setProjectJdk(ProjectRootManager.getInstance(myProject).getProjectJdk());
    if (isInitialized(myProjectConfigurable) && myProjectConfigurable.isModified()) myProjectConfigurable.apply(); //do not reorder

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (final LibrariesModifiableModel provider : myLevel2Providers.values()) {
          provider.deferredCommit();
        }        
      }
    });

    if (myModulesConfigurator.isModified()) myModulesConfigurator.apply();

    //cleanup
    myUpdateDependenciesAlarm.cancelAllRequests();
    myUpdateDependenciesAlarm.addRequest(new Runnable(){
      public void run() {
        SwingUtilities.invokeLater(new Runnable(){
          public void run() {
            if (myDisposed) return;
            dispose();
            reset();
          }
        });
      }
    }, 0);
  }

  public boolean isModified() {
    boolean isModified = myModulesConfigurator.isModified();
    for (int i = 0; i < myJdksNode.getChildCount(); i++) {
      final NamedConfigurable configurable = ((MyNode)myJdksNode.getChildAt(i)).getConfigurable();
      if (configurable.isModified()) {
        return true;
      }
    }
    isModified |= isInitialized(myProjectConfigurable) && myProjectConfigurable.isModified();
    isModified |= myJdksTreeModel.isModified();
    for (final LibrariesModifiableModel provider : myLevel2Providers.values()) {
      isModified |= provider.isChanged();
    }
    return isModified;
  }

  public void disposeUIResources() {
    myDisposed = true;
    myAutoScrollHandler.cancelAllRequests();
    myUpdateDependenciesAlarm.cancelAllRequests();
    myUpdateDependenciesAlarm.addRequest(new Runnable(){
      public void run() {
        SwingUtilities.invokeLater(new Runnable(){
          public void run() {
            dispose();
          }
        });
      }
    }, 0);
  }

  private void dispose() {
    myHistory.clear();
    myJdksTreeModel.removeListener(myListener);
    myJdksTreeModel.disposeUIResources();
    myModulesConfigurator.disposeUIResources();
    myLevel2Providers.clear();
    myLevel2Nodes.clear();
    myJdkDependencyCache.clear();
    myLibraryDependencyCache.clear();
    myValidityCache.clear();
    myLibraryPathValidityCache.clear();
    myModulesDependencyCache.clear();
    ProjectRootConfigurable.super.disposeUIResources();
  }


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
    final TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath != null) {
      MyNode node = (MyNode)selectionPath.getLastPathComponent();
      final NamedConfigurable configurable = node.getConfigurable();
      if (configurable != null) {
        return configurable.getHelpTopic();
      }
    }
    return "root.settings";
  }


  protected ArrayList<AnAction> createActions(final boolean fromPopup) {
    final ArrayList<AnAction> result = new ArrayList<AnAction>();
    result.add(new AddAction(this, fromPopup));
    result.add(new MyRemoveAction());

    result.add(Separator.getInstance());

    result.add(new BackAction(myWholePanel));
    result.add(new ForwardAction(myWholePanel));

    result.add(Separator.getInstance());

    result.add(new MyFindUsagesAction());
    result.add(new MyGroupAction());
    final TreeExpander expander = new TreeExpander() {
      public void expandAll() {
        TreeUtil.expandAll(myTree);
      }

      public boolean canExpand() {
        return true;
      }

      public void collapseAll() {
        TreeUtil.collapseAll(myTree, 0);
      }

      public boolean canCollapse() {
        return true;
      }
    };

    result.add(Separator.getInstance());

    final CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    result.add(actionsManager.createExpandAllAction(expander, myTree));
    result.add(actionsManager.createCollapseAllAction(expander, myTree));
    return result;
  }

  public static ProjectRootConfigurable getInstance(final Project project) {
    return ShowSettingsUtil.getInstance().findProjectConfigurable(project, ProjectRootConfigurable.class);
  }

  public MyNode createLibraryNode(Library library, String presentableName, Module module) {
    final LibraryTable table = library.getTable();
    if (table != null){
      final String level = table.getTableLevel();
      final LibraryConfigurable configurable = new LibraryConfigurable(createModifiableModelProvider(level, false), library, myProject, TREE_UPDATER);
      final MyNode node = new MyNode(configurable);
      addNode(node, myLevel2Nodes.get(level));
      return node;
    }

    return null;
  }

  @Nullable
  private Set<String> getCachedDependencies(final Object selectedObject, final MyNode selectedNode, boolean force) {
    if (selectedObject instanceof Library){
      final Library library = (Library)selectedObject;
      if (myLibraryDependencyCache.containsKey(library)){
        return myLibraryDependencyCache.get(library);
      }
    } else if (selectedObject instanceof ProjectJdk){
      final ProjectJdk projectJdk = (ProjectJdk)selectedObject;
      if (myJdkDependencyCache.containsKey(projectJdk)){
        return myJdkDependencyCache.get(projectJdk);
      }
    } else if (selectedObject instanceof Module) {
      final Module module = (Module)selectedObject;
      if (myModulesDependencyCache.containsKey(module)) {
        return myModulesDependencyCache.get(module);
      }
    }
    final Computable<Set<String>> dependencies = new Computable<Set<String>>(){
      @Nullable
      public Set<String> compute() {
        final Set<String> dependencies = getDependencies(selectedObject, selectedNode);
        if (selectedObject instanceof Library){
          myLibraryDependencyCache.put((Library)selectedObject, dependencies);
        } else if (selectedObject instanceof ProjectJdk){
          final ProjectJdk projectJdk = (ProjectJdk)selectedObject;
          myJdkDependencyCache.put(projectJdk, dependencies);
        } else if (selectedObject instanceof Module){
          myModulesDependencyCache.put((Module)selectedObject, dependencies);
        }
        return dependencies;
      }
    };
    if (force){
      return dependencies.compute();
    } else {
      myUpdateDependenciesAlarm.addRequest(new Runnable(){
        public void run() {
          final Set<String> dep = dependencies.compute();
          SwingUtilities.invokeLater(new Runnable(){
            public void run() {
              if (dep != null && dep.isEmpty() && !myDisposed){
                myTree.repaint();
              }
            }
          });
        }
      }, 0);
      return null;
    }
  }

  @Nullable
  private Set<String> getDependencies(final Object selectedObject, final MyNode node) {
    if (selectedObject instanceof Module) {
      return getDependencies(new Condition<OrderEntry>() {
        public boolean value(final OrderEntry orderEntry) {
          return orderEntry instanceof ModuleOrderEntry && Comparing.equal(((ModuleOrderEntry)orderEntry).getModule(), selectedObject);
        }
      });
    }
    else if (selectedObject instanceof Library) {
      if (((Library)selectedObject).getTable() == null) { //module library navigation
        final Set<String> set = new HashSet<String>();
        set.add(((MyNode)node.getParent()).getDisplayName());
        return set;
      }
      return getDependencies(new Condition<OrderEntry>() {
        @SuppressWarnings({"SimplifiableIfStatement"})
        public boolean value(final OrderEntry orderEntry) {
          if (orderEntry instanceof LibraryOrderEntry){
            final LibraryImpl library = (LibraryImpl)((LibraryOrderEntry)orderEntry).getLibrary();
            if (Comparing.equal(library, selectedObject)) return true;
            return library != null && Comparing.equal(library.getSource(), selectedObject);
          }
          return false;
        }
      });
    }
    else if (selectedObject instanceof ProjectJdk) {
      return getDependencies(new Condition<OrderEntry>() {
        public boolean value(final OrderEntry orderEntry) {
          return orderEntry instanceof JdkOrderEntry && Comparing.equal(((JdkOrderEntry)orderEntry).getJdk(), selectedObject);
        }
      });
    }
    return null;
  }

  private Set<String> getDependencies(final Condition<OrderEntry> condition) {
    final Set<String> result = new TreeSet<String>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final Module[] modules = myModulesConfigurator.getModules();
        for (final Module module : modules) {
          final ModuleEditor moduleEditor = myModulesConfigurator.getModuleEditor(module);
          if (moduleEditor != null) {
            final OrderEntry[] entries = moduleEditor.getModifiableRootModel().getOrderEntries();
            for (OrderEntry entry : entries) {
              if (myDisposed) return;
              if (condition.value(entry)) {
                result.add(module.getName());
                break;
              }
            }
          }
        }
      }
    });
    return result;
  }

  public void setStartModuleWizard(final boolean show) {
    myModulesConfigurator.getModulesConfigurable().setStartModuleWizardOnShow(show);
  }

  public List<LibraryTableModifiableModelProvider> getCustomLibrariesProviders(final boolean tableEditable) {
    return ContainerUtil.map2List(LibraryTablesRegistrar.getInstance().getCustomLibraryTables(), new NotNullFunction<LibraryTable, LibraryTableModifiableModelProvider>() {
      @NotNull
      public LibraryTableModifiableModelProvider fun(final LibraryTable libraryTable) {
        return createModifiableModelProvider(libraryTable.getTableLevel(), tableEditable);
      }
    });
  }

  private void invalidateModules(final Set<String> modules) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (modules != null) {
          for (String module : modules) {
            myValidityCache.remove(myModuleManager.findModuleByName(module));
          }
        }
      }
    });
  }

  public Project getProject() {
    return myProject;
  }

  @Nullable
  public Library getLibrary(final String libraryName, final String libraryLevel) {
/* the null check is added only to prevent NPE when called from getLibrary */    
    final LibrariesModifiableModel model = myLevel2Providers.get(libraryLevel);
    return model == null ? null : findLibraryModel(libraryName, model);
  }

  @Nullable
  private static Library findLibraryModel(final String libraryName, @NotNull LibrariesModifiableModel model) {
    final Library library = model.getLibraryByName(libraryName);
    return findLibraryModel(library, model);
  }

  @Nullable
  private static Library findLibraryModel(final Library library, LibrariesModifiableModel tableModel) {
    if (tableModel == null) return library;
    if (tableModel.wasLibraryRemoved(library)) return null;
    return tableModel.hasLibraryEditor(library) ? (Library)tableModel.getLibraryEditor(library).getModel() : library;
  }

  public void selectFacetTab(@NotNull Facet facet, @Nullable final String tabName) {
    final MyNode node = findNodeByObject(myModulesNode, facet);
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

  public boolean addJdkNode(final ProjectJdk jdk, final boolean selectInTree) {
    if (!myDisposed) {
      addNode(new MyNode(new JdkConfigurable((ProjectJdkImpl)jdk, myJdksTreeModel, TREE_UPDATER)), myJdksNode);
      if (selectInTree) {
        selectNodeInTree(MasterDetailsComponent.findNodeByObject(myJdksNode, jdk));
      }
      return true;
    }
    return false;
  }

  public void clearCaches(final Module module, final List<Library> chosen) {
    for (Library library : chosen) {
      myLibraryDependencyCache.remove(library);
    }
    myValidityCache.remove(module);
    myTree.repaint();
  }

  public void clearCaches(final Module module, final LibraryOrderEntry libEntry) {
    final Library library = libEntry.getLibrary();
    myLibraryDependencyCache.remove(library);
    if (library != null){
      myLibraryDependencyCache.remove(((LibraryImpl)library).getSource());
    }
    myValidityCache.remove(module);
    myTree.repaint();
  }

  public void clearCaches(final Module module, final ProjectJdk oldJdk, final ProjectJdk selectedModuleJdk) {
    myJdkDependencyCache.remove(oldJdk);
    myJdkDependencyCache.remove(selectedModuleJdk);
    myValidityCache.remove(module);
    myTree.repaint();
  }

  public void clearCaches(final Module module) {
    myValidityCache.remove(module);
    myTree.repaint();
  }

  public Module[] getModules() {
    return myModulesConfigurator != null ? myModulesConfigurator.getModuleModel().getModules() : myModuleManager.getModules();
  }

  public void addLibraryOrderEntry(final Module module, final Library library) {
    final ModuleEditor moduleEditor = myModulesConfigurator.getModuleEditor(module);
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
    Set<String> modules = myLibraryDependencyCache.get(library);
    if (modules == null) {
      modules = new HashSet<String>();
      myLibraryDependencyCache.put(library, modules);
    }
    modules.add(module.getName());
    myTree.repaint();
  }

  @Nullable
  public MyNode findModuleNode(final Module module) {
    return findNodeByObject(myModulesNode, module);
  }

  public FacetEditorFacadeImpl getFacetEditorFacade() {
    return myFacetEditorFacade;
  }

  public ProjectFacetsConfigurator getFacetConfigurator() {
    return myModulesConfigurator.getFacetsConfigurator();
  }

  public void addModule() {
    final Module module = myModulesConfigurator.addModule(myTree);
    if (module != null) {
      final MasterDetailsComponent.MyNode node = new MasterDetailsComponent.MyNode(new ModuleConfigurable(myModulesConfigurator, module, TREE_UPDATER));
      final TreePath selectionPath = myTree.getSelectionPath();
      MyNode parent = null;
      if (selectionPath != null) {
        MyNode selected = (MyNode)selectionPath.getLastPathComponent();
        final Object o = selected.getConfigurable().getEditableObject();
        if (o instanceof ModuleGroup) {
          myModulesConfigurator.getModuleModel().setModuleGroupPath(module, ((ModuleGroup)o).getGroupPath());
          parent = selected;
        } else if (o instanceof Module) { //create near selected
          final ModifiableModuleModel modifiableModuleModel = myModulesConfigurator.getModuleModel();
          final String[] groupPath = modifiableModuleModel.getModuleGroupPath((Module)o);
          if (groupPath != null) {
            modifiableModuleModel.setModuleGroupPath(module, groupPath);
            parent = findNodeByObject(myModulesNode, new ModuleGroup(groupPath));
          }
        }
      }
      if (parent == null) parent = myModulesNode;
      addNode(node, parent);
      myFacetEditorFacade.addFacetsNodes(module, node);
      ((DefaultTreeModel)myTree.getModel()).reload(parent);
      selectNodeInTree(node);
      myValidityCache.clear(); //missing modules added
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

  public void addReloadProjectRequest(final Runnable runnable) {
    myReloadProjectAlarm.cancelAllRequests();
    myReloadProjectAlarm.addRequest(runnable, 300, ModalityState.NON_MODAL);
  }

  public String getCompilerOutputUrl() {
    return isInitialized(myProjectConfigurable) ? myProjectConfigurable.getCompilerOutputUrl() : ProjectRootManager.getInstance(myProject).getCompilerOutputUrl();
  }

  @Nullable
  public Module getModule(final String moduleName) {
    if (moduleName == null) return null;
    return myModulesConfigurator != null ? myModulesConfigurator.getModule(moduleName) : myModuleManager.findModuleByName(moduleName);
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
        return myModulesConfigurator.getModuleModel();
      }

      if (KEY.getName().equals(dataId)) return ProjectRootConfigurable.this;
      return null;
    }
  }

  private class MyRemoveAction extends MyDeleteAction {
    public MyRemoveAction() {
      super(new Condition<Object>() {
        public boolean value(final Object object) {
          if (object instanceof MyNode) {
            final NamedConfigurable namedConfigurable = ((MyNode)object).getConfigurable();
            if (namedConfigurable != null) {
              final Object editableObject = namedConfigurable.getEditableObject();
              if (editableObject instanceof ProjectJdk || editableObject instanceof Module || editableObject instanceof Facet) return true;
              if (editableObject instanceof Library) {
                final LibraryTable table = ((Library)editableObject).getTable();
                return table == null || table.isEditable();
              }
            }
          }
          return false;
        }
      });
    }

    public void actionPerformed(AnActionEvent e) {
      final TreePath[] paths = myTree.getSelectionPaths();
      final Set<TreePath> pathsToRemove = new HashSet<TreePath>();
      for (TreePath path : paths) {
        if (removeFromModel(path)) {
          pathsToRemove.add(path);
        }
      }
      removePaths(pathsToRemove.toArray(new TreePath[pathsToRemove.size()]));
    }

    private boolean removeFromModel(final TreePath selectionPath) {
      final MyNode node = (MyNode)selectionPath.getLastPathComponent();
      final NamedConfigurable configurable = node.getConfigurable();
      final Object editableObject = configurable.getEditableObject();
      if (editableObject instanceof ProjectJdk) {
        final ProjectJdk jdk = (ProjectJdk)editableObject;
        myJdksTreeModel.removeJdk(jdk);
        myJdkDependencyCache.remove(jdk);
        myValidityCache.clear();
      }
      else if (editableObject instanceof Module) {
        final Module module = (Module)editableObject;
        if (!myModulesConfigurator.deleteModule(module)) {
          //wait for confirmation
          return false;
        }
        myValidityCache.remove(module);
        invalidateModules(myModulesDependencyCache.get(module));
        myModulesDependencyCache.remove(module);
      }
      else if (editableObject instanceof Facet) {
        Facet facet = (Facet)editableObject;
        if (!ProjectFileVersion.getInstance(myProject).isFacetDeletionEnabled(facet.getTypeId())) {
          return false;
        }
        getFacetConfigurator().removeFacet(facet);
      }
      else if (editableObject instanceof Library) {
        final Library library = (Library)editableObject;
        final LibraryTable table = library.getTable();
        if (table != null) {
          final String level = table.getTableLevel();
          myLevel2Providers.get(level).removeLibrary(library);
          invalidateModules(myLibraryDependencyCache.get(library));
          myLibraryDependencyCache.remove(library);
        }        
      }
      return true;
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

      if (myModulesConfigurator != null) {
        presentation.setVisible(myModulesConfigurator.getModuleModel().hasModuleGroups());
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
      final ModifiableModuleModel model = myModulesConfigurator.getModuleModel();
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
      for(int i = myModulesNode.getChildCount() - 1; i >=0; i--){
        final MyNode node = (MyNode)myModulesNode.getChildAt(i);
        if (node.getConfigurable().getEditableObject() instanceof ModuleGroup){
          node.removeFromParent();
        }
      }
      ((DefaultTreeModel)myTree.getModel()).reload(myModulesNode);
    }
  }

  private class MyFindUsagesAction extends AnAction {
    public MyFindUsagesAction() {
      super(ProjectBundle.message("find.usages.action.text"), ProjectBundle.message("find.usages.action.text"), ProjectRootConfigurable.FIND_ICON);
      registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_USAGES).getShortcutSet(), myTree);
    }

    public void update(AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      final TreePath selectionPath = myTree.getSelectionPath();
      if (selectionPath != null){
        final MyNode node = (MyNode)selectionPath.getLastPathComponent();
        presentation.setEnabled(!node.isDisplayInBold());
      } else {
        presentation.setEnabled(false);
      }
    }

    public void actionPerformed(AnActionEvent e) {
      final Object selectedObject = getSelectedObject();
      final MyNode selectedNode = (MyNode)myTree.getSelectionPath().getLastPathComponent();
      final Set<String> dependencies = getCachedDependencies(selectedObject, selectedNode, true);
      if (dependencies == null || dependencies.isEmpty()) {
        Messages.showInfoMessage(myTree, FindBundle.message("find.usage.view.no.usages.text"),
                                 FindBundle.message("find.pointcut.applications.not.found.title"));
        return;
      }
      final int selectedRow = myTree.getSelectionRows()[0];
      final Rectangle rowBounds = myTree.getRowBounds(selectedRow);
      final Point location = rowBounds.getLocation();
      location.x += rowBounds.width;
      JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<String>(
        ProjectBundle.message("dependencies.used.in.popup.title"), dependencies.toArray(new String[dependencies.size()])) {

        public PopupStep onChosen(final String nameToSelect, final boolean finalChoice) {
          selectNodeInTree(nameToSelect);
          return PopupStep.FINAL_CHOICE;
        }

        public Icon getIconFor(String selection) {
          final Module module = myModulesConfigurator.getModule(selection);
          LOG.assertTrue(module != null, selection + " was not found");
          return module.getModuleType().getNodeIcon(false);
        }

      }).show(new RelativePoint(myTree, location));
    }
  }

  public History getHistory() {
    return myHistory;
  }

  public boolean isHistoryNavigatedNow() {
    return myHistoryNavigatedNow;
  }
}
