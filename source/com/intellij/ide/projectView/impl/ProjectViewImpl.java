package com.intellij.ide.projectView.impl;

import com.intellij.ide.*;
import com.intellij.ide.FileEditorProvider;
import com.intellij.ide.impl.StructureViewWrapperImpl;
import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.HelpID;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.nodes.*;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.ide.util.EditorHelper;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.projectView.ResourceBundleNode;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.localVcs.LvcsAction;
import com.intellij.openapi.localVcs.impl.LvcsIntegration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementBase;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import com.intellij.ui.AutoScrollFromSourceHandler;
import com.intellij.ui.AutoScrollToSourceHandler;
import com.intellij.ui.ListPopup;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.Icons;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.*;
import java.util.List;

public final class ProjectViewImpl extends ProjectView implements JDOMExternalizable, ProjectComponent {
  private CopyPasteManagerEx.CopyPasteDelegator myCopyPasteDelegator;
  private boolean isInitialized;
  private Project myProject;

  // + options
  private Map<String, Boolean> myFlattenPackages = new HashMap<String, Boolean>();
  private static final boolean ourFlattenPackagesDefaults = false;
  private Map<String, Boolean> myShowMembers = new HashMap<String, Boolean>();
  private static final boolean ourShowMembersDefaults = false;
  private Map<String, Boolean> mySortByType = new HashMap<String, Boolean>();
  private static final boolean ourSortByTypeDefaults = false;
  private Map<String, Boolean> myShowModules = new HashMap<String, Boolean>();
  private static final boolean ourShowModulesDefaults = true;
  private Map<String, Boolean> myShowLibraryContents = new HashMap<String, Boolean>();
  private static final boolean ourShowLibraryContentsDefaults = true;
  private Map<String, Boolean> myHideEmptyPackages = new HashMap<String, Boolean>();
  private static final boolean ourHideEmptyPackagesDefaults = true;
  private Map<String, Boolean> myAbbreviatePackageNames = new HashMap<String, Boolean>();
  private static final boolean ourAbbreviatePackagesDefaults = false;
  private Map<String, Boolean> myAutoscrollToSource = new HashMap<String, Boolean>();
  private static final boolean ourAutoscrollToSourceDefaults = false;
  private Map<String, Boolean> myAutoscrollFromSource = new HashMap<String, Boolean>();
  private static final boolean ourAutoscrollFromSourceDefaults = false;
  private Map<String, Boolean> myShowStructure = new HashMap<String, Boolean>();
  private static final boolean ourShowStructureDefaults = false;

  private String myCurrentViewId;
  private float mySplitterProportion = 0.5f;
  // - options


  private AutoScrollToSourceHandler myAutoScrollToSourceHandler;
  private AutoScrollFromSourceHandler myAutoScrollFromSourceHandler;
  private ActionToolbar myToolBar;
  private TabbedPaneWrapper myTabbedPane;

  private final IdeView myIdeView = new MyIdeView();
  private final MyDeletePSIElementProvider myDeletePSIElementProvider = new MyDeletePSIElementProvider();
  private final ModuleDeleteProvider myDeleteModuleProvider = new ModuleDeleteProvider();

  private JPanel myStructurePanel;
  private MyStructureViewWrapperImpl myStructureViewWrapper;
  private Splitter mySplitter;

  private MyPanel myPanel;
  private final Map<String, AbstractProjectViewPane> myId2Pane = new HashMap<String, AbstractProjectViewPane>();
  private final List<AbstractProjectViewPane> myUninitializedPanes = new ArrayList<AbstractProjectViewPane>();

