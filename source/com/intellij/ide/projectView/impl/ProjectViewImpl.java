package com.intellij.ide.projectView.impl;

import com.intellij.ide.*;
import com.intellij.ide.impl.StructureViewWrapper;
import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.HelpID;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.nodes.ProjectViewModuleNode;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.ide.util.EditorHelper;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
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
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import com.intellij.ui.AutoScrollFromSourceHandler;
import com.intellij.ui.AutoScrollToSourceHandler;
import com.intellij.ui.ListPopup;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IJSwingUtilities;
import org.jdom.Attribute;
import org.jdom.Element;

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
  private Map<String, Boolean> myShowModules = new HashMap<String, Boolean>();
  private static final boolean ourShowModulesDefaults = true;
  private Map<String, Boolean> myShowLibraryContents = new HashMap<String, Boolean>();
  private static final boolean ourShowLibraryContentsDefaults = false;
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
  private MyStructureViewWrapper myStructureViewWrapper;
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
    myAutoScrollToSourceHandler = new AutoScrollToSourceHandler(myProject) {
      protected boolean isAutoScrollMode() {
        return isAutoscrollToSource(myCurrentViewId);
      }

      protected void setAutoScrollMode(boolean state) {
        setAutoscrollToSource(state, myCurrentViewId);
      }
    };
    myAutoScrollFromSourceHandler = new AutoScrollFromSourceHandler(myProject) {
      private Alarm myAlarm = new Alarm();
      private FileEditorManagerAdapter myEditorManagerListener;

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
            }, 400);
          }
        };
        FileEditorManager.getInstance(myProject).addFileEditorManagerListener(myEditorManagerListener);
      }

      private void selectElementAtCaretNotLosingFocus(Editor editor) {
        if (IJSwingUtilities.hasFocus(getCurrentProjectViewPane().getComponentToFocus())) return;
        PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
        if (file == null) return;
        final SelectInTarget[] targets = SelectInManager.getInstance(myProject).getTargets();
        for (int i = 0; i < targets.length; i++) {
          SelectInTarget target = targets[i];
          if (!ToolWindowId.PROJECT_VIEW.equals(target.getToolWindowId())) continue;
          String compatiblePaneViewId = target.getMinorViewId();
          if (!Comparing.strEqual(compatiblePaneViewId, getCurrentViewId())) continue;
          if (!target.canSelect(file)) continue;
          final int offset = editor.getCaretModel().getOffset();
          PsiDocumentManager.getInstance(myProject).commitAllDocuments();
          PsiElement e = file.findElementAt(offset);
          if (e == null) {
            e = file;
          }
          target.select(e, false);
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
    };
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
      myCurrentViewId = myId2Pane.keySet().toArray(ArrayUtil.EMPTY_STRING_ARRAY)[0];
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
    newPane.installAutoScrollFromSourceHandler(myAutoScrollFromSourceHandler);
  }

  private void setupImpl() {
    myTabbedPane = new TabbedPaneWrapper();
    myTabbedPane.installKeyboardNavigation();

    mySplitter = new Splitter(true);
    mySplitter.setHonorComponentsMinimumSize(true);
    mySplitter.setFirstComponent(myTabbedPane.getComponent());
    myStructurePanel = new JPanel(new BorderLayout());
    myStructureViewWrapper = new MyStructureViewWrapper();
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
    myActionGroup.add(new PaneOptionAction(myFlattenPackages, "Flatten Packages", "Flatten Packages", IconLoader.getIcon("/objectBrowser/flattenPackages.png"), ourFlattenPackagesDefaults) {
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
    myActionGroup.add(new FlattenPackagesDependableAction(myAbbreviatePackageNames, "Abbreviate Qualified Package Names", "Abbreviate Qualified Package Names", IconLoader.getIcon("/objectBrowser/abbreviatePackageNames.png"), ourAbbreviatePackagesDefaults) {
      public boolean isSelected(AnActionEvent event) {
        return super.isSelected(event) && isAbbreviatePackageNames(myCurrentViewId);
      }
    });
    myActionGroup.add(new PaneOptionAction(myShowMembers, "Show Members", "Show/Hide Members", IconLoader.getIcon("/objectBrowser/showMembers.png"), ourShowMembersDefaults));
    myActionGroup.add(myAutoScrollToSourceHandler.createToggleAction());
    myActionGroup.add(myAutoScrollFromSourceHandler.createToggleAction());
    myActionGroup.add(new ShowStructureAction());
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

  private JComponent getComponent() {
    return myPanel;
  }

  void updateAllBuilders() {
    final Collection<AbstractProjectViewPane> panes = myId2Pane.values();
    for (Iterator<AbstractProjectViewPane> iterator = panes.iterator(); iterator.hasNext();) {
      AbstractProjectViewPane projectViewPane = iterator.next();
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
    if (userObject instanceof NodeDescriptor) {
      NodeDescriptor descriptor = (NodeDescriptor)userObject;
      Object element = descriptor.getElement();
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
      super("Show Structure", "Show structure view", IconLoader.getIcon("/objectBrowser/showStructure.png"));
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
      PsiFile psiFile = files.length != 0 ? PsiManager.getInstance(myProject).findFile(files[0]) : null;
      myStructureViewWrapper.setFileEditor(null);
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
    for (Iterator<AbstractProjectViewPane> iterator = panes.iterator(); iterator.hasNext();) {
      final AbstractProjectViewPane pane = iterator.next();
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
    ListPopup popup = new ListPopup(" Views ", list, runnable, myProject);
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
      for (Iterator<PsiElement> iterator = allElements.iterator(); iterator.hasNext();) {
        PsiElement psiElement = iterator.next();
        if (psiElement != null &&  psiElement.isValid()) validElements.add(psiElement);
      }
      final PsiElement[] elements = validElements.toArray(new PsiElement[validElements.size()]);
 
      LvcsAction action = LvcsIntegration.checkinFilesBeforeRefactoring(myProject, "Deleting");
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

  private final class MyStructureViewWrapper extends StructureViewWrapper {
    MyStructureViewWrapper() {
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
        return elements.toArray(new PsiElement[elements.size()]);
      }
      if (DataConstantsEx.TARGET_PSI_ELEMENT.equals(dataId)) {
        /*
        DefaultMutableTreeNode node = getCurrentProjectViewPane().getSelectedNode();
        if (node == null) {
          return null;
        }
        if (node.getUserObject() instanceof DirectoryNodeDescriptor) {
          final PsiDirectory dir = ((DirectoryNodeDescriptor)node.getUserObject()).getDirectory();
          return dir.getParentDirectory();
        }
        node = (DefaultMutableTreeNode)node.getParent();
        if (node == null) {
          return null;
        }
        Object userObject = node.getUserObject();
        if (!(userObject instanceof NodeDescriptor)) {
          return null;
        }
        Object element = ((NodeDescriptor)userObject).getElement();
        if (!(element instanceof PsiElement)) {
          return null;
        }
        PsiElement psiElement = (PsiElement)element;
        return psiElement.isValid() ? psiElement : null;
        */
        // [dsl] in Project View we do not have any specific target psi element
        // we only guess
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

      return null;
    }

    private Module[] getSelectedModules() {
      final AbstractProjectViewPane viewPane = getCurrentProjectViewPane();
      if (viewPane == null) return null;
      final Object[] elements = viewPane.getSelectedElements();
      ArrayList<Module> result = new ArrayList<Module>();
      for (int i = 0; i < elements.length; i++) {
        Object element = elements[i];
        if (element instanceof Module) {
          result.add((Module)element);
        }
        else if (element instanceof ModuleGroup) {
          Module[] modules = ((ModuleGroup)element).modulesInGroup(myProject);
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

  private final class MyIdeView implements IdeView {
    public void selectElement(PsiElement element) {
      ProjectViewImpl.this.selectPsiElement(element, true);
      if (element instanceof PsiElement) {
        final PsiElement psiElement = (PsiElement)element;
        final boolean isDirectory = psiElement instanceof PsiDirectory;
        if (!isDirectory) {
          Editor editor = EditorHelper.openInEditor(psiElement);
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
            userObject instanceof ProjectViewModuleNode) {
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
      else if (userObject instanceof ProjectViewModuleNode) {
        final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(((ProjectViewModuleNode)userObject).getValue());
        final VirtualFile[] sourceRoots = moduleRootManager.getSourceRoots();
        List<PsiDirectory> dirs = new ArrayList<PsiDirectory>(sourceRoots.length);
        final PsiManager psiManager = PsiManager.getInstance(myProject);
        for (int idx = 0; idx < sourceRoots.length; idx++) {
          final VirtualFile sourceRoot = sourceRoots[idx];
          final PsiDirectory directory = psiManager.findDirectory(sourceRoot);
          if (directory != null) {
            dirs.add(directory);
          }
        }
        return dirs.toArray(new PsiDirectory[dirs.size()]);
      }

      return PsiDirectory.EMPTY_ARRAY;
    }
  }

  public void selectPsiElement(PsiElement element, boolean requestFocus) {
    if (element == null) return;
    if (element instanceof PsiDirectory) {
       select(element, ((PsiDirectory)element).getVirtualFile() , requestFocus);
    } else {
      select(element, element.getContainingFile().getVirtualFile(), requestFocus);
    }

  }


  private void readOption(Element node, Map<String, Boolean> options) {
    if (node == null) return;
    List attributes = node.getAttributes();
    for (Iterator iterator1 = attributes.iterator(); iterator1.hasNext();) {
      Attribute attribute = (Attribute)iterator1.next();
      options.put(attribute.getName(), "true".equals(attribute.getValue()) ? Boolean.TRUE : Boolean.FALSE);
    }
  }

  private void writeOption(Element parentNode, Map<String, Boolean> optionsForPanes, String optionName) {
    Element e = new Element(optionName);
    for (Iterator<Map.Entry<String, Boolean>> iterator = optionsForPanes.entrySet().iterator(); iterator.hasNext();) {
      Map.Entry<String, Boolean> entry = iterator.next();
      e.setAttribute(entry.getKey(), entry.getValue().booleanValue() ? "true" : "false");
    }

    parentNode.addContent(e);
  }

  public void readExternal(Element parentNode) {
    Element navigatorElement = parentNode.getChild("navigator");
    if (navigatorElement != null) {
      mySavedPaneId = navigatorElement.getAttributeValue("currentView");
      readOption(navigatorElement.getChild("flattenPackages"), myFlattenPackages);
      readOption(navigatorElement.getChild("showMembers"), myShowMembers);
      readOption(navigatorElement.getChild("showModules"), myShowModules);
      readOption(navigatorElement.getChild("showLibraryContents"), myShowLibraryContents);
      readOption(navigatorElement.getChild("hideEmptyPackages"), myHideEmptyPackages);
      readOption(navigatorElement.getChild("abbreviatePackageNames"), myAbbreviatePackageNames);
      readOption(navigatorElement.getChild("showStructure"), myShowStructure);
      readOption(navigatorElement.getChild("autoscrollToSource"), myAutoscrollToSource);
      readOption(navigatorElement.getChild("autoscrollFromSource"), myAutoscrollFromSource);

      try {
        mySplitterProportion = Float.parseFloat(navigatorElement.getAttributeValue("splitterProportion"));
      }
      catch (NumberFormatException e) {
        mySplitterProportion = 0.5f;
      }
    }
  }

  public void writeExternal(Element parentNode) {
    Element navigatorElement = new Element("navigator");
    if (getCurrentViewId() != null) {
      navigatorElement.setAttribute("currentView", getCurrentViewId());
    }
    writeOption(navigatorElement, myFlattenPackages, "flattenPackages");
    writeOption(navigatorElement, myShowMembers, "showMembers");
    writeOption(navigatorElement, myShowModules, "showModules");
    writeOption(navigatorElement, myShowLibraryContents, "showLibraryContents");
    writeOption(navigatorElement, myHideEmptyPackages, "hideEmptyPackages");
    writeOption(navigatorElement, myAbbreviatePackageNames, "abbreviatePackageNames");
    writeOption(navigatorElement, myShowStructure, "showStructure");
    writeOption(navigatorElement, myAutoscrollToSource, "autoscrollToSource");
    writeOption(navigatorElement, myAutoscrollFromSource, "autoscrollFromSource");

    navigatorElement.setAttribute("splitterProportion", Float.toString(getSplitterProportion()));
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

  private boolean getPaneOptionValue(Map<String, Boolean> optionsMap, String paneId, boolean defaultValue) {
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
        presentation.setText("Hide Empty Middle Packages");
        presentation.setDescription("Show/Hide Empty Middle Packages");
        presentation.setIcon(HIDE_EMPTY_MIDDLE_PACKAGES_ICON);
      }
      else {
        presentation.setText("Compact Empty Middle Packages");
        presentation.setDescription("Show/Compact Empty Middle Packages");
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
      for (int idx = 0; idx < myElements.length; idx++) {
        final Object element = myElements[idx];
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
      List selectedElements = Collections.EMPTY_LIST;
      if (viewPane != null) {
        final TreePath[] selectionPaths = viewPane.getSelectionPaths();
        if (selectionPaths != null) {
          selectedElements = new ArrayList();
          for (int idx = 0; idx < selectionPaths.length; idx++) {
            TreePath path = selectionPaths[idx];
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
}
