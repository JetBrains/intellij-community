package com.intellij.ide.todo;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.actions.*;
import com.intellij.ide.todo.configurable.TodoConfigurable;
import com.intellij.ide.todo.nodes.TodoDirNode;
import com.intellij.ide.todo.nodes.TodoFileNode;
import com.intellij.ide.todo.nodes.TodoItemNode;
import com.intellij.ide.todo.nodes.TodoPackageNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.VisibilityWatcher;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.AutoScrollToSourceHandler;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.content.Content;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.Icons;
import com.intellij.util.OpenSourceUtil;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * @author Vladimir Kondratyev
 */
abstract class TodoPanel extends JPanel implements OccurenceNavigator, DataProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.todo.TodoPanel");
  private static final VirtualFile[] ourEmptyArray = new VirtualFile[]{};

  protected Project myProject;
  private final TodoPanelSettings mySettings;
  private final boolean myCurrentFileMode;
  private Content myContent;

  private final Tree myTree;
  private final MyTreeExpander myTreeExpander;
  private final MyOccurenceNavigator myOccurenceNavigator;
  protected final TodoTreeBuilder myTodoTreeBuilder;
  private MyVisibilityWatcher myVisibilityWatcher;

  /**
   * @param currentFileMode if <code>true</code> then view doesn't have "Group By Packages" and "Flatten Packages"
   *                        actions.
   */
  public TodoPanel(Project project,
                   TodoPanelSettings settings,
                   boolean currentFileMode,
                   Content content) {
    super(new BorderLayout());
    myProject = project;
    mySettings = settings;
    myCurrentFileMode = currentFileMode;
    myContent = content;

    DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode());
    myTree = new Tree(model);
    myTreeExpander = new MyTreeExpander();
    myOccurenceNavigator = new MyOccurenceNavigator();
    initUI();
    myTodoTreeBuilder = createTreeBuilder(myTree, model, myProject);
    updateTodoFilter();
    myTodoTreeBuilder.setShowPackages(mySettings.arePackagesShown());
    myTodoTreeBuilder.setShowModules(mySettings.areModulesShown());
    myTodoTreeBuilder.setFlattenPackages(mySettings.areFlattenPackages());

    myVisibilityWatcher = new MyVisibilityWatcher();
    myVisibilityWatcher.install(this);
  }

  protected abstract TodoTreeBuilder createTreeBuilder(JTree tree, DefaultTreeModel treeModel, Project project);

  private void initUI() {
    UIUtil.setLineStyleAngled(myTree);
    myTree.setShowsRootHandles(true);
    myTree.setRootVisible(false);
    myTree.setCellRenderer(new TodoCompositeRenderer());
    EditSourceOnDoubleClickHandler.install(myTree);

    DefaultActionGroup group = new DefaultActionGroup();
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE));
    group.addSeparator();
    group.add(ActionManager.getInstance().getAction(IdeActions.GROUP_VERSION_CONTROLS));
    PopupHandler.installPopupHandler(myTree, group, ActionPlaces.TODO_VIEW_POPUP, ActionManager.getInstance());

    myTree.addKeyListener(
      new KeyAdapter() {
        public void keyPressed(KeyEvent e) {
          if (!e.isConsumed() && KeyEvent.VK_ENTER == e.getKeyCode()) {
            TreePath path = myTree.getSelectionPath();
            if (path == null) {
              return;
            }
            NodeDescriptor desciptor = (NodeDescriptor)((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject();
            if (!(desciptor instanceof TodoItemNode)) {
              return;
            }
            OpenSourceUtil.openSourcesFrom(TodoPanel.this, false);
          }
        }
      }
    );
    add(new JScrollPane(myTree), BorderLayout.CENTER);

    // Create tool bars and register custom shortcuts

    JPanel toolBarPanel = new JPanel(new GridLayout());

    DefaultActionGroup leftGroup = new DefaultActionGroup();
    leftGroup.add(new PreviousOccurenceToolbarAction(myOccurenceNavigator));
    leftGroup.add(new NextOccurenceToolbarAction(myOccurenceNavigator));
    leftGroup.add(CommonActionsFactory.getCommonActionsFactory().createContextHelpAction("find.todoList"));
    toolBarPanel.add(
      ActionManager.getInstance().createActionToolbar(ActionPlaces.TODO_VIEW_TOOLBAR, leftGroup, false).getComponent());

    DefaultActionGroup rightGroup = new DefaultActionGroup();
    ExpandAllToolbarAction expandAllAction = new ExpandAllToolbarAction(myTreeExpander);
    expandAllAction.registerCustomShortcutSet(expandAllAction.getShortcutSet(), myTree);
    rightGroup.add(expandAllAction);

    CollapseAllToolbarAction collapseAllAction = new CollapseAllToolbarAction(myTreeExpander);
    collapseAllAction.registerCustomShortcutSet(collapseAllAction.getShortcutSet(), myTree);
    rightGroup.add(collapseAllAction);

    if (!myCurrentFileMode) {
      MyShowModulesAction showModulesAction = new MyShowModulesAction();
      showModulesAction.registerCustomShortcutSet(
        new CustomShortcutSet(
          KeyStroke.getKeyStroke(KeyEvent.VK_M, SystemInfo.isMac ? KeyEvent.META_MASK : KeyEvent.CTRL_MASK)),
        myTree);
      rightGroup.add(showModulesAction);
      MyShowPackagesAction showPackagesAction = new MyShowPackagesAction();
      showPackagesAction.registerCustomShortcutSet(
        new CustomShortcutSet(
          KeyStroke.getKeyStroke(KeyEvent.VK_P, SystemInfo.isMac ? KeyEvent.META_MASK : KeyEvent.CTRL_MASK)),
        myTree);
      rightGroup.add(showPackagesAction);

      MyFlattenPackagesAction flattenPackagesAction = new MyFlattenPackagesAction();
      flattenPackagesAction.registerCustomShortcutSet(
        new CustomShortcutSet(
          KeyStroke.getKeyStroke(KeyEvent.VK_F, SystemInfo.isMac ? KeyEvent.META_MASK : KeyEvent.CTRL_MASK)),
        myTree);
      rightGroup.add(flattenPackagesAction);
    }

    MyAutoScrollToSourceHandler autoScrollToSourceHandler = new MyAutoScrollToSourceHandler();
    autoScrollToSourceHandler.install(myTree);
    rightGroup.add(autoScrollToSourceHandler.createToggleAction());

    MySetTodoFilterAction setTodoFilterAction = new MySetTodoFilterAction();
    rightGroup.add(setTodoFilterAction);
    toolBarPanel.add(
      ActionManager.getInstance().createActionToolbar(ActionPlaces.TODO_VIEW_TOOLBAR, rightGroup, false).getComponent());

    add(toolBarPanel, BorderLayout.WEST);
  }

  void dispose() {
    Disposer.dispose(myTodoTreeBuilder);
    myVisibilityWatcher.deinstall(this);
    myVisibilityWatcher = null;
    myProject = null;
  }

  void rebuildCache() {
    myTodoTreeBuilder.rebuildCache();
  }

  /**
   * Immediately updates tree.
   */
  void updateTree() {
    myTodoTreeBuilder.updateTree(false);
  }

  /**
   * Updates current filter. If previously set filter was removed then empty filter is set.
   * 
   * @see com.intellij.ide.todo.TodoTreeBuilder#setTodoFilter
   */
  void updateTodoFilter() {
    TodoFilter filter = TodoConfiguration.getInstance().getTodoFilter(mySettings.getTodoFilterName());
    setTodoFilter(filter);
  }

  /**
   * Sets specified <code>TodoFilter</code>. The method also updates window's title.
   * 
   * @see com.intellij.ide.todo.TodoTreeBuilder#setTodoFilter
   */
  private void setTodoFilter(TodoFilter filter) {
    // Clear name of current filter if it was removed from configuration.
    String filterName = filter != null ? filter.getName() : null;
    mySettings.setTodoFilterName(filterName);
    // Update filter
    myTodoTreeBuilder.setTodoFilter(filter);
    // Update content's title
    myContent.setDescription(filterName);
  }

  /**
   * @return list of all selected virtual files.
   */
  @Nullable
  protected PsiFile getSelectedFile() {
    TreePath path = myTree.getSelectionPath();
    if (path == null) {
      return null;
    }
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
    LOG.assertTrue(node != null);
    if(node.getUserObject() == null){
      return null;
    }
    return myTodoTreeBuilder.getFileForNode(node);
  }

  @Nullable
  private PsiElement getSelectedElement() {
    TreePath path = myTree.getSelectionPath();
    if (path == null) {
      return null;
    }
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
    Object userObject = node.getUserObject();
    if (userObject instanceof TodoDirNode) {
      TodoDirNode descriptor = (TodoDirNode)userObject;
      return descriptor.getValue();
    }
    else if (userObject instanceof TodoPackageNode) {
      TodoPackageNode descriptor = (TodoPackageNode)userObject;
      return descriptor.getValue().getPackage();
    }
    else if (userObject instanceof TodoFileNode) {
      TodoFileNode descriptor = (TodoFileNode)userObject;
      return descriptor.getValue();
    }
    else {
      return getSelectedFile();
    }
  }

  public Object getData(String dataId) {
    if (DataConstants.NAVIGATABLE.equals(dataId)) {
      TreePath path = myTree.getSelectionPath();
      if (path == null) {
        return null;
      }
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      NodeDescriptor userObject = (NodeDescriptor)node.getUserObject();
      if (userObject == null) {
        return null;
      }
      Object element = userObject.getElement();
      if (!((element instanceof TodoFileNode) || (element instanceof TodoItemNode))) { // allow user to use F4 only on files an TODOs
        return null;
      }
      TodoItemNode pointer = myTodoTreeBuilder.getFirstPointerForElement(element);
      if (pointer != null) {
        return new OpenFileDescriptor(myProject, pointer.getValue().getTodoItem().getFile().getVirtualFile(),
          pointer.getValue().getRangeMarker().getStartOffset()
        );
      }
      else {
        return null;
      }
    }
    else if (DataConstants.VIRTUAL_FILE.equals(dataId)) {
      final PsiFile file = getSelectedFile();
      return file != null ? file.getVirtualFile() : null;
    }
    else if (DataConstants.PSI_ELEMENT.equals(dataId)) {
      return getSelectedElement();
    }
    else if (DataConstants.VIRTUAL_FILE_ARRAY.equals(dataId)) {
      PsiFile file = getSelectedFile();
      if (file != null) {
        return new VirtualFile[]{file.getVirtualFile()};
      }
      else {
        return ourEmptyArray;
      }
    }
    else if (DataConstantsEx.HELP_ID.equals(dataId)) {
      //noinspection HardCodedStringLiteral
      return "find.todoList";
    }
    return null;
  }

  public OccurenceNavigator.OccurenceInfo goPreviousOccurence() {
    return myOccurenceNavigator.goPreviousOccurence();
  }

  public String getNextOccurenceActionName() {
    return myOccurenceNavigator.getNextOccurenceActionName();
  }

  public OccurenceNavigator.OccurenceInfo goNextOccurence() {
    return myOccurenceNavigator.goNextOccurence();
  }

  public boolean hasNextOccurence() {
    return myOccurenceNavigator.hasNextOccurence();
  }

  public String getPreviousOccurenceActionName() {
    return myOccurenceNavigator.getPreviousOccurenceActionName();
  }

  public boolean hasPreviousOccurence() {
    return myOccurenceNavigator.hasPreviousOccurence();
  }

  private final class MyTreeExpander implements TreeExpander {
    public boolean canCollapse() {
      return true;
    }

    public boolean canExpand() {
      return true;
    }

    public void collapseAll() {
      myTodoTreeBuilder.collapseAll();
    }

    public void expandAll() {
      myTodoTreeBuilder.expandAll();
    }
  }

  /**
   * Provides support for "auto scroll to source" functionnality.
   */
  private final class MyAutoScrollToSourceHandler extends AutoScrollToSourceHandler {
    public MyAutoScrollToSourceHandler() {
      super();
    }

    protected boolean isAutoScrollMode() {
      return mySettings.isAutoScrollToSource();
    }

    protected void setAutoScrollMode(boolean state) {
      mySettings.setAutoScrollToSource(state);
    }
  }

  /**
   * Provides support for "Ctrl+Alt+Up/Down" navigation.
   */
  private final class MyOccurenceNavigator implements OccurenceNavigator {
    public boolean hasNextOccurence() {
      TreePath path = myTree.getSelectionPath();
      if (path == null) {
        return false;
      }
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      NodeDescriptor userObject = (NodeDescriptor)node.getUserObject();
      if (userObject == null) {
        return false;
      }
      Object element = userObject.getElement();
      if (element instanceof TodoItemNode) {
        return myTree.getRowCount() != myTree.getRowForPath(path) + 1;
      }
      else {
        return node.getChildCount() > 0;
      }
    }

    public boolean hasPreviousOccurence() {
      TreePath path = myTree.getSelectionPath();
      if (path == null) {
        return false;
      }
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      NodeDescriptor userObject = (NodeDescriptor)node.getUserObject();
      if (userObject == null) {
        return false;
      }
      return !isFirst(node);
    }

    private boolean isFirst(final TreeNode node) {
      final TreeNode parent = node.getParent();
      if (parent == null) return true;
      if (parent.getIndex(node) != 0) return false;
      return isFirst(parent);
    }

    public OccurenceNavigator.OccurenceInfo goNextOccurence() {
      return goToPointer(getNextPointer());
    }

    public OccurenceNavigator.OccurenceInfo goPreviousOccurence() {
      return goToPointer(getPreviousPointer());
    }

    public String getNextOccurenceActionName() {
      return IdeBundle.message("action.next.todo");
    }

    public String getPreviousOccurenceActionName() {
      return IdeBundle.message("action.previous.todo");
    }

    private OccurenceNavigator.OccurenceInfo goToPointer(TodoItemNode pointer) {
      LOG.assertTrue(pointer != null);
      DefaultMutableTreeNode node = myTodoTreeBuilder.getNodeForElement(pointer);
      if (node == null) {
        myTodoTreeBuilder.buildNodeForElement(pointer);
        node = myTodoTreeBuilder.getNodeForElement(pointer);
        if (node == null) {
          // TODO[vova] it seems that this check isn't required any more bacause it was side effect of SCR#7063
          // TODO[vova] try to remove this check in Aurora
          return null;
        }
      }
      TreeUtil.selectPath(myTree, new TreePath(node.getPath()));
      return new OccurenceInfo(
        new OpenFileDescriptor(myProject, pointer.getValue().getTodoItem().getFile().getVirtualFile(),
                               pointer.getValue().getRangeMarker().getStartOffset()),
        -1,
        -1
      );
    }

    private TodoItemNode getNextPointer() {
      TreePath path = myTree.getSelectionPath();
      if (path == null) {
        return null;
      }
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      NodeDescriptor userObject = (NodeDescriptor)node.getUserObject();
      if (userObject == null) {
        return null;
      }
      Object element = userObject.getElement();
      TodoItemNode pointer;
      if (element instanceof TodoItemNode) {
        pointer = myTodoTreeBuilder.getNextPointer(((TodoItemNode)element));
      }
      else {
        pointer = myTodoTreeBuilder.getFirstPointerForElement(element);
      }
      return pointer;
    }

    private TodoItemNode getPreviousPointer() {
      TreePath path = myTree.getSelectionPath();
      if (path == null) {
        return null;
      }
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      NodeDescriptor userObject = (NodeDescriptor)node.getUserObject();
      if (userObject == null) {
        return null;
      }
      Object element = userObject.getElement();
      TodoItemNode pointer;
      if (element instanceof TodoItemNode) {
        pointer = myTodoTreeBuilder.getPreviousPointer((TodoItemNode)element);
      }
      else {
        Object sibling = myTodoTreeBuilder.getPreviousSibling(element);
        if (sibling == null) {
          return null;
        }
        pointer = myTodoTreeBuilder.getLastPointerForElement(sibling);
      }
      return pointer;
    }
  }

  private final class MyShowPackagesAction extends ToggleAction {
    public MyShowPackagesAction() {
      super(IdeBundle.message("action.group.by.packages"), null, Icons.GROUP_BY_PACKAGES);
    }

    public boolean isSelected(AnActionEvent e) {
      return mySettings.arePackagesShown();
    }

    public void setSelected(AnActionEvent e, boolean state) {
      mySettings.setShownPackages(state);
      myTodoTreeBuilder.setShowPackages(state);
    }
  }

  private final class MyShowModulesAction extends ToggleAction {
    public MyShowModulesAction() {
      super(IdeBundle.message("action.group.by.modules"), null, IconLoader.getIcon("/objectBrowser/showModules.png"));
    }

    public boolean isSelected(AnActionEvent e) {
      return mySettings.areModulesShown();
    }

    public void setSelected(AnActionEvent e, boolean state) {
      mySettings.setShownModules(state);
      myTodoTreeBuilder.setShowModules(state);
    }
  }

  private final class MyFlattenPackagesAction extends ToggleAction {
    public MyFlattenPackagesAction() {
      super(IdeBundle.message("action.flatten.packages"), null, Icons.FLATTEN_PACKAGES_ICON);
    }

    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(mySettings.arePackagesShown());
    }

    public boolean isSelected(AnActionEvent e) {
      return mySettings.areFlattenPackages();
    }

    public void setSelected(AnActionEvent e, boolean state) {
      mySettings.setAreFlattenPackages(state);
      myTodoTreeBuilder.setFlattenPackages(state);
    }
  }

  private final class MySetTodoFilterAction extends AnAction implements CustomComponentAction {
    public MySetTodoFilterAction() {
      super(IdeBundle.message("action.filter.todo.items"), null, IconLoader.getIcon("/ant/filter.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      JComponent button = (JComponent)presentation.getClientProperty("button");
      DefaultActionGroup group = createPopupActionGroup();
      ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TODO_VIEW_TOOLBAR,
                                                                                    group);
      popupMenu.getComponent().show(button, button.getWidth(), 0);
    }

    public JComponent createCustomComponent(Presentation presentation) {
      ActionButton button = new ActionButton(
        this,
        presentation,
        ActionPlaces.TODO_VIEW_TOOLBAR,
        ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
      );
      presentation.putClientProperty("button", button);
      return button;
    }

    private DefaultActionGroup createPopupActionGroup() {
      TodoFilter[] filters = TodoConfiguration.getInstance().getTodoFilters();
      DefaultActionGroup group = new DefaultActionGroup();
      group.add(new TodoFilterApplier(IdeBundle.message("action.todo.show.all"),
                                      IdeBundle.message("action.description.todo.show.all"), null));
      for (int i = 0; i < filters.length; i++) {
        TodoFilter filter = filters[i];
        group.add(new TodoFilterApplier(filter.getName(), null, filter));
      }
      group.addSeparator();
      group.add(
        new AnAction(IdeBundle.message("action.todo.edit.filters"),
                     IdeBundle.message("action.todo.edit.filters"), IconLoader.getIcon("/general/ideOptions.png")) {
          public void actionPerformed(AnActionEvent e) {
            ShowSettingsUtil.getInstance().editConfigurable(myProject, TodoConfigurable.getInstance());
          }
        }
      );
      return group;
    }

    private final class TodoFilterApplier extends ToggleAction {
      private TodoFilter myFilter;

      /**
       * @param text        action's text.
       * @param description action's description.
       * @param filter      filter to be applied. <code>null</code> value means "empty" filter.
       */
      public TodoFilterApplier(String text, String description, TodoFilter filter) {
        super(null, description, null);
        getTemplatePresentation().setText(text, false);
        myFilter = filter;
      }

      public void update(AnActionEvent e) {
        super.update(e);
        if (myFilter != null) {
          e.getPresentation().setEnabled(!myFilter.isEmpty());
        }
      }

      public boolean isSelected(AnActionEvent e) {
        return Comparing.equal(myFilter != null ? myFilter.getName() : null, mySettings.getTodoFilterName());
      }

      public void setSelected(AnActionEvent e, boolean state) {
        if (state) {
          setTodoFilter(myFilter);
        }
      }
    }
  }

  private final class MyVisibilityWatcher extends VisibilityWatcher {
    public void visibilityChanged() {
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      myTodoTreeBuilder.setUpdatable(isShowing());
    }
  }
}