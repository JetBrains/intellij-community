package com.intellij.ide.structureView.newStructureView;

import com.intellij.ide.CopyPasteManagerEx;
import com.intellij.ide.DataManager;
import com.intellij.ide.structureView.*;
import com.intellij.ide.structureView.impl.StructureViewFactoryImpl;
import com.intellij.ide.structureView.impl.StructureViewState;
import com.intellij.ide.structureView.impl.java.KindSorter;
import com.intellij.ide.ui.customization.CustomizableActionsSchemas;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.Disposable;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.*;
import com.intellij.util.Alarm;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.OpenSourceUtil;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class StructureViewComponent extends JPanel implements TreeActionsOwner, DataProvider, StructureView {
  private static Logger LOG = Logger.getInstance("#com.intellij.ide.structureView.newStructureView.StructureViewComponent");

  private AbstractTreeBuilder myAbstractTreeBuilder;

  private FileEditor myFileEditor;
  private final TreeModelWrapper myTreeModelWrapper;

  private StructureViewState myStructureViewState;
  private boolean myAutoscrollFeedback;

  private final Alarm myAutoscrollAlarm = new Alarm();

  private final CopyPasteManagerEx.CopyPasteDelegator myCopyPasteDelegator;
  private final MyAutoScrollToSourceHandler myAutoScrollToSourceHandler;
  private final AutoScrollFromSourceHandler myAutoScrollFromSourceHandler;

  private static final Key<StructureViewState> STRUCTURE_VIEW_STATE_KEY = Key.create("STRUCTURE_VIEW_STATE");
  private final Project myProject;
  private final StructureViewModel myTreeModel;
  private boolean mySortByKind = true;


  public StructureViewComponent(FileEditor editor, StructureViewModel structureViewModel, Project project) {
    super(new BorderLayout());

    myProject = project;
    myFileEditor = editor;
    myTreeModel = structureViewModel;
    myTreeModelWrapper = new TreeModelWrapper(myTreeModel, this);
    SmartTreeStructure treeStructure = new SmartTreeStructure(project, myTreeModelWrapper){
      public void rebuildTree() {
        storeState();
        super.rebuildTree();
        restoreState();
      }
    };
    JTree tree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode(treeStructure.getRootElement())));
    myAbstractTreeBuilder = new StructureTreeBuilder(project, tree,
                                                     (DefaultTreeModel)tree.getModel(),treeStructure,myTreeModelWrapper);
    myAbstractTreeBuilder.updateFromRoot();
    Disposer.register(this, myAbstractTreeBuilder);
    Disposer.register(myAbstractTreeBuilder, new Disposable() {
      public void dispose() {
        storeState();
      }
    });

    add(new JScrollPane(myAbstractTreeBuilder.getTree()), BorderLayout.CENTER);

    myAbstractTreeBuilder.getTree().setCellRenderer(new NodeRenderer());

    myAutoScrollToSourceHandler = new MyAutoScrollToSourceHandler();
    myAutoScrollFromSourceHandler = new MyAutoScrollFromSourceHandler(myProject);

    JComponent toolbarComponent =
    ActionManager.getInstance().createActionToolbar(ActionPlaces.STRUCTURE_VIEW_TOOLBAR,
                                                    createActionGroup(),
                                                    true)
      .getComponent();
    add(toolbarComponent, BorderLayout.NORTH);

    installTree();

    myCopyPasteDelegator = new CopyPasteManagerEx.CopyPasteDelegator(myProject, getTree()) {
      protected PsiElement[] getSelectedElements() {
        return StructureViewComponent.this.getSelectedPsiElements();
      }
    };

  }

  private void installTree() {
    getTree().getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    myAutoScrollToSourceHandler.install(getTree());
    myAutoScrollFromSourceHandler.install();

    TreeToolTipHandler.install(getTree());
    TreeUtil.installActions(getTree());
    new TreeSpeedSearch(getTree());

    addTreeKeyListener();
    addTreeMouseListeners();
    restoreState();
  }

  private PsiElement[] getSelectedPsiElements() {
    return filterPsiElements(getSelectedElements());
  }

  private static PsiElement[] filterPsiElements(Object[] selectedElements) {
    ArrayList<PsiElement> psiElements = new ArrayList<PsiElement>();

    if (selectedElements == null)
    {
      return null;
    }
    for (Object selectedElement : selectedElements) {
      if (selectedElement instanceof PsiElement) {
        psiElements.add((PsiElement)selectedElement);
      }
    }
    return psiElements.toArray(new PsiElement[psiElements.size()]);
  }

  private Object[] getSelectedElements() {
    return convertPathsToValues(getTree().getSelectionPaths());
  }

  private Object[] getSelectedTreeElements() {
    return convertPathsToTreeElements(getTree().getSelectionPaths());
  }


  private static Object[] convertPathsToValues(TreePath[] selectionPaths) {
    if (selectionPaths != null) {
      List<Object> result = new ArrayList<Object>();

      for (TreePath selectionPath : selectionPaths) {
        final Object userObject = ((DefaultMutableTreeNode)selectionPath.getLastPathComponent()).getUserObject();
        if (userObject instanceof AbstractTreeNode) {
          Object value = ((AbstractTreeNode)userObject).getValue();
          if (value instanceof TreeElement) {
            value = ((StructureViewTreeElement)value).getValue();
          }
          result.add(value);
        }
      }
      return result.toArray(new Object[result.size()]);
    }
    else {
      return null;
    }
  }

  private static Object[] convertPathsToTreeElements(TreePath[] selectionPaths) {
    if (selectionPaths != null) {
      Object[] result = new Object[selectionPaths.length];

      for (int i = 0; i < selectionPaths.length; i++) {
        result[i] = ((AbstractTreeNode)((DefaultMutableTreeNode)selectionPaths[i].getLastPathComponent()).getUserObject()).getValue();
      }
      return result;
    }
    else {
      return null;
    }
  }

  private void addTreeMouseListeners() {
    EditSourceOnDoubleClickHandler.install(getTree());
    ActionGroup group = (ActionGroup)CustomizableActionsSchemas.getInstance().getCorrectedAction(IdeActions.GROUP_STRUCTURE_VIEW_POPUP);
    PopupHandler.installPopupHandler(getTree(), group, ActionPlaces.STRUCTURE_VIEW_POPUP, ActionManager.getInstance());
  }

  private void addTreeKeyListener() {
    getTree().addKeyListener(
        new KeyAdapter() {
        public void keyPressed(KeyEvent e) {
          if (KeyEvent.VK_ENTER == e.getKeyCode()) {
            DataContext dataContext = DataManager.getInstance().getDataContext(getTree());
            OpenSourceUtil.openSourcesFrom(dataContext, false);
          }
          else if (KeyEvent.VK_ESCAPE == e.getKeyCode()) {
            if (e.isConsumed())
            {
              return;
            }
            CopyPasteManagerEx copyPasteManager = (CopyPasteManagerEx)CopyPasteManager.getInstance();
            boolean[] isCopied = new boolean[1];
            if (copyPasteManager.getElements(isCopied) != null && !isCopied[0]) {
              copyPasteManager.clear();
              e.consume();
            }
          }
        }
      });
  }

  public void storeState() {
    myStructureViewState = getState();
    myFileEditor.putUserData(STRUCTURE_VIEW_STATE_KEY, getState());
  }

  public StructureViewState getState() {
    LOG.assertTrue(getTree() != null);
    StructureViewState structureViewState = new StructureViewState();
    structureViewState.setExpandedElements(getExpandedElements());
    structureViewState.setSelectedElements(getSelectedElements());
    return structureViewState;
  }

  private Object[] getExpandedElements() {
    final JTree tree = getTree();
    LOG.assertTrue(tree != null);
    final List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(tree);
    return convertPathsToValues(expandedPaths.toArray(new TreePath[expandedPaths.size()]));
  }


  public void restoreState() {
    myStructureViewState = myFileEditor.getUserData(STRUCTURE_VIEW_STATE_KEY);
    if (myStructureViewState == null) {
      TreeUtil.expand(getTree(), 2);
    }
    else {
      expandStoredElements();
      selectStoredElenents();
      myFileEditor.putUserData(STRUCTURE_VIEW_STATE_KEY, null);
      myStructureViewState = null;
    }
  }

  private void selectStoredElenents() {
    Object[] selectedPsiElements = null;

    if (myStructureViewState != null) {
      selectedPsiElements = myStructureViewState.getSelectedElements();
    }

    if (selectedPsiElements == null) {
      getTree().setSelectionPath(new TreePath(getRootNode().getPath()));
    }
    else {
      for (Object element : selectedPsiElements) {
        if (element instanceof PsiElement && !((PsiElement)element).isValid()) {
          continue;
        }
        addSelectionPathTo(element);
      }
    }
  }

  public void addSelectionPathTo(final Object element) {
    DefaultMutableTreeNode node = myAbstractTreeBuilder.getNodeForElement(element);
    if (node != null) {
      getTree().addSelectionPath(new TreePath(node.getPath()));
    }
  }

  private DefaultMutableTreeNode getRootNode() {
    return (DefaultMutableTreeNode)getTree().getModel().getRoot();
  }

  private void expandStoredElements() {
    Object[] expandedPsiElements = null;

    if (myStructureViewState != null) {
      expandedPsiElements = myStructureViewState.getExpandedElements();
    }

    if (expandedPsiElements == null) {
      getTree().expandPath(new TreePath(getRootNode().getPath()));
    }
    else {
      for (Object element : expandedPsiElements) {
        if (element instanceof PsiElement && !((PsiElement)element).isValid()) {
          continue;
        }
        expandPathToElement(element);
      }
    }
  }

  protected ActionGroup createActionGroup() {
    DefaultActionGroup result = new DefaultActionGroup();
    Sorter[] sorters = myTreeModel.getSorters();
    for (final Sorter sorter : sorters) {
      if (shouldBeShown(sorter)) {
        result.add(new TreeActionWrapper(sorter, this));
      }
    }
    if (sorters.length > 0)
    {
      result.addSeparator();
    }

    Grouper[] groupers = myTreeModel.getGroupers();
    for (Grouper grouper : groupers) {
      result.add(new TreeActionWrapper(grouper, this));
    }
    Filter[] filters = myTreeModel.getFilters();
    for (Filter filter : filters) {
      result.add(new TreeActionWrapper(filter, this));
    }

    if (showScrollToFromSourceActions()) {
      result.addSeparator();

      result.add(myAutoScrollToSourceHandler.createToggleAction());
      result.add(myAutoScrollFromSourceHandler.createToggleAction());
    }
    return result;
  }

  protected boolean showScrollToFromSourceActions() {
    return true;
  }

  private static boolean shouldBeShown(Sorter sorter) {
    return !sorter.getName().equals(KindSorter.ID);
  }

  public FileEditor getFileEditor() {
    return myFileEditor;
  }

  public DefaultMutableTreeNode expandPathToElement(Object element) {
    ArrayList<AbstractTreeNode> pathToElement = getPathToElement(element);

    if (pathToElement.isEmpty()) return null;

    JTree tree = myAbstractTreeBuilder.getTree();

    if (pathToElement.size() == 1) {
      return (DefaultMutableTreeNode)tree.getModel().getRoot();
    }

    DefaultMutableTreeNode currentTreeNode = (DefaultMutableTreeNode)tree.getModel().getRoot();
    pathToElement.remove(0);
    DefaultMutableTreeNode result = null;
    while (currentTreeNode != null) {
      AbstractTreeNode topPathElement;
      if (!pathToElement.isEmpty()) {
        topPathElement = pathToElement.get(0);
        pathToElement.remove(0);
      }
      else {
        topPathElement = null;
      }
      TreePath treePath = new TreePath(currentTreeNode.getPath());
      if (!tree.isExpanded(treePath)) {
        tree.expandPath(treePath);
      }
      if (topPathElement != null) {
        currentTreeNode = findInChildren(currentTreeNode, topPathElement);
        result = currentTreeNode;
      }
      else {
        currentTreeNode = null;
      }
    }
    return result;
  }

  public boolean select(Object element, boolean requestFocus) {
    DefaultMutableTreeNode currentTreeNode = expandPathToElement(element);

    if (currentTreeNode != null) {
      TreeUtil.selectInTree(currentTreeNode, requestFocus, getTree());
      myAutoScrollToSourceHandler.setShouldAutoScroll(false);
      TreePath path = new TreePath(currentTreeNode.getPath());
      TreeUtil.showRowCentered(getTree(), getTree().getRowForPath(path), false);
      myAutoScrollToSourceHandler.setShouldAutoScroll(true);
      centerSelectedRow();
    }
    return true;
  }

  private ArrayList<AbstractTreeNode> getPathToElement(Object element) {
    ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    addToPath((AbstractTreeNode)myAbstractTreeBuilder.getTreeStructure().getRootElement(), element, result, new THashSet<Object>());
    return result;
  }

  private boolean addToPath(AbstractTreeNode rootElement, Object element, ArrayList<AbstractTreeNode> result, Collection<Object> processedElements) {

    Object value = rootElement.getValue();
    if (value instanceof TreeElement) {
      value = ((StructureViewTreeElement) value).getValue();
    }
    if (!processedElements.add(value)){
        return false;
    }

    if (Comparing.equal(value, element)){
      result.add(0, rootElement);
      return true;
    }

    Collection<AbstractTreeNode> children = rootElement.getChildren();
    for (AbstractTreeNode child : children) {
      if (addToPath(child, element, result, processedElements)) {
        result.add(0, rootElement);
        return true;
      }
    }

    return false;
  }

  private DefaultMutableTreeNode findInChildren(DefaultMutableTreeNode currentTreeNode, AbstractTreeNode topPathElement) {
    for (int i = 0; i < currentTreeNode.getChildCount(); i++) {
      TreeNode child = currentTreeNode.getChildAt(i);
      if (((DefaultMutableTreeNode)child).getUserObject().equals(topPathElement))
      {
        return (DefaultMutableTreeNode)child;
      }
    }
    return null;
  }

  public void scrollToSelectedElement() {
    if (myAutoscrollFeedback) {
      myAutoscrollFeedback = false;
      return;
    }

    StructureViewFactoryImpl structureViewFactory = (StructureViewFactoryImpl)StructureViewFactoryEx.getInstance(myProject);

    if (!structureViewFactory.AUTOSCROLL_FROM_SOURCE)
    {
      return;
    }

    myAutoscrollAlarm.cancelAllRequests();
    myAutoscrollAlarm.addRequest(
        new Runnable() {
        public void run() {
          if (myAbstractTreeBuilder == null)
          {
            return;
          }
          selectViewableElement();
        }
      }, 1000
    );
  }

  private void selectViewableElement() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    final Object currentEditorElement = myTreeModel.getCurrentEditorElement();
    if (currentEditorElement != null) {
      select(currentEditorElement, false);
    }
  }


  public JComponent getComponent() {
    return this;
  }

  public void dispose() {
    LOG.assertTrue(EventQueue.isDispatchThread(), Thread.currentThread().getName());
    myAbstractTreeBuilder = null;
    // this will also dispose wrapped TreeModel
    myTreeModelWrapper.dispose();
    myFileEditor = null;
    myAutoScrollFromSourceHandler.dispose();
  }

  public void centerSelectedRow() {
    TreePath path = getTree().getSelectionPath();
    if (path == null)
    {
      return;
    }
    myAutoScrollToSourceHandler.setShouldAutoScroll(false);
    TreeUtil.showRowCentered(getTree(), getTree().getRowForPath(path), false);
    myAutoScrollToSourceHandler.setShouldAutoScroll(true);
  }

  public void setActionActive(String name, boolean state) {
    StructureViewFactoryEx.getInstance(myProject).setActiveAction(name, state);
    rebuild();
  }

  public void rebuild() {
    storeState();
    ((SmartTreeStructure)myAbstractTreeBuilder.getTreeStructure()).rebuildTree();
    myAbstractTreeBuilder.updateFromRoot();
    restoreState();
  }

  public boolean isActionActive(String name) {
    if (KindSorter.ID.equals(name)) {
      return mySortByKind;
    }
    else {
      return StructureViewFactoryEx.getInstance(myProject).isActionActive(name);
    }
  }

  public AbstractTreeStructure getTreeStructure() {
    return myAbstractTreeBuilder.getTreeStructure();
  }

  public @Nullable JTree getTree() {
    return myAbstractTreeBuilder == null ? null : myAbstractTreeBuilder.getTree();
  }

  public void setKindSortingIsActive(boolean state) {
    mySortByKind = state;
    rebuild();
 }

  private final class MyAutoScrollToSourceHandler extends AutoScrollToSourceHandler {
    private boolean myShouldAutoScroll = true;

    public void setShouldAutoScroll(boolean shouldAutoScroll) {
      myShouldAutoScroll = shouldAutoScroll;
    }

    protected boolean isAutoScrollMode() {
      return myShouldAutoScroll && ((StructureViewFactoryImpl)StructureViewFactoryEx.getInstance(myProject)).AUTOSCROLL_MODE;
    }

    protected void setAutoScrollMode(boolean state) {
      ((StructureViewFactoryImpl)StructureViewFactoryEx.getInstance(myProject)).AUTOSCROLL_MODE = state;
    }

    protected void scrollToSource(Component tree) {
      if (myAbstractTreeBuilder == null) return;
      myAutoscrollFeedback = true;

      Navigatable editSourceDescriptor = (Navigatable)DataManager.getInstance().getDataContext(getTree())
        .getData(DataConstants.NAVIGATABLE);
      if (myFileEditor != null && editSourceDescriptor != null && editSourceDescriptor.canNavigateToSource()) {
        editSourceDescriptor.navigate(false);
      }
    }
  }

  private class MyAutoScrollFromSourceHandler extends AutoScrollFromSourceHandler {
    private FileEditorPositionListener myFileEditorPositionListener;

    private MyAutoScrollFromSourceHandler(Project project) {
      super(project);
    }

    public void install() {
      addEditorCaretListener();
    }

    public void dispose() {
      myTreeModel.removeEditorPositionListener(myFileEditorPositionListener);
    }

    private void addEditorCaretListener() {
      myFileEditorPositionListener = new FileEditorPositionListener() {
        public void onCurrentElementChanged() {
          scrollToSelectedElement();
        }
      };
      myTreeModel.addEditorPositionListener(myFileEditorPositionListener);
    }

    protected boolean isAutoScrollMode() {
      StructureViewFactoryImpl structureViewFactory = (StructureViewFactoryImpl)StructureViewFactoryEx.getInstance(myProject);
      return structureViewFactory.AUTOSCROLL_FROM_SOURCE;
    }

    protected void setAutoScrollMode(boolean state) {
      StructureViewFactoryImpl structureViewFactory = (StructureViewFactoryImpl)StructureViewFactoryEx.getInstance(myProject);
      structureViewFactory.AUTOSCROLL_FROM_SOURCE = state;
      final FileEditor[] selectedEditors = FileEditorManager.getInstance(myProject).getSelectedEditors();
      if (selectedEditors != null && selectedEditors.length > 0) {
        if (state)
        {
          scrollToSelectedElement();
        }
      }
    }
  }

  public Object getData(String dataId) {
    if (DataConstants.PSI_ELEMENT.equals(dataId)) {
      TreePath path = getSelectedUniquePath();
      if (path == null) return null;
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      AbstractTreeNode descriptor = (AbstractTreeNode)node.getUserObject();
      Object element = descriptor.getValue();
      if (element instanceof StructureViewTreeElement) {
        element = ((StructureViewTreeElement)element).getValue();
      }
      if (!(element instanceof PsiElement)) return null;
      if (!((PsiElement)element).isValid()) return null;
      return element;
    }
    if (DataConstantsEx.PSI_ELEMENT_ARRAY.equals(dataId)) {
      return convertToPsiElementsArray(getSelectedElements());
    }
    if (DataConstants.FILE_EDITOR.equals(dataId)) {
      return myFileEditor;
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
    if (DataConstantsEx.NAVIGATABLE.equals(dataId)) {
      Object[] selectedElements = getSelectedTreeElements();
      if (selectedElements == null || selectedElements.length == 0) return null;
      if (selectedElements[0] instanceof Navigatable) {
        return selectedElements[0];
      }
    }
    return null;
  }

  private static PsiElement[] convertToPsiElementsArray(final Object[] selectedElements) {
    if (selectedElements == null) return null;
    ArrayList<PsiElement> psiElements = new ArrayList<PsiElement>();
    for (Object selectedElement : selectedElements) {
      if (selectedElement instanceof PsiElement && ((PsiElement)selectedElement).isValid()) {
        psiElements.add((PsiElement)selectedElement);
      }
    }
    return psiElements.toArray(new PsiElement[psiElements.size()]);
  }

  private TreePath getSelectedUniquePath() {
    JTree tree = getTree();
    if (tree == null) return null;
    TreePath[] paths = tree.getSelectionPaths();
    return paths == null || paths.length != 1 ? null : paths[0];
  }

  public StructureViewModel getTreeModel() {
    return myTreeModel;
  }

  public boolean navigateToSelectedElement(boolean requestFocus) {
    return select(myTreeModel.getCurrentEditorElement(), requestFocus);
  }
}