  private static final String PROJECT_VIEW_DATA_CONSTANT = "com.intellij.ide.projectView.impl.ProjectViewImpl";
  private DefaultActionGroup myActionGroup;
  private final Runnable myTreeChangeListener;
  private final ModuleListener myModulesListener;
  private String mySavedPaneId;
  private static final Icon COMPACT_EMPTY_MIDDLE_PACKAGES_ICON = IconLoader.getIcon("/objectBrowser/compactEmptyPackages.png");
  private static final Icon HIDE_EMPTY_MIDDLE_PACKAGES_ICON = IconLoader.getIcon("/objectBrowser/hideEmptyPackages.png");
  @NonNls
  private static final String ELEMENT_NAVIGATOR = "navigator";
  @NonNls
  private static final String ATTRIBUTE_CURRENTVIEW = "currentView";
  @NonNls
  private static final String ELEMENT_FLATTEN_PACKAGES = "flattenPackages";
  @NonNls
  private static final String ELEMENT_SHOW_MEMBERS = "showMembers";
  @NonNls
  private static final String ELEMENT_SHOW_MODULES = "showModules";
  @NonNls
  private static final String ELEMENT_SHOW_LIBRARY_CONTENTS = "showLibraryContents";
  @NonNls
  private static final String ELEMENT_HIDE_EMPTY_PACKAGES = "hideEmptyPackages";
  @NonNls
  private static final String ELEMENT_ABBREVIATE_PACKAGE_NAMES = "abbreviatePackageNames";
  @NonNls
  private static final String ELEMENT_SHOW_STRUCTURE = "showStructure";
  @NonNls
  private static final String ELEMENT_AUTOSCROLL_TO_SOURCE = "autoscrollToSource";
  @NonNls
  private static final String ELEMENT_AUTOSCROLL_FROM_SOURCE = "autoscrollFromSource";
  @NonNls
  private static final String ELEMENT_SORT_BY_TYPE = "sortByType";
  @NonNls
  private static final String ATTRIBUTE_SPLITTER_PROPORTION = "splitterProportion";

  public ProjectViewImpl(Project project) {
    myProject = project;
    myTreeChangeListener = new Runnable() {
      public void run() {
        updateToolWindowTitle();
      }
    };
    myModulesListener = new ModuleListener() {
      public void moduleRemoved(Project project, Module module) {
        updateAllBuilders();
      }

      public void modulesRenamed(Project project, List<Module> modules) {
        updateAllBuilders();
      }

      public void moduleAdded(Project project, Module module) {
        updateAllBuilders();
      }

      public void beforeModuleRemoved(Project project, Module module) {
      }
    };

    myPanel = new MyPanel();
    myAutoScrollToSourceHandler = new AutoScrollToSourceHandler() {
      protected boolean isAutoScrollMode() {
        return isAutoscrollToSource(myCurrentViewId);
      }

      protected void setAutoScrollMode(boolean state) {
        setAutoscrollToSource(state, myCurrentViewId);
      }
    };

    myAutoScrollFromSourceHandler = new MyAutoScrollFromSourceHandler();
    AbstractProjectViewPane.installAutoScrollFromSourceHandler(myAutoScrollFromSourceHandler);
  }

  public void disposeComponent() {
    myAutoScrollFromSourceHandler.dispose();
  }

  public void initComponent() { }

  public void projectClosed() {
    ToolWindowManager.getInstance(myProject).unregisterToolWindow(ToolWindowId.PROJECT_VIEW);
    ModuleManager.getInstance(myProject).removeModuleListener(myModulesListener);
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
    if (myCurrentViewId == null || myCurrentViewId.equals(pane.getId())) {
      changeView(pane.getId());
    }
    selectSavedPane();
  }

  private void selectSavedPane() {
    AbstractProjectViewPane pane = getProjectViewPaneById(mySavedPaneId);
    if (pane != null) {
      changeView(mySavedPaneId);
    }
  }

  public synchronized void removeProjectPane(AbstractProjectViewPane pane) {
    //assume we are completely initialized here
    String idToRemove = pane.getId();
    if (myId2Pane.remove(idToRemove) == null) return;
    pane.removeTreeChangeListener();
    final int i = myTabbedPane.indexOfComponent(pane.getComponent());
    myTabbedPane.removeTabAt(i);
    if (idToRemove.equals(myCurrentViewId)) {
      final String[] paneIds = myId2Pane.keySet().toArray(ArrayUtil.EMPTY_STRING_ARRAY);
      if (paneIds.length == 0) {
        myCurrentViewId = null;
      }
      else {
        myCurrentViewId = paneIds[0];
      }
    }
  }

