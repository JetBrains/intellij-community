package com.intellij.ide.hierarchy.method;

import com.intellij.ide.actions.CloseTabToolbarAction;
import com.intellij.ide.actions.ToolbarHelpAction;
import com.intellij.ide.hierarchy.*;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.IconLoader;
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

public final class MethodHierarchyBrowser extends JPanel implements DataProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.hierarchy.method.MethodHierarchyBrowser");

  private final static String HELP_ID = "viewingStructure.methodHierarchy";

  private Content myContent;
  private final Project myProject;
  private final Hashtable<String,HierarchyTreeBuilder> myBuilders = new Hashtable<String, HierarchyTreeBuilder>();
  private final Hashtable<Object,JTree> myTrees = new Hashtable<Object, JTree>();

  private final RefreshAction myRefreshAction = new RefreshAction();
  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  private SmartPsiElementPointer mySmartPsiElementPointer;
  private final CardLayout myCardLayout;
  private final JPanel myTreePanel;
  private String myCurrentViewName;

  private final AutoScrollToSourceHandler myAutoScrollToSourceHandler;

  static final String METHOD_HIERARCHY_BROWSER_DATA_CONSTANT = "com.intellij.ide.hierarchy.type.MethodHierarchyBrowser";

  public MethodHierarchyBrowser(final Project project, final PsiMethod method) {
    myProject = project;

    myAutoScrollToSourceHandler = new AutoScrollToSourceHandler(myProject) {
      protected boolean isAutoScrollMode() {
        return HierarchyBrowserManager.getInstance(myProject).IS_AUTOSCROLL_TO_SOURCE;
      }

      protected void setAutoScrollMode(final boolean state) {
        HierarchyBrowserManager.getInstance(myProject).IS_AUTOSCROLL_TO_SOURCE = state;
      }
    };

    setHierarchyBase(method);
    setLayout(new BorderLayout());

    final ActionToolbar toolbar = createToolbar();
    add(toolbar.getComponent(), BorderLayout.NORTH);

    myCardLayout = new CardLayout();
    myTreePanel = new JPanel(myCardLayout);
    myTrees.put(MethodHierarchyTreeStructure.TYPE, createTree());
    final Enumeration<Object> keys = myTrees.keys();
    while (keys.hasMoreElements()) {
      final Object key = keys.nextElement();
      final JTree tree = myTrees.get(key);
      myTreePanel.add(new JScrollPane(tree), key);
    }
    add(myTreePanel, BorderLayout.CENTER);

    add(createLegendPanel(), BorderLayout.SOUTH);
  }

  private JPanel createLegendPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());

    JLabel label;
    final GridBagConstraints gc = new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.WEST,
                                                   GridBagConstraints.HORIZONTAL, new Insets(3, 5, 0, 5), 0, 0);

    label =
    new JLabel("method is defined in the class", IconLoader.getIcon("/hierarchy/methodDefined.png"),
               SwingConstants.LEFT);
    label.setUI(new MultiLineLabelUI());
    label.setIconTextGap(10);
    panel.add(label, gc);

    gc.gridy++;
    label =
    new JLabel("method is not defined in the class but defined in superclass",
               IconLoader.getIcon("/hierarchy/methodNotDefined.png"), SwingConstants.LEFT);
    label.setUI(new MultiLineLabelUI());
    label.setIconTextGap(10);
    panel.add(label, gc);

    gc.gridy++;
    label =
    new JLabel("method should be defined since the class is not abstract",
               IconLoader.getIcon("/hierarchy/shouldDefineMethod.png"), SwingConstants.LEFT);
    label.setUI(new MultiLineLabelUI());
    label.setIconTextGap(10);
    panel.add(label, gc);

    return panel;
  }

  private JTree createTree() {
    final Tree tree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode("")));
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    tree.setToggleClickCount(-1);
    tree.setCellRenderer(new HierarchyNodeRenderer());
    tree.putClientProperty("JTree.lineStyle", "Angled");
    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_METHOD_HIERARCHY_POPUP);
    PopupHandler.installPopupHandler(tree, group, ActionPlaces.METHOD_HIERARCHY_VIEW_POPUP, ActionManager.getInstance());
    EditSourceOnDoubleClickHandler.install(tree);

    final ShortcutSet shortcutSet = ActionManager.getInstance().getAction(IdeActions.ACTION_REFRESH).getShortcutSet();
    myRefreshAction.registerCustomShortcutSet(shortcutSet, tree);

    final BaseOnThisMethodAction baseOnThisMethodAction = new BaseOnThisMethodAction();
    baseOnThisMethodAction.registerCustomShortcutSet(
      ActionManager.getInstance().getAction(IdeActions.ACTION_METHOD_HIERARCHY).getShortcutSet(), tree);

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
    myCurrentViewName = typeName;

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
      final JTree tree = myTrees.get(typeName);
      final DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode(""));
      tree.setModel(model);

      final HierarchyTreeStructure structure;
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      if (MethodHierarchyTreeStructure.TYPE.equals(typeName)) {
        structure = new MethodHierarchyTreeStructure(myProject, method);
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
      final TreePath path = new TreePath(node.getPath());
      tree.expandPath(path);
      TreeUtil.selectPath(tree, path);

      restoreCursor();
    }

    getCurrentTree().requestFocus();
  }

  private ActionToolbar createToolbar() {
    final DefaultActionGroup actionGroup = new DefaultActionGroup();

    actionGroup.add(new AlphaSortAction());
    actionGroup.add(new ShowImplementationsOnlyAction());
    actionGroup.add(myRefreshAction);
    actionGroup.add(myAutoScrollToSourceHandler.createToggleAction());
    actionGroup.add(new CloseAction());
    actionGroup.add(new ToolbarHelpAction(HELP_ID));

    final ActionToolbar toolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.METHOD_HIERARCHY_VIEW_TOOLBAR,
                                                                            actionGroup, true);
    return toolBar;
  }

  final class RefreshAction extends AnAction {
    public RefreshAction() {
      super("Refresh", "Refresh", IconLoader.getIcon("/actions/sync.png"));
    }

    public final void actionPerformed(final AnActionEvent e) {
      if (!isValidBase()) return;

      final Object[] storedInfo = new Object[1];
      if (myCurrentViewName != null) {
        final HierarchyTreeBuilder builder = myBuilders.get(myCurrentViewName);
        storedInfo[0] = builder.storeExpandedAndSelectedInfo();
      }

      final PsiMethod base = (PsiMethod)mySmartPsiElementPointer.getElement();
      final String[] name = new String[]{myCurrentViewName};
      dispose();
      setHierarchyBase(base);
      validate();
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

  private boolean isValidBase() {
    final PsiElement element = mySmartPsiElementPointer.getElement();
    return element instanceof PsiMethod && element.isValid();
  }

  final class ShowImplementationsOnlyAction extends ToggleAction {
    public ShowImplementationsOnlyAction() {
      super("Hide classes where the method is legally not implemented", null, IconLoader.getIcon("/ant/filter.png")); // TODO[anton] use own icon!!!
    }

    public final boolean isSelected(final AnActionEvent event) {
      return HierarchyBrowserManager.getInstance(myProject).HIDE_CLASSES_WHERE_METHOD_NOT_IMPLEMENTED;
    }

    public final void setSelected(final AnActionEvent event, final boolean flag) {
      HierarchyBrowserManager.getInstance(myProject).HIDE_CLASSES_WHERE_METHOD_NOT_IMPLEMENTED = flag;

      final Object[] storedInfo = new Object[1];
      if (myCurrentViewName != null) {
        final HierarchyTreeBuilder builder = myBuilders.get(myCurrentViewName);
        storedInfo[0] = builder.storeExpandedAndSelectedInfo();
      }

      final PsiMethod base = (PsiMethod)mySmartPsiElementPointer.getElement();
      final String[] name = new String[]{myCurrentViewName};
      dispose();
      setHierarchyBase(base);
      validate();
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
      super.update(event);
      final Presentation presentation = event.getPresentation();
      presentation.setEnabled(isValidBase());
    }
  }

  private JTree getCurrentTree() {
    if (myCurrentViewName == null) return null;
    final JTree tree = myTrees.get(myCurrentViewName);
    return tree;
  }

  public final class CloseAction extends CloseTabToolbarAction {
    public final void actionPerformed(final AnActionEvent e) {
      myContent.getManager().removeContent(myContent);
    }
  }

  private PsiElement getSelectedElement() {
    final DefaultMutableTreeNode node = getSelectedNode();
    if (node == null) return null;
    final Object userObject = node.getUserObject();
    if (!(userObject instanceof MethodHierarchyNodeDescriptor)) return null;
    final PsiElement element = ((MethodHierarchyNodeDescriptor)userObject).getTargetElement();
    return element;
  }

  private PsiMethod[] getSelectedMethods() {
    MethodHierarchyNodeDescriptor[] descriptors = getSelectedDescriptors();
    ArrayList<PsiMethod> psiElements = new ArrayList<PsiMethod>();
    for (int i = 0; i < descriptors.length; i++) {
      MethodHierarchyNodeDescriptor descriptor = descriptors[i];
      PsiElement element = descriptor.getTargetElement();
      if (!(element instanceof PsiMethod)) continue;
      psiElements.add((PsiMethod)element);
    }
    return psiElements.toArray(new PsiMethod[psiElements.size()]);
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

  public final Object getData(final String dataId) {
    if (DataConstants.PSI_ELEMENT.equals(dataId)) {
      return getSelectedElement();
    }
    else if (DataConstantsEx.DELETE_ELEMENT_PROVIDER.equals(dataId)) {
      return null;
    }
    else if (METHOD_HIERARCHY_BROWSER_DATA_CONSTANT.equals(dataId)) {
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

  public final void dispose() {
    final Collection<HierarchyTreeBuilder> builders = myBuilders.values();
    for (Iterator<HierarchyTreeBuilder> iterator = builders.iterator(); iterator.hasNext();) {
      final HierarchyTreeBuilder builder = iterator.next();
      builder.dispose();
    }
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
      final Presentation presentation = event.getPresentation();
      presentation.setEnabled(isValidBase());
    }
  }

  public static final class BaseOnThisMethodAction extends AnAction {
    public BaseOnThisMethodAction() {
      super("Base on This Method");
    }

    public final void actionPerformed(final AnActionEvent event) {
      final DataContext dataContext = event.getDataContext();
      final MethodHierarchyBrowser browser = (MethodHierarchyBrowser)dataContext.getData(
        METHOD_HIERARCHY_BROWSER_DATA_CONSTANT);
      if (browser == null) return;

      final PsiElement selectedElement = browser.getSelectedElement();
      if (!(selectedElement instanceof PsiMethod)) {
        return;
      }
      final PsiMethod method = (PsiMethod)selectedElement;

      final String[] name = new String[]{browser.myCurrentViewName};
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
        ActionManager.getInstance().getAction(IdeActions.ACTION_METHOD_HIERARCHY).getShortcutSet(), null);

      final DataContext dataContext = event.getDataContext();
      final MethodHierarchyBrowser browser = (MethodHierarchyBrowser)dataContext.getData(
        METHOD_HIERARCHY_BROWSER_DATA_CONSTANT);
      if (browser == null) {
        presentation.setVisible(false);
        presentation.setEnabled(false);
        return;
      }

      final PsiElement selectedElement = browser.getSelectedElement();
      if (!(selectedElement instanceof PsiMethod)) {
        presentation.setEnabled(false);
        presentation.setVisible(false);
        return;
      }

      presentation.setVisible(true);

      final PsiMethod method = (PsiMethod)selectedElement;
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

  public final MethodHierarchyNodeDescriptor[] getSelectedDescriptors() {
    final JTree tree = getCurrentTree();
    if (tree == null) {
      return new MethodHierarchyNodeDescriptor[0];
    }
    final TreePath[] paths = tree.getSelectionPaths();
    if (paths == null) {
      return new MethodHierarchyNodeDescriptor[0];
    }
    final ArrayList<MethodHierarchyNodeDescriptor> list = new ArrayList<MethodHierarchyNodeDescriptor>(paths.length);
    for (int i = 0; i < paths.length; i++) {
      final TreePath path = paths[i];
      final Object lastPathComponent = path.getLastPathComponent();
      if (lastPathComponent instanceof DefaultMutableTreeNode) {
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode)lastPathComponent;
        final Object userObject = node.getUserObject();
        if (userObject instanceof MethodHierarchyNodeDescriptor) {
          list.add((MethodHierarchyNodeDescriptor)userObject);
        }
      }
    }
    return list.toArray(new MethodHierarchyNodeDescriptor[list.size()]);
  }

  public final PsiMethod getBaseMethod() {
    final HierarchyTreeBuilder builder = myBuilders.get(myCurrentViewName);
    final MethodHierarchyTreeStructure treeStructure = (MethodHierarchyTreeStructure)builder.getTreeStructure();

    final PsiMethod baseMethod = treeStructure.getBaseMethod();
    return baseMethod;
  }
}
