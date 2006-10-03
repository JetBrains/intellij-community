package com.intellij.ide.projectView.impl;

import com.intellij.ide.*;
import com.intellij.ide.FileEditorProvider;
import com.intellij.ide.impl.ProjectViewSelectInTarget;
import com.intellij.ide.impl.StructureViewWrapperImpl;
import com.intellij.ide.projectView.HelpID;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.nodes.*;
import com.intellij.ide.scopeView.ScopeViewPane;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.ide.util.EditorHelper;
import com.intellij.ide.util.PackageUtil;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.localVcs.LvcsAction;
import com.intellij.openapi.localVcs.impl.LvcsIntegration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SplitterProportionsData;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import com.intellij.ui.AutoScrollFromSourceHandler;
import com.intellij.ui.AutoScrollToSourceHandler;
import com.intellij.ui.GuiUtils;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.Icons;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.*;
import java.util.List;

public final class ProjectViewImpl extends ProjectView implements JDOMExternalizable, ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.projectView.impl.ProjectViewImpl");
  private CopyPasteManagerEx.CopyPasteDelegator myCopyPasteDelegator;
  private boolean isInitialized;
  private final Project myProject;

  // + options
  private Map<String, Boolean> myFlattenPackages = new THashMap<String, Boolean>();
  private static final boolean ourFlattenPackagesDefaults = false;
  private Map<String, Boolean> myShowMembers = new THashMap<String, Boolean>();
  private static final boolean ourShowMembersDefaults = false;
  private Map<String, Boolean> mySortByType = new THashMap<String, Boolean>();
  private static final boolean ourSortByTypeDefaults = false;
  private Map<String, Boolean> myShowModules = new THashMap<String, Boolean>();
  private static final boolean ourShowModulesDefaults = true;
  private Map<String, Boolean> myShowLibraryContents = new THashMap<String, Boolean>();
  private static final boolean ourShowLibraryContentsDefaults = true;
  private Map<String, Boolean> myHideEmptyPackages = new THashMap<String, Boolean>();
  private static final boolean ourHideEmptyPackagesDefaults = true;
  private Map<String, Boolean> myAbbreviatePackageNames = new THashMap<String, Boolean>();
  private static final boolean ourAbbreviatePackagesDefaults = false;
  private Map<String, Boolean> myAutoscrollToSource = new THashMap<String, Boolean>();
  private static final boolean ourAutoscrollToSourceDefaults = false;
  private Map<String, Boolean> myAutoscrollFromSource = new THashMap<String, Boolean>();
  private static final boolean ourAutoscrollFromSourceDefaults = false;
  private Map<String, Boolean> myShowStructure = new THashMap<String, Boolean>();
  private static final boolean ourShowStructureDefaults = false;

  private String myCurrentViewId;
  private String myCurrentViewSubId;
  // - options

  private AutoScrollToSourceHandler myAutoScrollToSourceHandler;
  private AutoScrollFromSourceHandler myAutoScrollFromSourceHandler;

  private final IdeView myIdeView = new MyIdeView();
  private final MyDeletePSIElementProvider myDeletePSIElementProvider = new MyDeletePSIElementProvider();
  private final ModuleDeleteProvider myDeleteModuleProvider = new ModuleDeleteProvider();

  private JPanel myStructureViewPanel;
  private MyStructureViewWrapperImpl myStructureViewWrapper;

  private JPanel myPanel;
  private final Map<String, AbstractProjectViewPane> myId2Pane = new LinkedHashMap<String, AbstractProjectViewPane>();
  private final Collection<AbstractProjectViewPane> myUninitializedPanes = new THashSet<AbstractProjectViewPane>();

  static final String PROJECT_VIEW_DATA_CONSTANT = "com.intellij.ide.projectView.impl.ProjectViewImpl";
  private DefaultActionGroup myActionGroup;
  private final Runnable myTreeChangeListener;
  private String mySavedPaneId = ProjectViewPane.ID;
  private String mySavedPaneSubId;
  private static final Icon COMPACT_EMPTY_MIDDLE_PACKAGES_ICON = IconLoader.getIcon("/objectBrowser/compactEmptyPackages.png");
  private static final Icon HIDE_EMPTY_MIDDLE_PACKAGES_ICON = IconLoader.getIcon("/objectBrowser/hideEmptyPackages.png");
  @NonNls private static final String ELEMENT_NAVIGATOR = "navigator";
  @NonNls private static final String ATTRIBUTE_CURRENT_VIEW = "currentView";
  @NonNls private static final String ATTRIBUTE_CURRENT_SUBVIEW = "currentSubView";
  @NonNls private static final String ELEMENT_FLATTEN_PACKAGES = "flattenPackages";
  @NonNls private static final String ELEMENT_SHOW_MEMBERS = "showMembers";
  @NonNls private static final String ELEMENT_SHOW_MODULES = "showModules";
  @NonNls private static final String ELEMENT_SHOW_LIBRARY_CONTENTS = "showLibraryContents";
  @NonNls private static final String ELEMENT_HIDE_EMPTY_PACKAGES = "hideEmptyPackages";
  @NonNls private static final String ELEMENT_ABBREVIATE_PACKAGE_NAMES = "abbreviatePackageNames";
  @NonNls private static final String ELEMENT_SHOW_STRUCTURE = "showStructure";
  @NonNls private static final String ELEMENT_AUTOSCROLL_TO_SOURCE = "autoscrollToSource";
  @NonNls private static final String ELEMENT_AUTOSCROLL_FROM_SOURCE = "autoscrollFromSource";
  @NonNls private static final String ELEMENT_SORT_BY_TYPE = "sortByType";
  private ComboBox myCombo;
  private JPanel myViewContentPanel;
  private JPanel myActionGroupPanel;
  private JLabel myLabel;
  private static final Comparator<AbstractProjectViewPane> PANE_WEIGHT_COMPARATOR = new Comparator<AbstractProjectViewPane>() {
    public int compare(final AbstractProjectViewPane o1, final AbstractProjectViewPane o2) {
      return o1.getWeight() - o2.getWeight();
    }
  };
  private final FileEditorManager myFileEditorManager;
  private final SelectInManager mySelectInManager;
  private MyPanel myDataProvider;
  private final SplitterProportionsData splitterProportions = PeerFactory.getInstance().getUIHelper().createSplitterProportionsData();
  private static final Icon BULLET_ICON = IconLoader.getIcon("/general/bullet.png");
  private final ModuleRootListener myModuleRootListener;

  public ProjectViewImpl(Project project, final FileEditorManager fileEditorManager, SelectInManager selectInManager, ProjectRootManager rootManager) {
    myProject = project;
    myFileEditorManager = fileEditorManager;
    mySelectInManager = selectInManager;
    myTreeChangeListener = new Runnable() {
      public void run() {
        updateToolWindowTitle();
      }
    };

    myModuleRootListener = new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {

      }

      public void rootsChanged(ModuleRootEvent event) {
        refresh();
      }
    };
    rootManager.addModuleRootListener(myModuleRootListener);
    myAutoScrollFromSourceHandler = new MyAutoScrollFromSourceHandler();

    myDataProvider = new MyPanel();
    myDataProvider.add(myPanel, BorderLayout.CENTER);
  }

  public void disposeComponent() {
    myAutoScrollFromSourceHandler.dispose();
  }

  public void initComponent() { }

  public void projectClosed() {
    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    if (toolWindowManager != null) {
      toolWindowManager.unregisterToolWindow(ToolWindowId.PROJECT_VIEW);
    }
    ProjectRootManager.getInstance(myProject).removeModuleRootListener(myModuleRootListener);
    dispose();
  }

  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        setupImpl();
      }
    });
  }

  public synchronized void addProjectPane(final AbstractProjectViewPane pane) {
    myUninitializedPanes.add(pane);
    if (isInitialized) {
      doAddUninitializedPanes();
    }
  }

  public synchronized void removeProjectPane(AbstractProjectViewPane pane) {
    //assume we are completely initialized here
    String idToRemove = pane.getId();

    removeSelectInTargetsFor(pane);

    if (!myId2Pane.containsKey(idToRemove)) return;
    pane.removeTreeChangeListener();
    for (int i = myCombo.getItemCount()-1; i>=0; i--) {
      Pair<String, String> ids = (Pair<String, String>)myCombo.getItemAt(i);
      String id = ids.first;
      if (id.equals(idToRemove)) {
        myCombo.removeItemAt(i);
      }
    }
    myId2Pane.remove(idToRemove);
    viewSelectionChanged();
  }

  private void removeSelectInTargetsFor(final AbstractProjectViewPane pane) {
    SelectInTarget[] targets = mySelectInManager.getTargets();
    for (SelectInTarget target : targets) {
      if (pane.getId().equals(target.getMinorViewId())) {
        mySelectInManager.removeTarget(target);
        break;
      }
    }
  }

  private synchronized void doAddUninitializedPanes() {
    for (AbstractProjectViewPane pane : myUninitializedPanes) {
      doAddPane(pane);
    }
    myUninitializedPanes.clear();
  }

  private void doAddPane(final AbstractProjectViewPane newPane) {
    Pair<String, String> selected = (Pair<String, String>)myCombo.getSelectedItem();
    int index;
    for (index = 0; index < myCombo.getItemCount(); index++) {
      Pair<String, String> ids = (Pair<String, String>)myCombo.getItemAt(index);
      String id = ids.first;
      AbstractProjectViewPane pane = myId2Pane.get(id);

      int comp = PANE_WEIGHT_COMPARATOR.compare(pane, newPane);
      LOG.assertTrue(comp != 0);
      if (comp > 0) {
        break;
      }
    }
    final String id = newPane.getId();
    myId2Pane.put(id, newPane);
    String[] subIds = newPane.getSubIds();
    subIds = ArrayUtil.mergeArrays(new String[]{null}, subIds, String.class);
    for (String subId : subIds) {
      myCombo.insertItemAt(Pair.create(id, subId), index++);
    }
    myCombo.setMaximumRowCount(myCombo.getItemCount());
    SelectInTarget selectInTarget = newPane.createSelectInTarget();
    if (selectInTarget != null) {
      mySelectInManager.addTarget(selectInTarget);
    }

    if (id.equals(mySavedPaneId)) {
      changeView(mySavedPaneId, mySavedPaneSubId);
      mySavedPaneId = null;
      mySavedPaneSubId = null;
    }
    else if (selected == null) {
      changeView(id, subIds.length == 1 ? subIds[0] : subIds[1]);
    }
  }

  private void showPane(AbstractProjectViewPane newPane) {
    AbstractProjectViewPane currentPane = getCurrentProjectViewPane();
    PsiElement selectedPsiElement = null;
    Module selectedModule = null;
    if (currentPane != null) {
      if (currentPane != newPane) {
        currentPane.saveExpandedPaths();
      }
      Object selected = currentPane.getSelectedElement();
      if (selected instanceof PsiElement) {
        selectedPsiElement = (PsiElement)selected;
      }
      if (selected instanceof PackageElement) {
        PsiPackage psiPackage = ((PackageElement)selected).getPackage();
        PsiDirectory[] directories = psiPackage.getDirectories();
        selectedPsiElement = directories.length == 0 ? null : directories[0];
      }
      if (selected instanceof Module) {
        selectedModule = (Module)selected;
      }
      currentPane.dispose();
    }
    removeLabelFocusListener();
    myViewContentPanel.removeAll();
    JComponent component = newPane.createComponent();
    myViewContentPanel.setLayout(new BorderLayout());
    myViewContentPanel.add(component, BorderLayout.CENTER);
    myCurrentViewId = newPane.getId();
    String newSubId = myCurrentViewSubId = newPane.getSubId();
    myViewContentPanel.revalidate();
    myViewContentPanel.repaint();
    createToolbarActions();

    newPane.setTreeChangeListener(myTreeChangeListener);
    myAutoScrollToSourceHandler.install(newPane.myTree);

    newPane.getComponentToFocus().requestFocus();
    updateToolWindowTitle();
    showOrHideStructureView(isShowStructure());

    newPane.restoreExpandedPaths();
    if (selectedPsiElement != null) {
      VirtualFile virtualFile = PsiUtil.getVirtualFile(selectedPsiElement);
      if (((ProjectViewSelectInTarget)newPane.createSelectInTarget()).isSubIdSelectable(newSubId, virtualFile)) {
        newPane.select(selectedPsiElement, virtualFile, true);
      }
    }
    else if (selectedModule != null) {
      newPane.select(selectedModule, selectedModule.getModuleFile(), true);
    }
    installLabelFocusListener();
  }

  // public for tests
  public synchronized void setupImpl() {
    myCombo.setRenderer(new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        if (value == null) return this;
        Pair<String, String> ids = (Pair<String, String>)value;
        String id = ids.first;
        String subId = ids.second;
        AbstractProjectViewPane pane = getProjectViewPaneById(id);
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (pane != null) {
          if (subId == null) {
            setText(pane.getTitle());
            setIcon(pane.getIcon());
          }
          else {
            String presentable = pane.getPresentableSubIdName(subId);
            if (index == -1) {
              setText(pane.getTitle() + ": "+presentable);
              setIcon(pane.getIcon());
            }
            else {
              // indent sub id
              setText(presentable);
              setIcon(BULLET_ICON);
            }
          }
        }
        return this;
      }
    });
    myCombo.setMinimumAndPreferredWidth(10);

    myStructureViewWrapper = new MyStructureViewWrapperImpl();
    myStructureViewWrapper.setFileEditor(null);
    myStructureViewPanel.setLayout(new BorderLayout());
    myStructureViewPanel.add(myStructureViewWrapper.getComponent(), BorderLayout.CENTER);

    myActionGroup = new DefaultActionGroup();
    myAutoScrollToSourceHandler = new AutoScrollToSourceHandler() {
      protected boolean isAutoScrollMode() {
        return isAutoscrollToSource(myCurrentViewId);
      }

      protected void setAutoScrollMode(boolean state) {
        setAutoscrollToSource(state, myCurrentViewId);
      }
    };

    myAutoScrollFromSourceHandler.install();

    final ActionToolbar toolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.PROJECT_VIEW_TOOLBAR, myActionGroup, true);
    JComponent toolbarComponent = toolBar.getComponent();
    myActionGroupPanel.setLayout(new BorderLayout());
    myActionGroupPanel.add(toolbarComponent, BorderLayout.NORTH);

    myStructureViewPanel.setVisible(isShowStructure());

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
      ToolWindow toolWindow = toolWindowManager.registerToolWindow(ToolWindowId.PROJECT_VIEW, getComponent(), ToolWindowAnchor.LEFT);
      toolWindow.setIcon(IconLoader.getIcon("/general/toolWindowProject.png"));
    }

    myCopyPasteDelegator = new CopyPasteManagerEx.CopyPasteDelegator(myProject, myPanel) {
      protected PsiElement[] getSelectedElements() {
        final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
        return viewPane == null ? null : viewPane.getSelectedPSIElements();
      }
    };

    myCombo.addPopupMenuListener(new PopupMenuListener() {
      public void popupMenuWillBecomeVisible(PopupMenuEvent e) {

      }

      public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        if (!viewSelectionChanged()) {
          ToolWindowManager.getInstance(myProject).activateEditorComponent();
        }
      }

      public void popupMenuCanceled(PopupMenuEvent e) {
        ToolWindowManager.getInstance(myProject).activateEditorComponent();
      }
    });
    installLabelFocusListener();

    GuiUtils.replaceJSplitPaneWithIDEASplitter(myPanel);
    SwingUtilities.invokeLater(new Runnable(){
      public void run() {
        splitterProportions.restoreSplitterProportions(myPanel);
      }
    });

    isInitialized = true;
    doAddUninitializedPanes();
  }

  private final FocusListener myLabelFocusListener = new FocusListener() {
    public void focusGained(FocusEvent e) {
      if (!myCombo.isPopupVisible()) {
        myCombo.requestFocusInWindow();
        myCombo.showPopup();
      }
    }

    public void focusLost(FocusEvent e) {

    }
  };
  private void installLabelFocusListener() {
    myLabel.addFocusListener(myLabelFocusListener);
  }
  private void removeLabelFocusListener() {
    myLabel.removeFocusListener(myLabelFocusListener);
  }

  private boolean viewSelectionChanged() {
    Pair<String,String> ids = (Pair<String,String>)myCombo.getSelectedItem();
    if (ids == null) return false;
    final String id = ids.first;
    String subId = ids.second;
    if (ids.equals(Pair.create(myCurrentViewId, myCurrentViewSubId))) return false;
    final AbstractProjectViewPane newPane = getProjectViewPaneById(id);
    if (newPane == null) return false;
    newPane.setSubId(subId);
    String[] subIds = newPane.getSubIds();

    if (subId == null && subIds.length != 0) {
      final String firstNonTrivialSubId = subIds[0];
      SwingUtilities.invokeLater(new Runnable(){
        public void run() {
          changeView(id, firstNonTrivialSubId);
          newPane.setSubId(firstNonTrivialSubId);
        }
      });
    }
    else {
      showPane(newPane);
    }
    return true;
  }

  private void createToolbarActions() {
    myActionGroup.removeAll();
    myActionGroup.add(new PaneOptionAction(myFlattenPackages,
                                           IdeBundle.message("action.flatten.packages"),
                                           IdeBundle.message("action.flatten.packages"),
                                           Icons.FLATTEN_PACKAGES_ICON,
                                           ourFlattenPackagesDefaults) {
      public void setSelected(AnActionEvent event, boolean flag) {
        final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
        final SelectionInfo selectionInfo = SelectionInfo.create(viewPane);

        super.setSelected(event, flag);

        selectionInfo.apply(viewPane);
      }
    });

    class FlattenPackagesDependableAction extends PaneOptionAction {
      public FlattenPackagesDependableAction(Map<String, Boolean> optionsMap,
                                             final String text,
                                             final String description,
                                             final Icon icon,
                                             boolean optionDefaultValue) {
        super(optionsMap, text, description, icon, optionDefaultValue);
      }

      public void update(AnActionEvent e) {
        super.update(e);
        final Presentation presentation = e.getPresentation();
        presentation.setEnabled(isFlattenPackages(myCurrentViewId));
      }
    }
    myActionGroup.add(new HideEmptyMiddlePackagesAction());
    myActionGroup.add(new FlattenPackagesDependableAction(myAbbreviatePackageNames,
                                                          IdeBundle.message("action.abbreviate.qualified.package.names"),
                                                          IdeBundle.message("action.abbreviate.qualified.package.names"),
                                                          IconLoader.getIcon("/objectBrowser/abbreviatePackageNames.png"),
                                                          ourAbbreviatePackagesDefaults) {
      public boolean isSelected(AnActionEvent event) {
        return super.isSelected(event) && isAbbreviatePackageNames(myCurrentViewId);
      }


      public void update(AnActionEvent e) {
        super.update(e);
        if (ScopeViewPane.ID.equals(myCurrentViewId)) {
          e.getPresentation().setEnabled(false);
        }
      }
    });
    myActionGroup.add(new PaneOptionAction(myShowMembers,
                                           IdeBundle.message("action.show.members"),
                                           IdeBundle.message("action.show.hide.members"),
                                           IconLoader.getIcon("/objectBrowser/showMembers.png"),
                                           ourShowMembersDefaults));
    myActionGroup.add(myAutoScrollToSourceHandler.createToggleAction());
    myActionGroup.add(myAutoScrollFromSourceHandler.createToggleAction());
    myActionGroup.add(new ShowStructureAction());
    myActionGroup.add(new SortByTypeAction());

    AnAction collapseAllAction = CommonActionsManager.getInstance().createCollapseAllAction(new TreeExpander() {
      public void expandAll() {

      }

      public boolean canExpand() {
        return false;
      }

      public void collapseAll() {
        AbstractProjectViewPane pane = getCurrentProjectViewPane();
        JTree tree = pane.myTree;
        if (tree != null) {
          TreeUtil.collapseAll(tree, -1);
        }
      }

      public boolean canCollapse() {
        return true;
      }
    }, getComponent());
    myActionGroup.add(collapseAllAction);

    getCurrentProjectViewPane().addToolbarActions(myActionGroup);
  }

  public AbstractProjectViewPane getProjectViewPaneById(String id) {
    return myId2Pane.get(id);
  }

  public AbstractProjectViewPane getCurrentProjectViewPane() {
    return getProjectViewPaneById(myCurrentViewId);
  }

  public void refresh() {
    AbstractProjectViewPane currentProjectViewPane = getCurrentProjectViewPane();
    if (currentProjectViewPane != null) {
      // may be null for e.g. default project
      currentProjectViewPane.updateFromRoot(false);
    }
  }

  public void select(final Object element, VirtualFile file, boolean requestFocus) {
    final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
    if (viewPane != null) {
      viewPane.select(element, file, requestFocus);
    }
  }

  private void dispose() {
    getCurrentProjectViewPane().dispose();
    myStructureViewWrapper.dispose();
    myStructureViewWrapper = null;
  }
  public void rebuildStructureViewPane() {
    if (myStructureViewWrapper != null) {
      myStructureViewWrapper.rebuild();
    }
  }

  private JComponent getComponent() {
    return myDataProvider;
  }

  public String getCurrentViewId() {
    return myCurrentViewId;
  }

  private void updateToolWindowTitle() {
    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    ToolWindow toolWindow = toolWindowManager == null ? null : toolWindowManager.getToolWindow(ToolWindowId.PROJECT_VIEW);
    if (toolWindow == null) return;

    final PsiElement element = (PsiElement)myDataProvider.getData(DataConstants.PSI_ELEMENT);
    String title;
    if (element != null) {
      // todo!!!
      if (element instanceof PsiDirectory) {
        PsiDirectory directory = (PsiDirectory)element;
        PsiPackage aPackage = directory.getPackage();
        if (aPackage != null) {
          title = aPackage.getQualifiedName();
        }
        else {
          title = directory.getVirtualFile().getPresentableUrl();
        }
      }
      else if (element instanceof PsiFile) {
        PsiFile file = (PsiFile)element;
        title = file.getVirtualFile().getPresentableUrl();
      }
      else if (element instanceof PsiClass) {
        PsiClass psiClass = (PsiClass)element;
        title = psiClass.getQualifiedName();
      }
      else if (element instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)element;
        PsiClass aClass = method.getContainingClass();
        if (aClass != null) {
          title = aClass.getQualifiedName();
        }
        else {
          title = method.toString();
        }
      }
      else if (element instanceof PsiField) {
        PsiField field = (PsiField)element;
        PsiClass aClass = field.getContainingClass();
        if (aClass != null) {
          title = aClass.getQualifiedName();
        }
        else {
          title = field.toString();
        }
      }
      else if (element instanceof PsiPackage) {
        title = ((PsiPackage)element).getQualifiedName();
      }
      else {
        PsiFile file = element.getContainingFile();
        if (file != null) {
          title = file.getVirtualFile().getPresentableUrl();
        }
        else {
          title = element.toString();
        }
      }
    }
    else {
      title = "";
      if (myProject != null) {
        title = myProject.getProjectFile() != null ? myProject.getProjectFile().getPresentableUrl() : "";
      }
    }

    toolWindow.setTitle(title);
  }

  public PsiElement getParentOfCurrentSelection() {
    final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
    if (viewPane == null) {
      return null;
    }
    TreePath path = viewPane.getSelectedPath();
    if (path == null) {
      return null;
    }
    path = path.getParentPath();
    if (path == null) {
      return null;
    }
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
    Object userObject = node.getUserObject();
    if (userObject instanceof ProjectViewNode) {
      ProjectViewNode descriptor = (ProjectViewNode)userObject;
      Object element = descriptor.getValue();
      if (element instanceof PsiElement) {
        PsiElement psiElement = (PsiElement)element;
        if (!psiElement.isValid()) return null;
        return psiElement;
      }
      else {
        return null;
      }
    }
    return null;
  }


  private class PaneOptionAction extends ToggleAction {
    private final Map<String, Boolean> myOptionsMap;
    private final boolean myOptionDefaultValue;

    PaneOptionAction(Map<String, Boolean> optionsMap, final String text, final String description, final Icon icon, boolean optionDefaultValue) {
      super(text, description, icon);
      myOptionsMap = optionsMap;
      myOptionDefaultValue = optionDefaultValue;
    }

    public boolean isSelected(AnActionEvent event) {
      return getPaneOptionValue(myOptionsMap, myCurrentViewId, myOptionDefaultValue);
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      setPaneOption(myOptionsMap, flag, myCurrentViewId, true);
    }
  }

  private final class ShowStructureAction extends ToggleAction {
    ShowStructureAction() {
      super(IdeBundle.message("action.show.structure"), IdeBundle.message("action.description.show.structure"), IconLoader.getIcon("/objectBrowser/showStructure.png"));
    }

    public boolean isSelected(AnActionEvent event) {
      return isShowStructure();
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      showOrHideStructureView(flag);
    }
  }

  private void showOrHideStructureView(boolean toShow) {
    boolean hadFocus = IJSwingUtilities.hasFocus2(getComponent());

    myStructureViewPanel.setVisible(toShow);
    setShowStructure(toShow, myCurrentViewId);

    if (hadFocus) {
      final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
      if (viewPane != null) {
        viewPane.getComponentToFocus().requestFocus();
      }
    }

    if (toShow) {
      VirtualFile[] files = myFileEditorManager.getSelectedFiles();
      FileEditor editor = files.length == 0 ? null : myFileEditorManager.getSelectedEditor(files[0]);
      myStructureViewWrapper.setFileEditor(editor);
    }
  }

  public void changeView() {
    final List<AbstractProjectViewPane> views = new ArrayList<AbstractProjectViewPane>(myId2Pane.values());
    views.remove(getCurrentProjectViewPane());
    Collections.sort(views, PANE_WEIGHT_COMPARATOR);

    final JList list = new JList(views.toArray(new Object[views.size()]));
    list.setCellRenderer(new DefaultListCellRenderer(){
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        AbstractProjectViewPane pane = (AbstractProjectViewPane)value;
        setText(pane.getTitle());
        return this;
      }
    });

    if (!views.isEmpty()) {
      list.setSelectedValue(views.get(0), true);
    }
    Runnable runnable = new Runnable() {
      public void run() {
        if (list.getSelectedIndex() < 0) return;
        AbstractProjectViewPane pane = (AbstractProjectViewPane)list.getSelectedValue();
        changeView(pane.getId());
      }
    };

    new PopupChooserBuilder(list).
      setTitle(IdeBundle.message("title.popup.views")).
      setItemChoosenCallback(runnable).
      createPopup().showInCenterOf(getComponent());
  }

  public void changeView(@NotNull String viewId) {
    changeView(viewId, null);
  }

  public void changeView(@NotNull String viewId, @Nullable String subId) {
    AbstractProjectViewPane pane = getProjectViewPaneById(viewId);
    if (!viewId.equals(getCurrentViewId())
        || subId != null && !subId.equals(pane.getSubId()) ||
        // element not in model anymore
        pane != null && ((DefaultComboBoxModel)myCombo.getModel()).getIndexOf(Pair.create(viewId, pane.getSubId())) == -1) {
      myCombo.setSelectedItem(Pair.create(viewId, subId));
      viewSelectionChanged();
    }
  }

  private final class MyDeletePSIElementProvider implements DeleteProvider {
    public boolean canDeleteElement(DataContext dataContext) {
      final PsiElement[] elements = getElementsToDelete();
      return DeleteHandler.shouldEnableDeleteAction(elements);
    }

    public void deleteElement(DataContext dataContext) {
      List<PsiElement> allElements = Arrays.asList(getElementsToDelete());
      List<PsiElement> validElements = new ArrayList<PsiElement>();
      for (PsiElement psiElement : allElements) {
        if (psiElement != null && psiElement.isValid()) validElements.add(psiElement);
      }
      final PsiElement[] elements = validElements.toArray(new PsiElement[validElements.size()]);

      LvcsAction action = LvcsIntegration.checkinFilesBeforeRefactoring(myProject, IdeBundle.message("progress.deleting"));
      try {
        DeleteHandler.deletePsiElement(elements, myProject);
      }
      finally {
        LvcsIntegration.checkinFilesAfterRefactoring(myProject, action);
      }
    }

    private PsiElement[] getElementsToDelete() {
      final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
      PsiElement[] elements = viewPane.getSelectedPSIElements();
      for (int idx = 0; idx < elements.length; idx++) {
        final PsiElement element = elements[idx];
        if (element instanceof PsiDirectory) {
          PsiDirectory directory = (PsiDirectory)element;
          if (isHideEmptyMiddlePackages(viewPane.getId()) && directory.getChildren().length == 0 && directory.getPackage() != null) {
            while (true) {
              PsiDirectory parent = directory.getParentDirectory();
              if (parent == null) break;
              PsiPackage psiPackage = parent.getPackage();
              if (psiPackage == null || psiPackage.getName() == null) break;
              PsiElement[] children = parent.getChildren();
              if (children.length == 0 || children.length == 1 && children[0] == directory) {
                directory = parent;
              }
              else {
                break;
              }
            }
            elements[idx] = directory;
          }
          final VirtualFile virtualFile = directory.getVirtualFile();
          final String path = virtualFile.getPath();
          if (path.endsWith(JarFileSystem.JAR_SEPARATOR)) { // if is jar-file root
            final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(
              path.substring(0, path.length() - JarFileSystem.JAR_SEPARATOR.length()));
            if (vFile != null) {
              final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(vFile);
              if (psiFile != null) {
                elements[idx] = psiFile;
              }
            }
          }
        }
      }
      return elements;
    }

  }

  @NotNull
  public String getComponentName() {
    return "ProjectView";
  }

  private final class MyStructureViewWrapperImpl extends StructureViewWrapperImpl {
    MyStructureViewWrapperImpl() {
      super(myProject);
    }

    protected boolean isStructureViewShowing() {
      return myStructureViewPanel.isVisible();
    }
  }

  private final class MyPanel extends JPanel implements DataProvider {
    MyPanel() {
      super(new BorderLayout());
    }

    private Object getSelectedNodeElement() {
      final AbstractProjectViewPane currentProjectViewPane = getCurrentProjectViewPane();
      if (currentProjectViewPane == null) { // can happen if not initialized yet
        return null;
      }
      DefaultMutableTreeNode node = currentProjectViewPane.getSelectedNode();
      if (node == null) {
        return null;
      }
      Object userObject = node.getUserObject();
      if (userObject instanceof AbstractTreeNode){
        return ((AbstractTreeNode)userObject).getValue();
      }
      if (!(userObject instanceof NodeDescriptor)) {
        return null;
      }
      return ((NodeDescriptor)userObject).getElement();
    }

    public Object getData(String dataId) {
      final AbstractProjectViewPane currentProjectViewPane = getCurrentProjectViewPane();
      if (currentProjectViewPane != null) {
        final Object paneSpecificData = currentProjectViewPane.getData(dataId);
        if (paneSpecificData != null) return paneSpecificData;
      }
      if (DataConstantsEx.RESOURCE_BUNDLE_ARRAY.equals(dataId)){
        final List<ResourceBundle> selectedElements = getSelectedElements(ResourceBundle.class);
        return selectedElements.isEmpty() ? null : selectedElements.toArray(new ResourceBundle[selectedElements.size()]);
      }
      if (DataConstants.PSI_ELEMENT.equals(dataId)) {
        final PsiElement psiElement;
        Object element = getSelectedNodeElement();
        if (element instanceof PsiElement) {
          psiElement = (PsiElement)element;
        }
        else if (element instanceof PackageElement) {
          psiElement = ((PackageElement)element).getPackage();
        }
        else {
          psiElement = null;
        }
        return psiElement != null && psiElement.isValid() ? psiElement : null;
      }
      if (DataConstants.PSI_ELEMENT_ARRAY.equals(dataId)) {
        if (currentProjectViewPane == null) {
          return null;
        }
        PsiElement[] elements = currentProjectViewPane.getSelectedPSIElements();
        return elements.length == 0 ? null : elements;
      }
      if (DataConstantsEx.TARGET_PSI_ELEMENT.equals(dataId)) {
        return null;
      }
      if (DataConstants.CUT_PROVIDER.equals(dataId)) {
        return myCopyPasteDelegator.getCutProvider();
      }
      if (DataConstants.COPY_PROVIDER.equals(dataId)) {
        return myCopyPasteDelegator.getCopyProvider();
      }
      if (DataConstants.PASTE_PROVIDER.equals(dataId)) {
        return myCopyPasteDelegator.getPasteProvider();
      }
      if (DataConstants.IDE_VIEW.equals(dataId)) {
        return myIdeView;
      }
      if (DataConstants.DELETE_ELEMENT_PROVIDER.equals(dataId)) {
        Object selectedNode = getSelectedNodeElement();
        if (selectedNode instanceof Module) {
          return myDeleteModuleProvider;
        }
        final LibraryOrderEntry orderEntry = getSelectedLibrary();
        if (orderEntry != null) {
          return new DeleteProvider(){
            public void deleteElement(DataContext dataContext) {
              detachLibrary(orderEntry, myProject);
            }

            public boolean canDeleteElement(DataContext dataContext) {
              return true;
            }
          };
        }
        return myDeletePSIElementProvider;
      }
      if (DataConstants.HELP_ID.equals(dataId)) {
        return HelpID.PROJECT_VIEWS;
      }
      if (PROJECT_VIEW_DATA_CONSTANT.equals(dataId)) {
        return ProjectViewImpl.this;
      }
      if (DataConstants.PROJECT_CONTEXT.equals(dataId)) {
        Object selected = getSelectedNodeElement();
        return selected instanceof Project ? selected : null;
      }
      if (DataConstants.MODULE_CONTEXT.equals(dataId)) {
        Object selected = getSelectedNodeElement();
        return selected instanceof Module ? selected : null;
      }
      if (DataConstantsEx.PACKAGE_ELEMENT.equals(dataId)){
        Object selected = getSelectedNodeElement();
        return selected instanceof PackageElement ? selected : null;
      }
      if (DataConstants.MODULE_CONTEXT_ARRAY.equals(dataId)) {
        return getSelectedModules();
      }
      if (DataConstantsEx.MODULE_GROUP_ARRAY.equals(dataId)){
        final List<ModuleGroup> selectedElements = getSelectedElements(ModuleGroup.class);
        return selectedElements.isEmpty() ? null : selectedElements.toArray(new ModuleGroup[selectedElements.size()]);
      }
      if (DataConstantsEx.LIBRARY_GROUP_ARRAY.equals(dataId)){
        final List<LibraryGroupElement> selectedElements = getSelectedElements(LibraryGroupElement.class);
        return selectedElements.isEmpty() ? null : selectedElements.toArray(new LibraryGroupElement[selectedElements.size()]);
      }
      if (DataConstantsEx.NAMED_LIBRARY_ARRAY.equals(dataId)){
        final List<NamedLibraryElement> selectedElements = getSelectedElements(NamedLibraryElement.class);
        return selectedElements.isEmpty() ? null : selectedElements.toArray(new NamedLibraryElement[selectedElements.size()]);
      }
      return null;
    }

    private LibraryOrderEntry getSelectedLibrary() {
      final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
      DefaultMutableTreeNode node = viewPane != null ? viewPane.getSelectedNode() : null;
      if (node == null) return null;
      while (true) {
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode)node.getParent();
        if (parent == null) break;
        Object userObject = parent.getUserObject();
        if (userObject instanceof LibraryGroupNode) {
          userObject = node.getUserObject();
          if (userObject instanceof NamedLibraryElementNode) {
            NamedLibraryElement element = ((NamedLibraryElementNode)userObject).getValue();
            OrderEntry orderEntry = element.getOrderEntry();
            return orderEntry instanceof LibraryOrderEntry ? (LibraryOrderEntry)orderEntry : null;
          }
          PsiDirectory directory = ((PsiDirectoryNode)userObject).getValue();
          VirtualFile virtualFile = directory.getVirtualFile();
          Module module = (Module)((AbstractTreeNode)((DefaultMutableTreeNode)parent.getParent()).getUserObject()).getValue();

          if (module == null) return null;
          ModuleFileIndex index = ModuleRootManager.getInstance(module).getFileIndex();
          return (LibraryOrderEntry)index.getOrderEntryForFile(virtualFile);
        }
        node = parent;
      }

      return null;
    }

    private void detachLibrary(final LibraryOrderEntry orderEntry, final Project project) {
      final Module module = orderEntry.getOwnerModule();
      String message = IdeBundle.message("detach.library.from.module", orderEntry.getPresentableName(), module.getName());
      String title = IdeBundle.message("detach.library");
      int ret = Messages.showOkCancelDialog(project, message, title, Messages.getQuestionIcon());
      if (ret != 0) return;
      CommandProcessor.getInstance().executeCommand(module.getProject(), new Runnable() {
        public void run() {
          final Runnable action = new Runnable() {
            public void run() {
              ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
              for (OrderEntry entry : model.getOrderEntries()) {
                if (entry instanceof LibraryOrderEntry && ((LibraryOrderEntry)entry).getLibrary() == orderEntry.getLibrary()) {
                  model.removeOrderEntry(entry);
                }
              }
              model.commit();
            }
          };
          ApplicationManager.getApplication().runWriteAction(action);
        }
      }, title, null);
    }

    private Module[] getSelectedModules() {
      final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
      if (viewPane == null) return null;
      final Object[] elements = viewPane.getSelectedElements();
      ArrayList<Module> result = new ArrayList<Module>();
      for (Object element : elements) {
        if (element instanceof Module) {
          final Module module = (Module)element;
          if (!module.isDisposed()) {
            result.add(module);
          }
        }
        else if (element instanceof ModuleGroup) {
          Collection<Module> modules = ((ModuleGroup)element).modulesInGroup(myProject, true);
          result.addAll(modules);
        }
      }
      if (result.isEmpty()) {
        return null;
      }
      else {
        return result.toArray(new Module[result.size()]);
      }
    }
  }

  private <T>List<T> getSelectedElements(Class<T> klass) {
    ArrayList<T> result = new ArrayList<T>();
    final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
    if (viewPane == null) return result;
    final Object[] elements = viewPane.getSelectedElements();
    for (Object element : elements) {
      //element still valid
      if (element != null && klass.isAssignableFrom(element.getClass())) {
        result.add((T)element);
      }
    }
    return result;
  }

  private final class MyIdeView implements IdeView {
    public void selectElement(PsiElement element) {
      selectPsiElement(element, true);
      if (element != null) {
        final boolean isDirectory = element instanceof PsiDirectory;
        if (!isDirectory) {
          Editor editor = EditorHelper.openInEditor(element);
          if (editor != null) {
            ToolWindowManager.getInstance(myProject).activateEditorComponent();
          }
        }
      }
    }

    public PsiDirectory[] getDirectories() {
      final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
      DefaultMutableTreeNode node = viewPane != null ? viewPane.getSelectedNode() : null;

      while (true) {
        if (node == null) {
          break;
        }
        final Object userObject = node.getUserObject();
        if (userObject instanceof PsiDirectoryNode ||
            userObject instanceof ProjectViewModuleNode
            || userObject instanceof PackageViewModuleNode
            || userObject instanceof PackageElementNode) {
          break;
        }
        node = (DefaultMutableTreeNode)node.getParent();
      }

      if (node == null) {
        return PsiDirectory.EMPTY_ARRAY;
      }
      final Object userObject = node.getUserObject();
      if (userObject instanceof PsiDirectoryNode) {
        PsiDirectory directory = ((PsiDirectoryNode)userObject).getValue();
        if (directory != null) {
          return new PsiDirectory[]{directory};
        }
      }
      else if (userObject instanceof AbstractModuleNode) {
        final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(((AbstractModuleNode)userObject).getValue());
        final VirtualFile[] sourceRoots = moduleRootManager.getSourceRoots();
        List<PsiDirectory> dirs = new ArrayList<PsiDirectory>(sourceRoots.length);
        final PsiManager psiManager = PsiManager.getInstance(myProject);
        for (final VirtualFile sourceRoot : sourceRoots) {
          final PsiDirectory directory = psiManager.findDirectory(sourceRoot);
          if (directory != null) {
            dirs.add(directory);
          }
        }
        return dirs.toArray(new PsiDirectory[dirs.size()]);
      } else if (userObject instanceof PackageElementNode) {
        final PsiPackage aPackage = ((PackageElementNode)userObject).getValue().getPackage();
        if (aPackage == null || !aPackage.isValid()) {
          return PsiDirectory.EMPTY_ARRAY;
        }
        else {
          return aPackage.getDirectories();
        }
      }

      return PsiDirectory.EMPTY_ARRAY;
    }

    public PsiDirectory getOrChooseDirectory() {
      return PackageUtil.getOrChooseDirectory(this);
    }
  }

  public void selectPsiElement(PsiElement element, boolean requestFocus) {
    if (element == null) return;
    VirtualFile virtualFile = PsiUtil.getVirtualFile(element);
    select(element, virtualFile, requestFocus);
  }


  private static void readOption(Element node, Map<String, Boolean> options) {
    if (node == null) return;
    List attributes = node.getAttributes();
    for (final Object attribute1 : attributes) {
      Attribute attribute = (Attribute)attribute1;
      options.put(attribute.getName(), Boolean.TRUE.toString().equals(attribute.getValue()) ? Boolean.TRUE : Boolean.FALSE);
    }
  }

  private static void writeOption(Element parentNode, Map<String, Boolean> optionsForPanes, String optionName) {
    Element e = new Element(optionName);
    for (Map.Entry<String, Boolean> entry : optionsForPanes.entrySet()) {
      final String key = entry.getKey();
      if (key != null) { //SCR48267
        e.setAttribute(key, Boolean.toString(entry.getValue().booleanValue()));
      }
    }

    parentNode.addContent(e);
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    Element navigatorElement = parentNode.getChild(ELEMENT_NAVIGATOR);
    if (navigatorElement != null) {
      mySavedPaneId = navigatorElement.getAttributeValue(ATTRIBUTE_CURRENT_VIEW);
      mySavedPaneSubId = navigatorElement.getAttributeValue(ATTRIBUTE_CURRENT_SUBVIEW);
      if (mySavedPaneId == null) {
        mySavedPaneId = ProjectViewPane.ID;
        mySavedPaneSubId = null;
      }
      readOption(navigatorElement.getChild(ELEMENT_FLATTEN_PACKAGES), myFlattenPackages);
      readOption(navigatorElement.getChild(ELEMENT_SHOW_MEMBERS), myShowMembers);
      readOption(navigatorElement.getChild(ELEMENT_SHOW_MODULES), myShowModules);
      readOption(navigatorElement.getChild(ELEMENT_SHOW_LIBRARY_CONTENTS), myShowLibraryContents);
      readOption(navigatorElement.getChild(ELEMENT_HIDE_EMPTY_PACKAGES), myHideEmptyPackages);
      readOption(navigatorElement.getChild(ELEMENT_ABBREVIATE_PACKAGE_NAMES), myAbbreviatePackageNames);
      readOption(navigatorElement.getChild(ELEMENT_SHOW_STRUCTURE), myShowStructure);
      readOption(navigatorElement.getChild(ELEMENT_AUTOSCROLL_TO_SOURCE), myAutoscrollToSource);
      readOption(navigatorElement.getChild(ELEMENT_AUTOSCROLL_FROM_SOURCE), myAutoscrollFromSource);
      readOption(navigatorElement.getChild(ELEMENT_SORT_BY_TYPE), mySortByType);

      splitterProportions.readExternal(navigatorElement);
    }
  }

  public void writeExternal(Element parentNode) throws WriteExternalException {
    Element navigatorElement = new Element(ELEMENT_NAVIGATOR);
    AbstractProjectViewPane currentPane = getCurrentProjectViewPane();
    if (currentPane != null) {
      navigatorElement.setAttribute(ATTRIBUTE_CURRENT_VIEW, currentPane.getId());
      String subId = currentPane.getSubId();
      if (subId != null) {
        navigatorElement.setAttribute(ATTRIBUTE_CURRENT_SUBVIEW, subId);
      }
    }
    writeOption(navigatorElement, myFlattenPackages, ELEMENT_FLATTEN_PACKAGES);
    writeOption(navigatorElement, myShowMembers, ELEMENT_SHOW_MEMBERS);
    writeOption(navigatorElement, myShowModules, ELEMENT_SHOW_MODULES);
    writeOption(navigatorElement, myShowLibraryContents, ELEMENT_SHOW_LIBRARY_CONTENTS);
    writeOption(navigatorElement, myHideEmptyPackages, ELEMENT_HIDE_EMPTY_PACKAGES);
    writeOption(navigatorElement, myAbbreviatePackageNames, ELEMENT_ABBREVIATE_PACKAGE_NAMES);
    writeOption(navigatorElement, myShowStructure, ELEMENT_SHOW_STRUCTURE);
    writeOption(navigatorElement, myAutoscrollToSource, ELEMENT_AUTOSCROLL_TO_SOURCE);
    writeOption(navigatorElement, myAutoscrollFromSource, ELEMENT_AUTOSCROLL_FROM_SOURCE);
    writeOption(navigatorElement, mySortByType, ELEMENT_SORT_BY_TYPE);

    splitterProportions.saveSplitterProportions(myPanel);
    splitterProportions.writeExternal(navigatorElement);
    parentNode.addContent(navigatorElement);

    // for compatibility with idea 5.1
    @Deprecated @NonNls final String ATTRIBUTE_SPLITTER_PROPORTION = "splitterProportion";
    navigatorElement.setAttribute(ATTRIBUTE_SPLITTER_PROPORTION, "0.5");
  }

  public boolean isAutoscrollToSource(String paneId) {
    return getPaneOptionValue(myAutoscrollToSource, paneId, ourAutoscrollToSourceDefaults);
  }

  public void setAutoscrollToSource(boolean autoscrollMode, String paneId) {
    myAutoscrollToSource.put(paneId, autoscrollMode ? Boolean.TRUE : Boolean.FALSE);
  }

  public boolean isAutoscrollFromSource(String paneId) {
    return getPaneOptionValue(myAutoscrollFromSource, paneId, ourAutoscrollFromSourceDefaults);
  }

  public void setAutoscrollFromSource(boolean autoscrollMode, String paneId) {
    setPaneOption(myAutoscrollFromSource, autoscrollMode, paneId, false);
  }

  public boolean isFlattenPackages(String paneId) {
    return getPaneOptionValue(myFlattenPackages, paneId, ourFlattenPackagesDefaults);
  }

  public void setFlattenPackages(boolean flattenPackages, String paneId) {
    setPaneOption(myFlattenPackages, flattenPackages, paneId, true);
  }

  public boolean isShowMembers(String paneId) {
    return getPaneOptionValue(myShowMembers, paneId, ourShowMembersDefaults);
  }

  public boolean isHideEmptyMiddlePackages(String paneId) {
    return getPaneOptionValue(myHideEmptyPackages, paneId, ourHideEmptyPackagesDefaults);
  }

  public boolean isAbbreviatePackageNames(String paneId) {
    return getPaneOptionValue(myAbbreviatePackageNames, paneId, ourAbbreviatePackagesDefaults);
  }

  public void setShowMembers(boolean showMembers, String paneId) {
    setPaneOption(myShowMembers, showMembers, paneId, true);
  }

  public boolean isShowLibraryContents(String paneId) {
    return getPaneOptionValue(myShowLibraryContents, paneId, ourShowLibraryContentsDefaults);
  }

  public void setShowLibraryContents(boolean showLibraryContents, String paneId) {
    setPaneOption(myShowLibraryContents, showLibraryContents, paneId, true);
  }

  public boolean isShowModules(String paneId) {
    return getPaneOptionValue(myShowModules, paneId, ourShowModulesDefaults);
  }

  public void setShowModules(boolean showModules, String paneId) {
    setPaneOption(myShowModules, showModules, paneId, true);
  }

  public void setHideEmptyPackages(boolean hideEmptyPackages, String paneId) {
    setPaneOption(myHideEmptyPackages, hideEmptyPackages, paneId, true);
  }

  public void setAbbreviatePackageNames(boolean abbreviatePackageNames, String paneId) {
    setPaneOption(myAbbreviatePackageNames, abbreviatePackageNames, paneId, true);
  }

  private void setPaneOption(Map<String, Boolean> optionsMap, boolean value, String paneId, final boolean updatePane) {
    optionsMap.put(paneId, value ? Boolean.TRUE : Boolean.FALSE);
    if (updatePane) {
      final AbstractProjectViewPane pane = getProjectViewPaneById(paneId);
      if (pane != null) {
        pane.updateFromRoot(false);
      }
    }
  }

  private static boolean getPaneOptionValue(Map<String, Boolean> optionsMap, String paneId, boolean defaultValue) {
    final Boolean value = optionsMap.get(paneId);
    return value == null ? defaultValue : value.booleanValue();
  }

  public boolean isShowStructure() {
    Boolean status = myShowStructure.get(getCurrentViewId());
    return status == null ? ourShowStructureDefaults : status.booleanValue();
  }

  public void setShowStructure(boolean showStructure, String paneId) {
    myShowStructure.put(paneId, showStructure ? Boolean.TRUE : Boolean.FALSE);
  }

  static {
    RenameHandlerRegistry.getInstance().registerHandler(RenameModuleHandler.INSTANCE);
  }

  private class HideEmptyMiddlePackagesAction extends PaneOptionAction {
    public HideEmptyMiddlePackagesAction() {
      super(myHideEmptyPackages, "", "", null, ourHideEmptyPackagesDefaults);
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
      final SelectionInfo selectionInfo = SelectionInfo.create(viewPane);

      super.setSelected(event, flag);

      selectionInfo.apply(viewPane);
    }

    public void update(AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      if (isFlattenPackages(myCurrentViewId)){
        presentation.setText(IdeBundle.message("action.hide.empty.middle.packages"));
        presentation.setDescription(IdeBundle.message("action.show.hide.empty.middle.packages"));
        presentation.setIcon(HIDE_EMPTY_MIDDLE_PACKAGES_ICON);
      }
      else {
        presentation.setText(IdeBundle.message("action.compact.empty.middle.packages"));
        presentation.setDescription(IdeBundle.message("action.show.compact.empty.middle.packages"));
        presentation.setIcon(COMPACT_EMPTY_MIDDLE_PACKAGES_ICON);
      }
    }
  }

  private static class SelectionInfo {
    private final Object[] myElements;

    private SelectionInfo(Object[] elements) {
      myElements = elements;
    }

    public void apply(final AbstractProjectViewPane viewPane) {
      if (viewPane == null) {
        return;
      }
      AbstractTreeBuilder treeBuilder = viewPane.myTreeBuilder;
      JTree tree = viewPane.myTree;
      DefaultTreeModel treeModel = (DefaultTreeModel)tree.getModel();
      List<TreePath> paths = new ArrayList<TreePath>(myElements.length);
      for (final Object element : myElements) {
        DefaultMutableTreeNode node = treeBuilder.getNodeForElement(element);
        if (node == null) {
          treeBuilder.buildNodeForElement(element);
          node = treeBuilder.getNodeForElement(element);
        }
        if (node != null) {
          paths.add(new TreePath(treeModel.getPathToRoot(node)));
        }
      }
      if (!paths.isEmpty()) {
        tree.setSelectionPaths(paths.toArray(new TreePath[paths.size()]));
      }
    }

    public static SelectionInfo create(final AbstractProjectViewPane viewPane) {
      List<Object> selectedElements = Collections.emptyList();
      if (viewPane != null) {
        final TreePath[] selectionPaths = viewPane.getSelectionPaths();
        if (selectionPaths != null) {
          selectedElements = new ArrayList<Object>();
          for (TreePath path : selectionPaths) {
            final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
            final Object userObject = node.getUserObject();
            if (userObject instanceof NodeDescriptor) {
              selectedElements.add(((NodeDescriptor)userObject).getElement());
            }
          }
        }
      }
      return new SelectionInfo(selectedElements.toArray());
    }
  }

  private class MyAutoScrollFromSourceHandler extends AutoScrollFromSourceHandler {
    private Alarm myAlarm = new Alarm(myProject);
    private FileEditorManagerAdapter myEditorManagerListener;

    public MyAutoScrollFromSourceHandler() {
      super(ProjectViewImpl.this.myProject);
    }

    public void install() {
      myEditorManagerListener = new FileEditorManagerAdapter() {
        public void selectionChanged(final FileEditorManagerEvent event) {
          PsiDocumentManager.getInstance(myProject).commitAllDocuments();
          myAlarm.cancelAllRequests();
          myAlarm.addRequest(new Runnable() {
            public void run() {
              if(myProject.isDisposed()) return;
              if (isAutoscrollFromSource(getCurrentViewId())) {
                FileEditor newEditor = event.getNewEditor();
                if (newEditor instanceof TextEditor) {
                  Editor editor = ((TextEditor)newEditor).getEditor();
                  selectElementAtCaretNotLosingFocus(editor);
                }
              }
            }
          }, 400, ModalityState.NON_MMODAL);
        }
      };
      myFileEditorManager.addFileEditorManagerListener(myEditorManagerListener);
    }

    private void selectElementAtCaretNotLosingFocus(final Editor editor) {
      if (IJSwingUtilities.hasFocus(getCurrentProjectViewPane().getComponentToFocus())) return;
      final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
      if (file == null) return;

      final MySelectInContext selectInContext = new MySelectInContext(file, editor);

      final SelectInTarget[] targets = mySelectInManager.getTargets();
      for (SelectInTarget target : targets) {
        if (!ToolWindowId.PROJECT_VIEW.equals(target.getToolWindowId())) continue;
        String compatiblePaneViewId = target.getMinorViewId();
        if (!Comparing.strEqual(compatiblePaneViewId, getCurrentViewId())) continue;

        if (target.canSelect(selectInContext)) {
          target.selectIn(selectInContext, false);
          break;
        }
      }
    }

    public void dispose() {
      if (myEditorManagerListener != null) {
        myFileEditorManager.removeFileEditorManagerListener(myEditorManagerListener);
      }
    }

    protected boolean isAutoScrollMode() {
      return isAutoscrollFromSource(myCurrentViewId);
    }

    protected void setAutoScrollMode(boolean state) {
      setAutoscrollFromSource(state, myCurrentViewId);
      if (state) {
        final Editor editor = myFileEditorManager.getSelectedTextEditor();
        if (editor != null) {
          selectElementAtCaretNotLosingFocus(editor);
        }
      }
    }

    private class MySelectInContext implements SelectInContext {
      private final PsiFile myPsiFile;
      private final Editor myEditor;

      public MySelectInContext(final PsiFile psiFile, Editor editor) {
        myPsiFile = psiFile;
        myEditor = editor;
      }

      @NotNull
      public Project getProject() {
        return myProject;
      }

      private PsiFile getPsiFile() {
        return myPsiFile;
      }

      public FileEditorProvider getFileEditorProvider() {
        if (myPsiFile == null) return null;
        return new FileEditorProvider() {
          public FileEditor openFileEditor() {
            return myFileEditorManager.openFile(myPsiFile.getContainingFile().getVirtualFile(), false)[0];
          }
        };
      }

      private PsiElement getPsiElement() {
        final int offset = myEditor.getCaretModel().getOffset();
        PsiDocumentManager.getInstance(myProject).commitAllDocuments();
        PsiElement e = getPsiFile().findElementAt(offset);
        if (e == null) {
          e = getPsiFile();
        }
        return e;
      }

      @NotNull
      public VirtualFile getVirtualFile() {
        return getPsiFile().getVirtualFile();
      }

      public Object getSelectorInFile() {
        return getPsiElement();
      }
    }
  }

  public boolean isSortByType(String paneId) {
    return getPaneOptionValue(mySortByType, paneId, ourSortByTypeDefaults);
  }
  public void setSortByType(String paneId, final boolean sortByType) {
    setPaneOption(mySortByType, sortByType, paneId, false);
    final AbstractProjectViewPane pane = getProjectViewPaneById(paneId);
    pane.installComparator();
  }

  private class SortByTypeAction extends ToggleAction {
    private SortByTypeAction() {
      super(IdeBundle.message("action.sort.by.type"), IdeBundle.message("action.sort.by.type"), IconLoader.getIcon("/objectBrowser/sortByType.png"));
    }

    public boolean isSelected(AnActionEvent event) {
      return isSortByType(getCurrentViewId());
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      setSortByType(getCurrentViewId(), flag);
    }

    public void update(final AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      final AbstractProjectViewPane pane = getCurrentProjectViewPane();
      presentation.setVisible(pane != null && (PackageViewPane.ID.equals(pane.getId()) || ProjectViewPane.ID.equals(pane.getId())));
    }
  }

  public Collection<String> getPaneIds() {
    return myId2Pane.keySet();
  }
}