  private synchronized void doAddUninitializedPanes() {
    for (int i = 0; i < myUninitializedPanes.size(); i++) {
      AbstractProjectViewPane pane = myUninitializedPanes.get(i);
      doAddPane(pane);
    }
    createToolbarActions();
    myUninitializedPanes.clear();
    selectSavedPane();
  }

  private void doAddPane(AbstractProjectViewPane newPane) {
    int componentIndexToInsertBefore = -1;
    final List<AbstractProjectViewPane> initializedPanes = new ArrayList<AbstractProjectViewPane>(myId2Pane.values());
    for (int i = 0; i < myTabbedPane.getTabCount(); i++) {
      final JComponent component = myTabbedPane.getComponentAt(i);
      AbstractProjectViewPane pane = null;
      for (int j = 0; j < initializedPanes.size(); j++) {
        AbstractProjectViewPane nextPane = initializedPanes.get(j);
        if (nextPane.getComponent() == component) {
          pane = nextPane;
          break;
        }
      }
      if (pane.getWeight() > newPane.getWeight()) {
        componentIndexToInsertBefore = i;
        break;
      }
    }
    myId2Pane.put(newPane.getId(), newPane);
    if (componentIndexToInsertBefore == -1) {
      myTabbedPane.addTab(newPane.getTitle(), newPane.getIcon(), newPane.getComponent(), null);
    }
    else {
      myTabbedPane.insertTab(newPane.getTitle(), newPane.getIcon(), newPane.getComponent(), null, componentIndexToInsertBefore);
    }
    newPane.setTreeChangeListener(myTreeChangeListener);
    newPane.installAutoScrollToSourceHandler(myAutoScrollToSourceHandler);
  }

  private void setupImpl() {
    myTabbedPane = new TabbedPaneWrapper();

    mySplitter = new Splitter(true);
    mySplitter.setHonorComponentsMinimumSize(true);
    mySplitter.setFirstComponent(myTabbedPane.getComponent());
    myStructurePanel = new JPanel(new BorderLayout());
    myStructureViewWrapper = new MyStructureViewWrapperImpl();
    myStructureViewWrapper.setFileEditor(null);
    myStructurePanel.add(myStructureViewWrapper.getComponent());
    mySplitter.setSecondComponent(myStructurePanel);
    myPanel.add(mySplitter, BorderLayout.CENTER);

    myActionGroup = new DefaultActionGroup();
    createToolbarActions();

    myToolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.PROJECT_VIEW_TOOLBAR, myActionGroup, true);
    JComponent toolbarComponent = myToolBar.getComponent();
    myPanel.add(toolbarComponent, BorderLayout.NORTH);

    mySplitter.setProportion(mySplitterProportion);
    myStructurePanel.setVisible(isShowStructure());

    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    ToolWindow toolWindow = toolWindowManager.registerToolWindow(ToolWindowId.PROJECT_VIEW, getComponent(), ToolWindowAnchor.LEFT);
    toolWindow.setIcon(IconLoader.getIcon("/general/toolWindowProject.png"));

    //todo                                                                   tree ?
    myCopyPasteDelegator = new CopyPasteManagerEx.CopyPasteDelegator(myProject, myPanel) {
      protected PsiElement[] getSelectedElements() {
        final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
        return viewPane != null ? viewPane.getSelectedPSIElements() : null;
      }
    };

