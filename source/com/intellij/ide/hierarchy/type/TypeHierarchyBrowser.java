package com.intellij.ide.hierarchy.type;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.ide.OccurenceNavigatorSupport;
import com.intellij.ide.hierarchy.*;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.ui.AutoScrollToSourceHandler;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.TreeToolTipHandler;
import com.intellij.util.Alarm;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;

public final class TypeHierarchyBrowser extends HierarchyBrowserBase implements DataProvider, OccurenceNavigator, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.hierarchy.type.TypeHierarchyBrowser");

  @NonNls private static final String HELP_ID = "reference.toolWindows.hierarchy";
  @NonNls
  static final String TYPE_HIERARCHY_BROWSER_ID = "TYPE_HIERARCHY_BROWSER_ID";

  private final Project myProject;
  private final Hashtable<String, HierarchyTreeBuilder> myBuilders = new Hashtable<String, HierarchyTreeBuilder>();
  private final Hashtable<String, JTree> myTrees = new Hashtable<String, JTree>();

  private final RefreshAction myRefreshAction = new RefreshAction();
  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  private SmartPsiElementPointer mySmartPsiElementPointer;
  private boolean myIsInterface;
  private final CardLayout myCardLayout;
  private final JPanel myTreePanel;
  private String myCurrentViewName;

  private final AutoScrollToSourceHandler myAutoScrollToSourceHandler;
  private final MyDeleteProvider myDeleteElementProvider = new MyDeleteProvider();

  private boolean myCachedIsValidBase = false;

  private static final String TYPE_HIERARCHY_BROWSER_DATA_CONSTANT = "com.intellij.ide.hierarchy.type.TypeHierarchyBrowser";
  private final List<Runnable> myRunOnDisposeList = new ArrayList<Runnable>();
  private final HashMap<String, OccurenceNavigator> myOccurenceNavigators = new HashMap<String, OccurenceNavigator>();
  private static final OccurenceNavigator EMPTY_NAVIGATOR = new OccurenceNavigator() {
    public boolean hasNextOccurence() {
      return false;
    }

    public boolean hasPreviousOccurence() {
      return false;
    }

    public OccurenceInfo goNextOccurence() {
      return null;
    }

    public OccurenceInfo goPreviousOccurence() {
      return null;
    }

    public String getNextOccurenceActionName() {
      return "";
    }

    public String getPreviousOccurenceActionName() {
      return "";
    }
  };

  public TypeHierarchyBrowser(final Project project, final PsiClass psiClass) {
    myProject = project;

    myAutoScrollToSourceHandler = new AutoScrollToSourceHandler() {
      protected boolean isAutoScrollMode() {
        return HierarchyBrowserManager.getInstance(myProject).getState().IS_AUTOSCROLL_TO_SOURCE;
      }

      protected void setAutoScrollMode(final boolean state) {
        HierarchyBrowserManager.getInstance(myProject).getState().IS_AUTOSCROLL_TO_SOURCE = state;
      }
    };

    setHierarchyBase(psiClass);
    setLayout(new BorderLayout());

    add(createToolbar(ActionPlaces.TYPE_HIERARCHY_VIEW_TOOLBAR, HELP_ID).getComponent(), BorderLayout.NORTH);

    myCardLayout = new CardLayout();
    myTreePanel = new JPanel(myCardLayout);
    myTrees.put(TypeHierarchyTreeStructure.TYPE, createTree());
    myTrees.put(SupertypesHierarchyTreeStructure.TYPE, createTree());
    myTrees.put(SubtypesHierarchyTreeStructure.TYPE, createTree());
    final Enumeration<String> keys = myTrees.keys();
    while (keys.hasMoreElements()) {
      final String key = keys.nextElement();
      final JTree tree = myTrees.get(key);
      myOccurenceNavigators.put(key, new OccurenceNavigatorSupport(tree) {
        @Nullable
        protected Navigatable createDescriptorForNode(DefaultMutableTreeNode node) {
          final Object userObject = node.getUserObject();
          if (userObject instanceof String) return null;
          TypeHierarchyNodeDescriptor nodeDescriptor = (TypeHierarchyNodeDescriptor)userObject;
          final PsiElement psiElement = nodeDescriptor.getPsiClass();
          if (psiElement == null || !psiElement.isValid()) return null;
          PsiElement navigationElement = psiElement.getNavigationElement();
          return new OpenFileDescriptor(psiElement.getProject(), navigationElement.getContainingFile().getVirtualFile(),
                                        navigationElement.getTextOffset());
        }

        public String getNextOccurenceActionName() {
          return IdeBundle.message("hierarchy.type.next.occurence.name");
        }

        public String getPreviousOccurenceActionName() {
          return IdeBundle.message("hierarchy.type.prev.occurence.name");
        }
      });
      myTreePanel.add(new JScrollPane(tree), key);
    }
    add(myTreePanel, BorderLayout.CENTER);
  }

  public String getCurrentViewName() {
    return myCurrentViewName;
  }

  public boolean isInterface() {
    return myIsInterface;
  }

  private JTree createTree() {
    final Tree tree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode("")));
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    tree.setToggleClickCount(-1);
    tree.setCellRenderer(new HierarchyNodeRenderer());
    UIUtil.setLineStyleAngled(tree);
    EditSourceOnDoubleClickHandler.install(tree);
    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_TYPE_HIERARCHY_POPUP);
    PopupHandler.installPopupHandler(tree, group, ActionPlaces.TYPE_HIERARCHY_VIEW_POPUP, ActionManager.getInstance());

    myRefreshAction.registerShortcutOn(tree);
    myRunOnDisposeList.add(new Runnable() {
      public void run() {
        myRefreshAction.unregisterCustomShortcutSet(tree);
      }
    });

    final BaseOnThisTypeAction baseOnThisTypeAction = new BaseOnThisTypeAction();
    baseOnThisTypeAction
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_TYPE_HIERARCHY).getShortcutSet(), tree);

    new TreeSpeedSearch(tree);
    TreeUtil.installActions(tree);
    TreeToolTipHandler.install(tree);
    myAutoScrollToSourceHandler.install(tree);
    return tree;
  }

  private void setHierarchyBase(final PsiClass psiClass) {
    mySmartPsiElementPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(psiClass);
    myIsInterface = psiClass.isInterface();
  }

  private void restoreCursor() {
    /*int n =*/
    myAlarm.cancelAllRequests();
    //    if (n == 0) {
    setCursor(Cursor.getDefaultCursor());
    //    }
  }

  private void setWaitCursor() {
    myAlarm.addRequest(new Runnable() {
      public void run() {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      }
    }, 100);
  }

  public final void changeView(final String typeName) {
    myCurrentViewName = typeName;

    final PsiElement element = mySmartPsiElementPointer.getElement();
    if (!(element instanceof PsiClass)) {
      return;
    }
    final PsiClass psiClass = (PsiClass)element;

    if (myContent != null) {
      myContent.setDisplayName(MessageFormat.format(typeName, ClassPresentationUtil.getNameForClass(psiClass, false)));
    }

    myCardLayout.show(myTreePanel, typeName);

    if (!myBuilders.containsKey(typeName)) {
      setWaitCursor();

      // create builder
      final JTree tree = myTrees.get(typeName);
      final DefaultTreeModel model = /*(DefaultTreeModel)tree.getModel()*/ new DefaultTreeModel(new DefaultMutableTreeNode(""));
      tree.setModel(model);

      final HierarchyTreeStructure structure;
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      if (SupertypesHierarchyTreeStructure.TYPE.equals(typeName)) {
        structure = new SupertypesHierarchyTreeStructure(myProject, psiClass);
      }
      else if (SubtypesHierarchyTreeStructure.TYPE.equals(typeName)) {
        structure = new SubtypesHierarchyTreeStructure(myProject, psiClass);
      }
      else if (TypeHierarchyTreeStructure.TYPE.equals(typeName)) {
        structure = new TypeHierarchyTreeStructure(myProject, psiClass);
      }
      else {
        LOG.error("unexpected type: " + typeName);
        return;
      }
      final Comparator<NodeDescriptor> comparator = JavaHierarchyUtil.getComparator(myProject);
      final HierarchyTreeBuilder builder = new HierarchyTreeBuilder(myProject, tree, model, structure, comparator);

      myBuilders.put(typeName, builder);

      final HierarchyNodeDescriptor baseDescriptor = structure.getBaseDescriptor();
      builder.buildNodeForElement(baseDescriptor);
      final DefaultMutableTreeNode node = builder.getNodeForElement(baseDescriptor);
      if (node != null) {
        final TreePath path = new TreePath(node.getPath());
        tree.expandPath(path);
        TreeUtil.selectPath(tree, path);
      }

      restoreCursor();
    }

    getCurrentTree().requestFocus();
  }

  protected void addSpecificActions(final DefaultActionGroup actionGroup) {
    actionGroup.add(new ViewClassHierarchyAction());
    actionGroup.add(new ViewSupertypesHierarchyAction());
    actionGroup.add(new ViewSubtypesHierarchyAction());
    actionGroup.add(new AlphaSortAction());
    actionGroup.add(myRefreshAction);
    actionGroup.add(myAutoScrollToSourceHandler.createToggleAction());
  }

  public boolean hasNextOccurence() {
    return getOccurrenceNavigator().hasNextOccurence();
  }

  public boolean hasPreviousOccurence() {
    return getOccurrenceNavigator().hasPreviousOccurence();
  }

  public OccurenceInfo goNextOccurence() {
    return getOccurrenceNavigator().goNextOccurence();
  }

  public OccurenceInfo goPreviousOccurence() {
    return getOccurrenceNavigator().goPreviousOccurence();
  }

  public String getNextOccurenceActionName() {
    return getOccurrenceNavigator().getNextOccurenceActionName();
  }

  public String getPreviousOccurenceActionName() {
    return getOccurrenceNavigator().getPreviousOccurenceActionName();
  }

  private OccurenceNavigator getOccurrenceNavigator() {
    if (myCurrentViewName == null) {
      return EMPTY_NAVIGATOR;
    }
    final OccurenceNavigator navigator = myOccurenceNavigators.get(myCurrentViewName);
    return navigator != null? navigator : EMPTY_NAVIGATOR;
  }

  final class RefreshAction extends com.intellij.ide.actions.RefreshAction {
    public RefreshAction() {
      super(IdeBundle.message("action.refresh"), IdeBundle.message("action.refresh"), IconLoader.getIcon("/actions/sync.png"));
    }

    public final void actionPerformed(final AnActionEvent e) {
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      if (!isValidBase()) return;

      final Object[] storedInfo = new Object[1];
      if (myCurrentViewName != null) {
        final HierarchyTreeBuilder builder = myBuilders.get(myCurrentViewName);
        storedInfo[0] = builder.storeExpandedAndSelectedInfo();
      }

      final PsiClass base = (PsiClass)mySmartPsiElementPointer.getElement();
      final String[] name = new String[]{myCurrentViewName};
      dispose();
      setHierarchyBase(base);
      validate();
      if (myIsInterface && TypeHierarchyTreeStructure.TYPE.equals(name[0])) {
        name[0] = SubtypesHierarchyTreeStructure.TYPE;
      }
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          changeView(name[0]);
          if (storedInfo != null) {
            final HierarchyTreeBuilder builder = myBuilders.get(myCurrentViewName);
            builder.restoreExpandedAndSelectedInfo(storedInfo[0]);
          }
        }
      });
    }

    public final void update(final AnActionEvent event) {
      final Presentation presentation = event.getPresentation();
      presentation.setEnabled(isValidBase());
    }
  }

  boolean isValidBase() {
    if (PsiDocumentManager.getInstance(myProject).getUncommittedDocuments().length > 0) {
      return myCachedIsValidBase;
    }

    final PsiElement element = mySmartPsiElementPointer.getElement();
    myCachedIsValidBase = element instanceof PsiClass && element.isValid();
    return myCachedIsValidBase;
  }

  protected JTree getCurrentTree() {
    if (myCurrentViewName == null) return null;
    return myTrees.get(myCurrentViewName);
  }

  @Override
  protected PsiClass getSelectedElement() {
    return (PsiClass) super.getSelectedElement();
  }

  protected PsiClass getPsiElementFromNodeDescriptor(final Object userObject) {
    if (!(userObject instanceof TypeHierarchyNodeDescriptor)) return null;
    return ((TypeHierarchyNodeDescriptor)userObject).getPsiClass();
  }


  public final Object getData(final String dataId) {
    if (DataConstants.DELETE_ELEMENT_PROVIDER.equals(dataId)) {
      return myDeleteElementProvider;
    }
    if (DataConstants.HELP_ID.equals(dataId)) {
      return HELP_ID;
    }
    if (TYPE_HIERARCHY_BROWSER_DATA_CONSTANT.equals(dataId)) {
      return this;
    }
    if (TYPE_HIERARCHY_BROWSER_ID.equals(dataId)) {
      return this;
    }

    return super.getData(dataId);
  }

  public final void dispose() {
    final Collection<HierarchyTreeBuilder> builders = myBuilders.values();
    for (final HierarchyTreeBuilder builder : builders) {
      Disposer.dispose(builder);
    }
    for (final Runnable aMyRunOnDisposeList : myRunOnDisposeList) {
      aMyRunOnDisposeList.run();
    }
    myRunOnDisposeList.clear();
    myBuilders.clear();
  }

  private final class AlphaSortAction extends ToggleAction {
    public AlphaSortAction() {
      super(IdeBundle.message("action.sort.alphabetically"), IdeBundle.message("action.sort.alphabetically"),
            IconLoader.getIcon("/objectBrowser/sorted.png"));
    }

    public final boolean isSelected(final AnActionEvent event) {
      return HierarchyBrowserManager.getInstance(myProject).getState().SORT_ALPHABETICALLY;
    }

    public final void setSelected(final AnActionEvent event, final boolean flag) {
      final HierarchyBrowserManager hierarchyBrowserManager = HierarchyBrowserManager.getInstance(myProject);
      hierarchyBrowserManager.getState().SORT_ALPHABETICALLY = flag;
      final Comparator<NodeDescriptor> comparator = JavaHierarchyUtil.getComparator(myProject);
      final Collection<HierarchyTreeBuilder> builders = myBuilders.values();
      for (final HierarchyTreeBuilder builder : builders) {
        builder.setNodeDescriptorComparator(comparator);
      }
    }

    public final void update(final AnActionEvent event) {
      super.update(event);
      final Presentation presentation = event.getPresentation();
      presentation.setEnabled(isValidBase());
    }
  }

  public static final class BaseOnThisTypeAction extends AnAction {
    public final void actionPerformed(final AnActionEvent event) {
      final DataContext dataContext = event.getDataContext();
      final TypeHierarchyBrowser browser = (TypeHierarchyBrowser)dataContext.getData(TYPE_HIERARCHY_BROWSER_DATA_CONSTANT);
      if (browser == null) return;

      final PsiClass selectedClass = browser.getSelectedElement();
      if (selectedClass == null) return;
      final String[] name = new String[]{browser.myCurrentViewName};
      browser.dispose();
      browser.setHierarchyBase(selectedClass);
      browser.validate();
      if (browser.myIsInterface && TypeHierarchyTreeStructure.TYPE.equals(name[0])) {
        name[0] = SubtypesHierarchyTreeStructure.TYPE;
      }
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          browser.changeView(name[0]);
        }
      });
    }

    public final void update(final AnActionEvent event) {
      final Presentation presentation = event.getPresentation();
      registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_TYPE_HIERARCHY).getShortcutSet(), null);

      final DataContext dataContext = event.getDataContext();
      final TypeHierarchyBrowser browser = (TypeHierarchyBrowser)dataContext.getData(TYPE_HIERARCHY_BROWSER_DATA_CONSTANT);
      if (browser == null) {
        presentation.setVisible(false);
        presentation.setEnabled(false);
        return;
      }

      presentation.setVisible(true);

      final PsiClass selectedClass = browser.getSelectedElement();
      if (selectedClass != null && !selectedClass.equals(browser.mySmartPsiElementPointer.getElement()) &&
          !"java.lang.Object".equals(selectedClass.getQualifiedName()) && selectedClass.isValid()) {
        presentation.setText(selectedClass.isInterface()
                             ? IdeBundle.message("action.base.on.this.interface")
                             : IdeBundle.message("action.base.on.this.class"));
        presentation.setEnabled(true);
      }
      else {
        presentation.setEnabled(false);
      }
    }
  }

  private final class MyDeleteProvider implements DeleteProvider {
    public final void deleteElement(final DataContext dataContext) {
      final PsiClass aClass = getSelectedElement();
      if (aClass == null || aClass instanceof PsiAnonymousClass) return;
      LocalHistoryAction a = LocalHistory.startAction(myProject, IdeBundle.message("progress.deleting.class", aClass.getQualifiedName()));
      try {
        final PsiElement[] elements = new PsiElement[]{aClass};
        DeleteHandler.deletePsiElement(elements, myProject);
      }
      finally {
        a.finish();
      }
    }

    public final boolean canDeleteElement(final DataContext dataContext) {
      final PsiClass aClass = getSelectedElement();
      if (aClass == null || aClass instanceof PsiAnonymousClass) {
        return false;
      }
      final PsiElement[] elements = new PsiElement[]{aClass};
      return DeleteHandler.shouldEnableDeleteAction(elements);
    }
  }
}
