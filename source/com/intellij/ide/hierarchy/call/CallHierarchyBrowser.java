package com.intellij.ide.hierarchy.call;

import com.intellij.ide.actions.CloseTabToolbarAction;
import com.intellij.ide.actions.ToolbarHelpAction;
import com.intellij.ide.hierarchy.*;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.ui.AutoScrollToSourceHandler;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.TreeToolTipHandler;
import com.intellij.ui.content.Content;
import com.intellij.util.Alarm;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.*;
import java.util.List;

public final class CallHierarchyBrowser extends JPanel implements DataProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.hierarchy.call.CallHierarchyBrowser");

  static final String SCOPE_PROJECT = "Project";
  static final String SCOPE_ALL = "All";
  static final String SCOPE_CLASS = "This Class";

  private final static String HELP_ID = "viewingStructure.callHierarchy";

  private Content myContent;
  private final Project myProject;
  private final Hashtable<String,HierarchyTreeBuilder> myBuilders = new Hashtable<String, HierarchyTreeBuilder>();
  private final Hashtable<Object,JTree> myType2TreeMap = new Hashtable<Object, JTree>();
  private final Hashtable<String,String> myType2ScopeMap = new Hashtable<String, String>();

  private final RefreshAction myRefreshAction = new RefreshAction();
  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  private SmartPsiElementPointer mySmartPsiElementPointer;
  private final CardLayout myCardLayout;
  private final JPanel myTreePanel;
  private String myCurrentViewType;

  private final AutoScrollToSourceHandler myAutoScrollToSourceHandler;

  private static final String CALL_HIERARCHY_BROWSER_DATA_CONSTANT = "com.intellij.ide.hierarchy.call.CallHierarchyBrowser";
  private List<Runnable> myRunOnDisposeList = new ArrayList<Runnable>();
  private static final CallHierarchyNodeDescriptor[] EMPTY_DESCRIPTORS = new CallHierarchyNodeDescriptor[0];

  public CallHierarchyBrowser(final Project project, final PsiMethod method) {
    myProject = project;

    myType2ScopeMap.put(CallerMethodsTreeStructure.TYPE, SCOPE_PROJECT);
    myType2ScopeMap.put(CalleeMethodsTreeStructure.TYPE, SCOPE_PROJECT);

    myAutoScrollToSourceHandler = new AutoScrollToSourceHandler() {
      protected boolean isAutoScrollMode() {
        return HierarchyBrowserManager.getInstance(myProject).IS_AUTOSCROLL_TO_SOURCE;
      }

      protected void setAutoScrollMode(final boolean state) {
        HierarchyBrowserManager.getInstance(myProject).IS_AUTOSCROLL_TO_SOURCE = state;
      }

      protected void scrollToSource(Component tree) {
        super.scrollToSource(tree);
      }

    };

    setHierarchyBase(method);
    setLayout(new BorderLayout());

    final ActionToolbar toolbar = createToolbar();
    add(toolbar.getComponent(), BorderLayout.NORTH);

    myCardLayout = new CardLayout();
    myTreePanel = new JPanel(myCardLayout);

    myType2TreeMap.put(CalleeMethodsTreeStructure.TYPE, createTree());
    myType2TreeMap.put(CallerMethodsTreeStructure.TYPE, createTree());

    final Enumeration<Object> keys = myType2TreeMap.keys();
    while (keys.hasMoreElements()) {
      final Object key = keys.nextElement();
      final JTree tree = myType2TreeMap.get(key);
      myTreePanel.add(new JScrollPane(tree), key);
    }
    add(myTreePanel, BorderLayout.CENTER);
  }

  private JTree createTree() {
    final Tree tree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode("")));
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    tree.setToggleClickCount(-1);
    tree.setCellRenderer(new HierarchyNodeRenderer());
    tree.putClientProperty("JTree.lineStyle", "Angled");

    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_CALL_HIERARCHY_POPUP);
    PopupHandler.installPopupHandler(tree, group, ActionPlaces.CALL_HIERARCHY_VIEW_POPUP, ActionManager.getInstance());
    EditSourceOnDoubleClickHandler.install(tree);

    myRefreshAction.registerShortcutOn(tree);
    myRunOnDisposeList.add(new Runnable() {
      public void run() {
        myRefreshAction.unregisterCustomShortcutSet(tree);
      }
    });

    final BaseOnThisMethodAction baseOnThisMethodAction = new BaseOnThisMethodAction();
    baseOnThisMethodAction.registerCustomShortcutSet(
      ActionManager.getInstance().getAction(IdeActions.ACTION_CALL_HIERARCHY).getShortcutSet(), tree);

    new TreeSpeedSearch(tree);
    TreeUtil.installActions(tree);
    TreeToolTipHandler.install(tree);
    myAutoScrollToSourceHandler.install(tree);
    return tree;
  }

  private void setHierarchyBase(final PsiMethod method) {
    mySmartPsiElementPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(method);
  }

  public final void setContent(final Content content) {
    myContent = content;
  }

  private void restoreCursor() {
    /*int n =*/ myAlarm.cancelAllRequests();
//    if (n == 0) {
    setCursor(Cursor.getDefaultCursor());
//    }
  }

  private void setWaitCursor() {
    myAlarm.addRequest(
      new Runnable() {
        public void run() {
          setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }
      },
      100
    );
  }

  public final void changeView(final String typeName) {
    myCurrentViewType = typeName;

    final PsiElement element = mySmartPsiElementPointer.getElement();
    if (!(element instanceof PsiMethod)) {
      return;
    }
    final PsiMethod method = (PsiMethod)element;

    if (myContent != null) {
      myContent.setDisplayName(typeName + method.getName());
    }

    myCardLayout.show(myTreePanel, typeName);

    if (!myBuilders.containsKey(typeName)) {
      setWaitCursor();

      // create builder
      final JTree tree = myType2TreeMap.get(typeName);
      final DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode(""));
      tree.setModel(model);

      final HierarchyTreeStructure structure;
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      if (CallerMethodsTreeStructure.TYPE.equals(typeName)) {
        structure = new CallerMethodsTreeStructure(myProject, method, getCurrentScopeType());
      }
      else if (CalleeMethodsTreeStructure.TYPE.equals(typeName)) {
        structure = new CalleeMethodsTreeStructure(myProject, method, getCurrentScopeType());
      }
      else {
        LOG.error("unexpected type: " + typeName);
        return;
      }
      final Comparator<NodeDescriptor> comparator = HierarchyBrowserManager.getInstance(myProject).getComparator();
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

  private ActionToolbar createToolbar() {
    final DefaultActionGroup actionGroup = new DefaultActionGroup();

    actionGroup.add(new ViewCallerMethodsHierarchyAction());
    actionGroup.add(new ViewCalleeMethodsHierarchyAction());
    actionGroup.add(new AlphaSortAction());
    actionGroup.add(new ChangeScopeAction());
    actionGroup.add(myRefreshAction);
    actionGroup.add(myAutoScrollToSourceHandler.createToggleAction());
    actionGroup.add(new CloseAction());
    actionGroup.add(new ToolbarHelpAction(HELP_ID));

    return ActionManager.getInstance().createActionToolbar(ActionPlaces.CALL_HIERARCHY_VIEW_TOOLBAR,
                                                           actionGroup, true);
  }

  private abstract class ChangeViewTypeActionBase extends ToggleAction {
    public ChangeViewTypeActionBase(final String shortDescription, final String longDescription, final Icon icon) {
      super(shortDescription, longDescription, icon);
    }

    public final boolean isSelected(final AnActionEvent event) {
      return getTypeName().equals(myCurrentViewType);
    }

    protected abstract String getTypeName();

    public final void setSelected(final AnActionEvent event, final boolean flag) {
      if (flag) {
//        setWaitCursor();
        // invokeLater is called to update state of button before long tree building operation
        ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                changeView(getTypeName());
              }
            });
      }
    }

    public final void update(final AnActionEvent event) {
      super.update(event);
      setEnabled(isValidBase());
    }
  }

  final class ViewCallerMethodsHierarchyAction extends ChangeViewTypeActionBase {
    public ViewCallerMethodsHierarchyAction() {
      super("Caller Methods Hierarchy", "Caller Methods Hierarchy", IconLoader.getIcon("/hierarchy/caller.png"));
    }

    protected final String getTypeName() {
      return CallerMethodsTreeStructure.TYPE;
    }
  }

  final class ViewCalleeMethodsHierarchyAction extends ChangeViewTypeActionBase {
    public ViewCalleeMethodsHierarchyAction() {
      super("Callee Methods Hierarchy", "Callee Methods Hierarchy", IconLoader.getIcon("/hierarchy/callee.png"));
    }

    protected final String getTypeName() {
      return CalleeMethodsTreeStructure.TYPE;
    }
  }

  final class RefreshAction extends com.intellij.ide.actions.RefreshAction {
    public RefreshAction() {
      super("Refresh", "Refresh", IconLoader.getIcon("/actions/sync.png"));
    }

    public final void actionPerformed(final AnActionEvent e) {
      if (!isValidBase()) return;

      final Object[] storedInfo = new Object[1];
      if (myCurrentViewType != null) {
        final HierarchyTreeBuilder builder = myBuilders.get(myCurrentViewType);
        storedInfo[0] = builder.storeExpandedAndSelectedInfo();
      }

      final PsiMethod base = (PsiMethod)mySmartPsiElementPointer.getElement();
      final String[] name = new String[]{myCurrentViewType};
      dispose();
      setHierarchyBase(base);
      validate();

      ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            changeView(name[0]);
            if (storedInfo != null) {
              final HierarchyTreeBuilder builder = myBuilders.get(myCurrentViewType);
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

  private boolean isValidBase() {
    final PsiElement element = mySmartPsiElementPointer.getElement();
    return element instanceof PsiMethod && element.isValid();
  }

  private JTree getCurrentTree() {
    if (myCurrentViewType == null) return null;
    return myType2TreeMap.get(myCurrentViewType);
  }

  private String getCurrentScopeType() {
    if (myCurrentViewType == null) return null;
    return myType2ScopeMap.get(myCurrentViewType);
  }

  public final class CloseAction extends CloseTabToolbarAction {
    public final void actionPerformed(final AnActionEvent e) {
      myContent.getManager().removeContent(myContent);
    }
  }

  private PsiMethod getSelectedMethod() {
    final PsiElement enclosingElement = getSelectedEnclosingElement();
    return enclosingElement instanceof PsiMethod ? (PsiMethod)enclosingElement : null;
  }

  private PsiMember getSelectedEnclosingElement() {
    final DefaultMutableTreeNode node = getSelectedNode();
    if (node == null) return null;
    final Object userObject = node.getUserObject();
    if (!(userObject instanceof CallHierarchyNodeDescriptor)) return null;
    final PsiMember enclosingElement = ((CallHierarchyNodeDescriptor)userObject).getEnclosingElement();
    return enclosingElement;
  }

  private DefaultMutableTreeNode getSelectedNode() {
    final JTree tree = getCurrentTree();
    if (tree == null) return null;
    final TreePath path = tree.getSelectionPath();
    if (path == null) return null;
    final Object lastPathComponent = path.getLastPathComponent();
    if (!(lastPathComponent instanceof DefaultMutableTreeNode)) return null;
    return (DefaultMutableTreeNode)lastPathComponent;
  }

  private PsiMethod[] getSelectedMethods() {
    JTree tree = getCurrentTree();
    if (tree == null) return PsiMethod.EMPTY_ARRAY;
    TreePath[] paths = tree.getSelectionPaths();
    if (paths == null) return PsiMethod.EMPTY_ARRAY;
    ArrayList<PsiMethod> psiMethods = new ArrayList<PsiMethod>();
    for (int i = 0; i < paths.length; i++) {
      TreePath path = paths[i];
      Object node = path.getLastPathComponent();
      if (!(node instanceof DefaultMutableTreeNode)) continue;
      Object userObject = ((DefaultMutableTreeNode)node).getUserObject();
      if (!(userObject instanceof CallHierarchyNodeDescriptor)) continue;
      PsiMember enclosingElement = ((CallHierarchyNodeDescriptor)userObject).getEnclosingElement();
      if (!(enclosingElement instanceof PsiMethod)) continue;
      psiMethods.add((PsiMethod)enclosingElement);
    }
    return psiMethods.toArray(new PsiMethod[psiMethods.size()]);
  }

  private CallHierarchyNodeDescriptor[] getSelectedDescriptors() {
    JTree tree = getCurrentTree();
    if (tree == null) return EMPTY_DESCRIPTORS;
    TreePath[] paths = tree.getSelectionPaths();
    final ArrayList<CallHierarchyNodeDescriptor> result = new ArrayList<CallHierarchyNodeDescriptor>();
    for (int i = 0; i < paths.length; i++) {
      TreePath path = paths[i];
      Object node = path.getLastPathComponent();
      if (!(node instanceof DefaultMutableTreeNode)) continue;
      Object userObject = ((DefaultMutableTreeNode)node).getUserObject();
      if (!(userObject instanceof CallHierarchyNodeDescriptor)) continue;
      result.add((CallHierarchyNodeDescriptor)userObject);
    }
    return result.toArray(new CallHierarchyNodeDescriptor[result.size()]);
  }

  public final Object getData(final String dataId) {
    if (DataConstants.PSI_ELEMENT.equals(dataId)) {
      return getSelectedEnclosingElement();
    }
    if (DataConstants.NAVIGATABLE_ARRAY.equals(dataId)) {
      return getNavigatables();
    }
    else if (DataConstantsEx.DELETE_ELEMENT_PROVIDER.equals(dataId)) {
      return null;
    }
    else if (CALL_HIERARCHY_BROWSER_DATA_CONSTANT.equals(dataId)) {
      return this;
    }
    else if (DataConstantsEx.HELP_ID.equals(dataId)) {
      return HELP_ID;
    }
    else if (DataConstantsEx.PSI_ELEMENT_ARRAY.equals(dataId)) {
      return getSelectedMethods();
    }
    return null;
  }

  private Navigatable[] getNavigatables() {
    return getSelectedDescriptors();
  }

  public final void dispose() {
    final Collection<HierarchyTreeBuilder> builders = myBuilders.values();
    for (Iterator<HierarchyTreeBuilder> iterator = builders.iterator(); iterator.hasNext();) {
      final HierarchyTreeBuilder builder = iterator.next();
      builder.dispose();
    }
    for (Iterator<Runnable> it = myRunOnDisposeList.iterator(); it.hasNext();) {
      it.next().run();
    }
    myRunOnDisposeList.clear();
    myBuilders.clear();
  }

  private final class AlphaSortAction extends ToggleAction {
    public AlphaSortAction() {
      super("Sort Alphabetically", "Sort Alphabetically", IconLoader.getIcon("/objectBrowser/sorted.png"));
    }

    public final boolean isSelected(final AnActionEvent event) {
      return HierarchyBrowserManager.getInstance(myProject).SORT_ALPHABETICALLY;
    }

    public final void setSelected(final AnActionEvent event, final boolean flag) {
      final HierarchyBrowserManager hierarchyBrowserManager = HierarchyBrowserManager.getInstance(myProject);
      hierarchyBrowserManager.SORT_ALPHABETICALLY = flag;
      final Comparator<NodeDescriptor> comparator = hierarchyBrowserManager.getComparator();
      final Collection<HierarchyTreeBuilder> builders = myBuilders.values();
      for (Iterator<HierarchyTreeBuilder> iterator = builders.iterator(); iterator.hasNext();) {
        final HierarchyTreeBuilder builder = iterator.next();
        builder.setNodeDescriptorComparator(comparator);
      }
    }

    public final void update(final AnActionEvent event) {
      super.update(event);
      event.getPresentation().setEnabled(isValidBase());
    }
  }

  public static final class BaseOnThisMethodAction extends AnAction {
    public BaseOnThisMethodAction() {
      super("Base on This Method");
    }

    public final void actionPerformed(final AnActionEvent event) {
      final DataContext dataContext = event.getDataContext();
      final CallHierarchyBrowser browser = (CallHierarchyBrowser)dataContext.getData(
        CALL_HIERARCHY_BROWSER_DATA_CONSTANT);
      if (browser == null) return;

      final PsiMethod method = browser.getSelectedMethod();
      if (method == null) return;

      final String[] name = new String[]{browser.myCurrentViewType};
      browser.dispose();
      browser.setHierarchyBase(method);
      browser.validate();
      ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            browser.changeView(name[0]);
          }
        });
    }

    public final void update(final AnActionEvent event) {
      final Presentation presentation = event.getPresentation();

      registerCustomShortcutSet(
        ActionManager.getInstance().getAction(IdeActions.ACTION_CALL_HIERARCHY).getShortcutSet(), null);

      final DataContext dataContext = event.getDataContext();
      final CallHierarchyBrowser browser = (CallHierarchyBrowser)dataContext.getData(CALL_HIERARCHY_BROWSER_DATA_CONSTANT);
      if (browser == null) {
        presentation.setVisible(false);
        presentation.setEnabled(false);
        return;
      }

      final PsiMethod method = browser.getSelectedMethod();
      if (method == null) {
        presentation.setEnabled(false);
        presentation.setVisible(false);
        return;
      }

      presentation.setVisible(true);

      if (!method.equals(browser.mySmartPsiElementPointer.getElement()) &&
          method.isValid()
      ) {
        presentation.setEnabled(true);
      }
      else {
        presentation.setEnabled(false);
      }
    }
  }

  private final class ChangeScopeAction extends ComboBoxAction {
    public final void update(final AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      final Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
      if (project == null) return;

      presentation.setText(getCurrentScopeType());
    }

    protected final DefaultActionGroup createPopupActionGroup(final JComponent button) {
      final DefaultActionGroup group = new DefaultActionGroup();

      group.add(new MenuAction(SCOPE_PROJECT));
      group.add(new MenuAction(SCOPE_ALL));
      group.add(new MenuAction(SCOPE_CLASS));

      return group;
    }

    public final JComponent createCustomComponent(final Presentation presentation) {
      final JPanel panel = new JPanel(new GridBagLayout());
      panel.add(new JLabel("Scope:"),
                new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.BOTH,
                                       new Insets(0, 5, 0, 0), 0, 0));
      panel.add(super.createCustomComponent(presentation),
                new GridBagConstraints(1, 0, 1, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.BOTH,
                                       new Insets(0, 0, 0, 0), 0, 0));
      return panel;
    }

    private final class MenuAction extends AnAction {
      private final String myScopeType;

      public MenuAction(final String scopeType) {
        super(scopeType);
        myScopeType = scopeType;
      }

      public final void actionPerformed(final AnActionEvent e) {
        myType2ScopeMap.put(myCurrentViewType, myScopeType);

        // invokeLater is called to update state of button before long tree building operation
        ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                if (!isValidBase()) return;
                LOG.assertTrue(myCurrentViewType != null);

                final Object[] storedInfo = new Object[1];
                final HierarchyTreeBuilder builder = myBuilders.get(myCurrentViewType);
                storedInfo[0] = builder.storeExpandedAndSelectedInfo();

                final PsiMethod base = (PsiMethod)mySmartPsiElementPointer.getElement();
                final String[] name = new String[]{myCurrentViewType};

                builder.dispose();
                myBuilders.remove(myCurrentViewType);

                setHierarchyBase(base);
                validate();

                ApplicationManager.getApplication().invokeLater(new Runnable() {
                              public void run() {
                                changeView(name[0]);
                                if (storedInfo != null) {
                                  final HierarchyTreeBuilder builder = myBuilders.get(myCurrentViewType);
                                  builder.restoreExpandedAndSelectedInfo(storedInfo[0]);
                                }
                              }
                            });
              }
            });

      }
    }

  }

}