    // important - should register listener in the end in order to prevent its work during setup
    myTabbedPane.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        final Collection<AbstractProjectViewPane> panes = myId2Pane.values();
        for (Iterator<AbstractProjectViewPane> iterator = panes.iterator(); iterator.hasNext();) {
          AbstractProjectViewPane pane = iterator.next();
          if (pane.getComponent() == myTabbedPane.getSelectedComponent()) {
            changeView(pane.getId());
            break;
          }
        }
      }
    });
    ModuleManager.getInstance(myProject).addModuleListener(myModulesListener);
    isInitialized = true;
    doAddUninitializedPanes();
    changeView(getCurrentViewId());
  }

  private void createToolbarActions() {
    myActionGroup.removeAll();
    myActionGroup.add(new PaneOptionAction(myFlattenPackages, IdeBundle.message("action.flatten.packages"), 
                                           IdeBundle.message("action.flatten.packages"), Icons.FLATTEN_PACKAGES_ICON, ourFlattenPackagesDefaults) {
      public void setSelected(AnActionEvent event, boolean flag) {
        final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
        final SelectionInfo selectionInfo = SelectionInfo.create(viewPane);

        super.setSelected(event, flag);

        selectionInfo.apply(viewPane);
      }
    });

    class FlattenPackagesDependableAction extends PaneOptionAction {
      public FlattenPackagesDependableAction(Map<String, Boolean> optionsMap, final String text, final String description, final Icon icon, boolean optionDefaultValue) {
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
                                                          IdeBundle.message("action.abbreviate.qualified.package.names"), IdeBundle.message("action.abbreviate.qualified.package.names"), IconLoader.getIcon("/objectBrowser/abbreviatePackageNames.png"), ourAbbreviatePackagesDefaults) {
      public boolean isSelected(AnActionEvent event) {
        return super.isSelected(event) && isAbbreviatePackageNames(myCurrentViewId);
      }
    });
    myActionGroup.add(new PaneOptionAction(myShowMembers, IdeBundle.message("action.show.members"), IdeBundle.message("action.show.hide.members"), IconLoader.getIcon("/objectBrowser/showMembers.png"), ourShowMembersDefaults));
    myActionGroup.add(myAutoScrollToSourceHandler.createToggleAction());
    myActionGroup.add(myAutoScrollFromSourceHandler.createToggleAction());
    myActionGroup.add(new ShowStructureAction());
    myActionGroup.add(new SortByTypeAction());

    final List<AbstractProjectViewPane> panes = new ArrayList<AbstractProjectViewPane>(myId2Pane.values());
    for (int i = 0; i < panes.size(); i++) {
      AbstractProjectViewPane projectViewPane = panes.get(i);
      projectViewPane.addToolbarActions(myActionGroup);
    }
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
    myProject = null;
    myStructureViewWrapper.dispose();
    myStructureViewWrapper = null;
  }
  public void rebuildStructureViewPane() {
    if (myStructureViewWrapper != null) {
      myStructureViewWrapper.rebuild();
    }
  }

  private JComponent getComponent() {
    return myPanel;
  }

  void updateAllBuilders() {
    final Collection<AbstractProjectViewPane> panes = myId2Pane.values();
    for (AbstractProjectViewPane projectViewPane : panes) {
      projectViewPane.updateFromRoot(false);
    }
  }

  public String getCurrentViewId() {
    return myCurrentViewId;
  }

  private void updateToolWindowTitle() {
    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.PROJECT_VIEW);
    if (toolWindow == null) return;

    final PsiElement element = (PsiElement)myPanel.getData(DataConstants.PSI_ELEMENT);
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

    myStructurePanel.setVisible(toShow);
    setShowStructure(toShow, myCurrentViewId);

    if (hadFocus) {
      final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
      if (viewPane != null) {
        viewPane.getComponent().requestFocus();
      }
    }

    if (toShow) {
      VirtualFile[] files = FileEditorManager.getInstance(myProject).getSelectedFiles();
      if (files.length != 0) {
        myStructureViewWrapper.setFileEditor(FileEditorManager.getInstance(myProject).getSelectedEditor(files[0]));
      } else {
        myStructureViewWrapper.setFileEditor(null);
      }
    }
  }

  public void changeView() {
    final class ViewWrapper {
      AbstractProjectViewPane myViewPane;

      ViewWrapper(AbstractProjectViewPane viewPane) {
        myViewPane = viewPane;
      }

      public String toString() {
        return myViewPane.getTitle();
      }
    }

    final List<ViewWrapper> views = new ArrayList<ViewWrapper>();
    final Collection<AbstractProjectViewPane> panes = myId2Pane.values();
    ViewWrapper viewToSelect = null;
    for (final AbstractProjectViewPane pane : panes) {
      final ViewWrapper wrapper = new ViewWrapper(pane);
      if (viewToSelect == null) {
        if (!pane.getId().equals(getCurrentViewId())) {
          viewToSelect = wrapper;
        }
      }
      views.add(wrapper);
    }

    final JList list = new JList(views.toArray(new Object[views.size()]));
    Runnable runnable = new Runnable() {
      public void run() {
        if (list.getSelectedIndex() < 0) return;
        ViewWrapper viewWrapper = (ViewWrapper)list.getSelectedValue();
        changeView(viewWrapper.myViewPane.getId());
      }
    };

    if (viewToSelect != null) {
      list.setSelectedValue(viewToSelect, true);
    }
    Dimension size = getComponent().getSize();
    Point loc = getComponent().getLocationOnScreen();
    ListPopup popup = new ListPopup(" " + IdeBundle.message("title.popup.views") + " ", list, runnable, myProject);
    popup.show(loc.x + size.width / 2 - popup.getSize().width / 2, loc.y + size.height / 2 - popup.getSize().height / 2);
  }

  public void setCurrentViewId(String viewId) {
    myCurrentViewId = viewId;
  }

  public void changeView(String viewId) {
    if (viewId != null) {
      setCurrentViewId(viewId);
      final AbstractProjectViewPane pane = getProjectViewPaneById(viewId);
      if (pane != null) {
        final JComponent component = pane.getComponent();
        myTabbedPane.setSelectedComponent(component);
        pane.getComponentToFocus().requestFocus();
        updateToolWindowTitle();
        showOrHideStructureView(isShowStructure());
      }
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
          final VirtualFile virtualFile = ((PsiDirectory)element).getVirtualFile();
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

  public String getComponentName() {
    return "ProjectView";
  }

  private final class MyStructureViewWrapperImpl extends StructureViewWrapperImpl {
    MyStructureViewWrapperImpl() {
      super(myProject);
    }

    protected boolean isStructureViewShowing() {
      return myStructurePanel.isVisible();
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
      if (DataConstantsEx.PSI_ELEMENT_ARRAY.equals(dataId)) {
        if (currentProjectViewPane == null) {
          return null;
        }
        final List<PsiElement> elements = new ArrayList<PsiElement>(Arrays.asList(currentProjectViewPane.getSelectedPSIElements()));
        for (Iterator<PsiElement> it = elements.iterator(); it.hasNext();) {
          PsiElement psiElement = it.next();
          if (!psiElement.isValid()) {
            it.remove();
          }
        }
        return elements.isEmpty() ? null : elements.toArray(new PsiElement[elements.size()]);
      }
      if (DataConstantsEx.TARGET_PSI_ELEMENT.equals(dataId)) {
        return null;
      }
      if (DataConstantsEx.CUT_PROVIDER.equals(dataId)) {
        return myCopyPasteDelegator.getCutProvider();
      }
      if (DataConstantsEx.COPY_PROVIDER.equals(dataId)) {
        return myCopyPasteDelegator.getCopyProvider();
      }
      if (DataConstantsEx.PASTE_PROVIDER.equals(dataId)) {
        return myCopyPasteDelegator.getPasteProvider();
      }
      if (DataConstantsEx.IDE_VIEW.equals(dataId)) {
        return myIdeView;
      }
      if (DataConstantsEx.DELETE_ELEMENT_PROVIDER.equals(dataId)) {
        return getSelectedNodeElement() instanceof Module ? (DeleteProvider)myDeleteModuleProvider : myDeletePSIElementProvider;
      }
      if (DataConstantsEx.HELP_ID.equals(dataId)) {
        return HelpID.PROJECT_VIEWS;
      }
      if (PROJECT_VIEW_DATA_CONSTANT.equals(dataId)) {
        return ProjectViewImpl.this;
      }
      if (DataConstantsEx.PROJECT_CONTEXT.equals(dataId)) {
        Object selected = getSelectedNodeElement();
        return selected instanceof Project ? selected : null;
      }
      if (DataConstantsEx.MODULE_CONTEXT.equals(dataId)) {
        Object selected = getSelectedNodeElement();
        return selected instanceof Module ? selected : null;
      }
      if (DataConstantsEx.MODULE_CONTEXT_ARRAY.equals(dataId)) {
        return getSelectedModules();
      }
      if (DataConstantsEx.MODULE_GROUP_ARRAY.equals(dataId)){
        final List<ModuleGroup> selectedElements = getSelectedElements(ModuleGroup.class);
        return selectedElements.isEmpty() ? null : selectedElements.toArray(new ModuleGroup[selectedElements.size()]);
      }
      if (DataConstantsEx.GUI_DESIGNER_FORM_ARRAY.equals(dataId)){
        final List<Form> selectedElements = getSelectedElements(Form.class);
        return selectedElements.isEmpty() ? null : selectedElements.toArray(new Form[selectedElements.size()]);
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

    private Module[] getSelectedModules() {
      final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
      if (viewPane == null) return null;
      final Object[] elements = viewPane.getSelectedElements();
      ArrayList<Module> result = new ArrayList<Module>();
      for (Object element : elements) {
        if (element instanceof Module) {
          result.add((Module)element);
        }
        else if (element instanceof ModuleGroup) {
          Module[] modules = ((ModuleGroup)element).modulesInGroup(myProject, true);
          result.addAll(Arrays.asList(modules));
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
      ProjectViewImpl.this.selectPsiElement(element, true);
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
  }

  public void selectPsiElement(PsiElement element, boolean requestFocus) {
    if (element == null) return;
    if (element instanceof PsiDirectory) {
       select(element, ((PsiDirectory)element).getVirtualFile() , requestFocus);
    }
    else {
      final PsiFile containingFile = element.getContainingFile();
      if (containingFile != null) {
        select(element, containingFile.getVirtualFile(), requestFocus);
      }
    }

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

  public void readExternal(Element parentNode) {
    Element navigatorElement = parentNode.getChild(ELEMENT_NAVIGATOR);
    if (navigatorElement != null) {
      mySavedPaneId = navigatorElement.getAttributeValue(ATTRIBUTE_CURRENTVIEW);
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

      try {
        mySplitterProportion = Float.parseFloat(navigatorElement.getAttributeValue(ATTRIBUTE_SPLITTER_PROPORTION));
      }
      catch (NumberFormatException e) {
        mySplitterProportion = 0.5f;
      }
    }
  }

  public void writeExternal(Element parentNode) {
    Element navigatorElement = new Element(ELEMENT_NAVIGATOR);
    if (getCurrentViewId() != null) {
      navigatorElement.setAttribute(ATTRIBUTE_CURRENTVIEW, getCurrentViewId());
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

    navigatorElement.setAttribute(ATTRIBUTE_SPLITTER_PROPORTION, Float.toString(getSplitterProportion()));
    parentNode.addContent(navigatorElement);
  }

  private float getSplitterProportion() {
    return mySplitter != null ? mySplitter.getProportion() : mySplitterProportion;
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
      final AbstractProjectViewPane pane = myId2Pane.get(paneId);
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
      final BaseProjectTreeBuilder treeBuilder = viewPane.myTreeBuilder;
      final ProjectViewTree tree = viewPane.myTree;
      final DefaultTreeModel treeModel = (DefaultTreeModel)tree.getModel();
      final List<TreePath> paths = new ArrayList<TreePath>(myElements.length);
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
      if (paths.size() > 0) {
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
            final NodeDescriptor descriptor = (NodeDescriptor)node.getUserObject();
            selectedElements.add(descriptor.getElement());
          }
        }
      }
      return new SelectionInfo(selectedElements.toArray());
    }
  }

  public void selectModuleGroup(ModuleGroup moduleGroup, boolean b) {
  }



  private class MyAutoScrollFromSourceHandler extends AutoScrollFromSourceHandler {
    private Alarm myAlarm = new Alarm();
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
      FileEditorManager.getInstance(myProject).addFileEditorManagerListener(myEditorManagerListener);
    }

    private void selectElementAtCaretNotLosingFocus(final Editor editor) {
      if (IJSwingUtilities.hasFocus(getCurrentProjectViewPane().getComponentToFocus())) return;
      final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
      if (file == null) return;

      final MySelectInContext selectInContext = new MySelectInContext(file, editor);

      final SelectInTarget[] targets = SelectInManager.getInstance(myProject).getTargets();
      for (SelectInTarget target : targets) {
        if (!ToolWindowId.PROJECT_VIEW.equals(target.getToolWindowId())) continue;
        String compatiblePaneViewId = target.getMinorViewId();
        if (!Comparing.strEqual(compatiblePaneViewId, getCurrentViewId())) continue;

        if (!target.canSelect(selectInContext)) continue;
        target.selectIn(selectInContext, false);
        break;
      }
    }

    public void dispose() {
      if (myEditorManagerListener != null) {
        FileEditorManager.getInstance(myProject).removeFileEditorManagerListener(myEditorManagerListener);
      }
    }

    protected boolean isAutoScrollMode() {
      return isAutoscrollFromSource(myCurrentViewId);
    }

    protected void setAutoScrollMode(boolean state) {
      setAutoscrollFromSource(state, myCurrentViewId);
      if (state) {
        final Editor editor = FileEditorManager.getInstance(myProject).getSelectedTextEditor();
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
            return FileEditorManager.getInstance(getProject()).openFile(myPsiFile.getContainingFile().getVirtualFile(), false)[0];
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
    setComparator(getProjectViewPaneById(paneId));
  }

  public abstract static class GroupByTypeComparator implements Comparator<NodeDescriptor> {
    public int compare(NodeDescriptor o1, NodeDescriptor o2) {
      if (!isSortByType() && o1 instanceof ResourceBundleNode) {
        final Collection<AbstractTreeNode> children = ((ResourceBundleNode)o1).getChildren();
        if (!children.isEmpty()) {
          o1 = children.iterator().next();
          o1.update();
        }
      }
      if (!isSortByType() && o2 instanceof ResourceBundleNode) {
        final Collection<AbstractTreeNode> children = ((ResourceBundleNode)o2).getChildren();
        if (!children.isEmpty()) {
          o2 = children.iterator().next();
          o2.update();
        }
      }
      if (o1 instanceof PsiDirectoryNode != o2 instanceof PsiDirectoryNode) {
        return o1 instanceof PsiDirectoryNode ? -1 : 1;
      }
      if (o1 instanceof PackageElementNode != o2 instanceof PackageElementNode) {
        return o1 instanceof PackageElementNode ? -1 : 1;
      }
      if (isSortByType() && o1 instanceof ClassTreeNode != o2 instanceof ClassTreeNode) {
        return o1 instanceof ClassTreeNode ? -1 : 1;
      }
      if (isSortByType() && o1 instanceof ClassTreeNode && o2 instanceof ClassTreeNode) {
        final PsiClass aClass1 = ((ClassTreeNode)o1).getValue();
        final PsiClass aClass2 = ((ClassTreeNode)o2).getValue();
        int pos1 = getClassPosition(aClass1);
        int pos2 = getClassPosition(aClass2);
        final int result = pos1 - pos2;
        if (result != 0) return result;
      }
      else if (isSortByType()
               && o1 instanceof AbstractTreeNode
               && o2 instanceof AbstractTreeNode
               && (o1 instanceof PsiFileNode || ((AbstractTreeNode)o1).getValue() instanceof ResourceBundle)
               && (o2 instanceof PsiFileNode || ((AbstractTreeNode)o2).getValue() instanceof ResourceBundle)) {
        String type1 = o1 instanceof PsiFileNode ? extension(((PsiFileNode)o1).getValue()) : StdFileTypes.PROPERTIES.getDefaultExtension();
        String type2 = o2 instanceof PsiFileNode ? extension(((PsiFileNode)o2).getValue()) : StdFileTypes.PROPERTIES.getDefaultExtension();
        if (type1 != null && type2 != null) {
          int result = type1.compareTo(type2);
          if (result != 0) return result;
        }
      }
      if (isAbbreviatePackageNames()){
        if (o1 instanceof PsiDirectoryNode) {
          final PsiDirectory aDirectory1 = ((PsiDirectoryNode)o1).getValue();
          final PsiDirectory aDirectory2 = ((PsiDirectoryNode)o2).getValue();
          if (aDirectory1 != null &&
              aDirectory2 != null) {
            final PsiPackage aPackage1 = aDirectory1.getPackage();
            final PsiPackage aPackage2 = aDirectory2.getPackage();
            if (aPackage1 != null && aPackage2 != null){
              return aPackage1.getQualifiedName().compareToIgnoreCase(aPackage2.getQualifiedName());
            }
          }
        } else if (o1 instanceof PackageElementNode) {
          final PackageElement packageElement1 = ((PackageElementNode)o1).getValue();
          final PackageElement packageElement2 = ((PackageElementNode)o2).getValue();
          if (packageElement1 != null &&
              packageElement2 != null){
            final PsiPackage aPackage1 = packageElement1.getPackage();
            final PsiPackage aPackage2 = packageElement2.getPackage();
            if (aPackage1 != null && aPackage2 != null) {
              return aPackage1.getQualifiedName().compareToIgnoreCase(aPackage2.getQualifiedName());
            }
          }
        }
      }
      return AlphaComparator.INSTANCE.compare(o1, o2);
    }

    protected abstract boolean isSortByType();

    protected boolean isAbbreviatePackageNames(){
      return false;
    }

    private static int getClassPosition(final PsiClass aClass) {
      if (aClass == null) {
        return 0;
      }
      int pos = ElementBase.getClassKind(aClass);
      //abstract class before concrete
      if (pos == ElementBase.CLASS_KIND_CLASS || pos == ElementBase.CLASS_KIND_EXCEPTION) {
        boolean isAbstract = aClass.hasModifierProperty(PsiModifier.ABSTRACT) && !aClass.isInterface();
        if (isAbstract) {
          pos --;
        }
      }
      return pos;
    }
    private static String extension(final PsiFile file) {
      return file == null || file.getVirtualFile() == null ? null : file.getVirtualFile().getFileType().getDefaultExtension();
    }
  }

  void setComparator(final AbstractProjectViewPane pane) {
    pane.getTreeBuilder().setNodeDescriptorComparator(new GroupByTypeComparator() {
      protected boolean isSortByType() {
        return ProjectViewImpl.this.isSortByType(pane.getId());
      }

      protected boolean isAbbreviatePackageNames(){
        return ProjectViewImpl.this.isAbbreviatePackageNames(pane.getId());
      }

    });
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
      final ProjectViewImpl projectView = (ProjectViewImpl)ProjectView.getInstance(myProject);
      final AbstractProjectViewPane pane = projectView.getCurrentProjectViewPane();
      presentation.setVisible(pane != null && (PackageViewPane.ID.equals(pane.getId()) || ProjectViewPane.ID.equals(pane.getId())));
    }
  }
}
