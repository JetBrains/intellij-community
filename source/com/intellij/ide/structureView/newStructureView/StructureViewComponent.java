package com.intellij.ide.structureView.newStructureView;

import com.intellij.ide.CopyPasteManagerEx;
import com.intellij.ide.DataManager;
import com.intellij.ide.structureView.StructureViewFactory;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.StructureViewFactoryImpl;
import com.intellij.ide.structureView.impl.StructureViewState;
import com.intellij.ide.structureView.impl.java.KindSorter;
import com.intellij.ide.util.treeView.*;
import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.*;
import com.intellij.util.Alarm;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

public class StructureViewComponent extends JPanel implements TreeActionsOwner, DataProvider {
  private static Logger LOG = Logger.getInstance("#com.intellij.ide.structureView.newStructureView.StructureViewComponent");

  private AbstractTreeBuilder myAbstractTreeBuilder;
  private final Collection<String> myActiveActions = new HashSet<String>();
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


  public StructureViewComponent(FileEditor editor, StructureViewModel structureViewModel, Project project) {
    super(new BorderLayout());
    myProject = project;
    myFileEditor = editor;
    myTreeModel = structureViewModel;
    myTreeModelWrapper = new TreeModelWrapper(myTreeModel, this);
    SmartTreeStructure treeStructure = new SmartTreeStructure(project, myTreeModelWrapper);
    JTree tree = new JTree(new DefaultTreeModel(new DefaultMutableTreeNode(treeStructure.getRootElement())));
    myAbstractTreeBuilder = new AbstractTreeBuilder(tree,
                                                    (DefaultTreeModel)tree.getModel(),
                                                    treeStructure, null) {
      protected boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
        return ((AbstractTreeNode)nodeDescriptor).isAlwaysShowPlus();
      }

      protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
        return ((AbstractTreeNode)nodeDescriptor).isAlwaysExpand();
      }
    };
    myAbstractTreeBuilder.updateFromRoot();
    add(new JScrollPane(myAbstractTreeBuilder.getTree()), BorderLayout.CENTER);

    myAbstractTreeBuilder.getTree().setCellRenderer(new NodeRenderer());

    myAutoScrollToSourceHandler = new MyAutoScrollToSourceHandler(myProject);
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

    restoreStructureViewState();

  }

  private PsiElement[] getSelectedPsiElements() {
    return filterPsiElements(getSelectedElements());

  }

  private PsiElement[] filterPsiElements(Object[] selectedElements) {
    ArrayList<PsiElement> psiElements = new ArrayList<PsiElement>();

    if (selectedElements == null) return null;
    for (int i = 0; i < selectedElements.length; i++) {
      Object selectedElement = selectedElements[i];
      if (selectedElement instanceof PsiElement) psiElements.add((PsiElement)selectedElement);
    }
    return psiElements.toArray(new PsiElement[psiElements.size()]);
  }

  private Object[] getSelectedElements() {
    return convertPathsToValues(getTree().getSelectionPaths());
  }

  private Object[] convertPathsToValues(TreePath[] selectionPaths) {
    if (selectionPaths != null) {
      Object[] result = new Object[selectionPaths.length];

      for (int i = 0; i < selectionPaths.length; i++) {
        TreePath selectionPath = selectionPaths[i];
        Object value = ((AbstractTreeNode)((DefaultMutableTreeNode)selectionPath.getLastPathComponent()).getUserObject()).getValue();
        if (value instanceof TreeElement) {
          value = ((StructureViewTreeElement)value).getValue();
        }
        result[i] = value;
      }
      return result;
    }
    else {
      return null;
    }
  }

  private void addTreeMouseListeners() {
    EditSourceOnDoubleClickHandler.install(getTree());
    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_STRUCTURE_VIEW_POPUP);
    PopupHandler.installPopupHandler(getTree(), group, ActionPlaces.STRUCTURE_VIEW_POPUP, ActionManager.getInstance());
  }

  private void addTreeKeyListener() {
    getTree().addKeyListener(
        new KeyAdapter() {
        public void keyPressed(KeyEvent e) {
          if (KeyEvent.VK_ENTER == e.getKeyCode()) {
            DataContext dataContext = DataManager.getInstance().getDataContext(getTree());
            Navigatable navigatable = (Navigatable)dataContext.getData(DataConstants.NAVIGATABLE);
            if (navigatable != null) {
              navigatable.navigate(false);
            }
          }
          else if (KeyEvent.VK_ESCAPE == e.getKeyCode()) {
            if (e.isConsumed()) return;
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

  public void saveStructureViewState() {
    myStructureViewState = new StructureViewState();
    myStructureViewState.setExpandedElements(getExpandedPsiElements());
    myStructureViewState.setSelectedElements(getSelectedPsiElements());
    myFileEditor.putUserData(STRUCTURE_VIEW_STATE_KEY, myStructureViewState);
  }

  private Object[] getExpandedPsiElements() {
    ArrayList<TreePath> paths = new ArrayList<TreePath>();
    TreeUtil.collectExpandedPaths(getTree(), paths);
    return filterPsiElements(convertPathsToValues(paths.toArray(new TreePath[paths.size()])));
  }


  public void restoreStructureViewState() {
    myStructureViewState = myFileEditor.getUserData(STRUCTURE_VIEW_STATE_KEY);
    if (myStructureViewState != null) {
      expandStoredElements();
      selectStoredElenents();
      myFileEditor.putUserData(STRUCTURE_VIEW_STATE_KEY, null);
      myStructureViewState = null;
    }
    else {
      TreeUtil.expand(getTree(), 3);
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
      for (int i = 0; i < selectedPsiElements.length; i++) {
        Object element = selectedPsiElements[i];
        if (element instanceof PsiElement && !((PsiElement)element).isValid()) continue;
        DefaultMutableTreeNode node = myAbstractTreeBuilder.getNodeForElement(element);
        if (node != null) {
          getTree().addSelectionPath(new TreePath(node.getPath()));
        }
      }
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
      for (int i = 0; i < expandedPsiElements.length; i++) {
        Object element = expandedPsiElements[i];
        if (element instanceof PsiElement && !((PsiElement)element).isValid()) continue;
        expandPathToElement(element);
      }
    }
  }

  private ActionGroup createActionGroup() {
    DefaultActionGroup result = new DefaultActionGroup();
    Sorter[] sorters = myTreeModel.getSorters();
    for (int i = 0; i < sorters.length; i++) {
      final Sorter sorter = sorters[i];
      if (shouldBeShown(sorter)) {
        result.add(new TreeActionWrapper(sorter, this));
      }
    }
    if (sorters.length > 0) result.addSeparator();

    Grouper[] groupers = myTreeModel.getGroupers();
    for (int i = 0; i < groupers.length; i++) {
      result.add(new TreeActionWrapper(groupers[i], this));
    }
    Filter[] filters = myTreeModel.getFilters();
    for (int i = 0; i < filters.length; i++) {
      result.add(new TreeActionWrapper(filters[i], this));
    }

    result.addSeparator();

    result.add(myAutoScrollToSourceHandler.createToggleAction());
    result.add(myAutoScrollFromSourceHandler.createToggleAction());


    return result;
  }

  private boolean shouldBeShown(final Sorter sorter) {
    return !sorter.getName().equals(KindSorter.ID);
  }

  public FileEditor getFileEditor() {
    return null;
  }

  private DefaultMutableTreeNode expandPathToElement(Object element) {
    ArrayList<AbstractTreeNode> pathToElement = getPathToElement(element);

    if (pathToElement.isEmpty()) return null;

    JTree tree = myAbstractTreeBuilder.getTree();
    DefaultMutableTreeNode currentTreeNode = ((DefaultMutableTreeNode)tree.getModel().getRoot());
    pathToElement.remove(0);
    while (!pathToElement.isEmpty() && currentTreeNode != null) {
      AbstractTreeNode topPathElement = pathToElement.get(0);
      pathToElement.remove(0);
      TreePath treePath = new TreePath(currentTreeNode.getPath());
      if (!tree.isExpanded(treePath)) tree.expandPath(treePath);
      currentTreeNode = findInChildren(currentTreeNode, topPathElement);
    }
    return currentTreeNode;
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
    addToPath((AbstractTreeNode)myAbstractTreeBuilder.getTreeStructure().getRootElement(), element, result, new HashSet());
    return result;
  }

  private boolean addToPath(AbstractTreeNode rootElement, Object element, ArrayList<AbstractTreeNode> result, Collection processedElements) {

    final Object rootValue = rootElement.getValue();
    if (rootValue instanceof TreeElement) {
      Object value = ((StructureViewTreeElement) rootValue).getValue();
      if (processedElements.contains(value)){
          return false;
      }
      else {
          processedElements.add(value);
      }
        
      if (Comparing.equal(value, element)){
        result.add(0, rootElement);
        return true;
      }
    }

    Collection<AbstractTreeNode> children = rootElement.getChildren();
    for (Iterator<AbstractTreeNode> iterator = children.iterator(); iterator.hasNext();) {
      AbstractTreeNode child = iterator.next();
      if (addToPath(child, element, result, new HashSet())) {
        result.add(0, rootElement);
        return true;
      }
    }

    return false;
  }

  private DefaultMutableTreeNode findInChildren(DefaultMutableTreeNode currentTreeNode, AbstractTreeNode topPathElement) {
    for (int i = 0; i < currentTreeNode.getChildCount(); i++) {
      TreeNode child = currentTreeNode.getChildAt(i);
      if (((DefaultMutableTreeNode)child).getUserObject().equals(topPathElement)) return (DefaultMutableTreeNode)child;
    }
    return null;
  }

  public void scrollToElementAtCaret(final FileEditor editor) {
    if (myAutoscrollFeedback) {
      myAutoscrollFeedback = false;
      return;
    }

    if (myFileEditor == null || !Comparing.equal(myFileEditor, editor)) return;

    StructureViewFactoryImpl structureViewFactory
    = (StructureViewFactoryImpl)StructureViewFactory.getInstance(myProject);

    if (!structureViewFactory.AUTOSCROLL_FROM_SOURCE) return;

    myAutoscrollAlarm.cancelAllRequests();
    myAutoscrollAlarm.addRequest(
        new Runnable() {
        public void run() {
          if (myAbstractTreeBuilder == null) return;
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

    saveStructureViewState();

    if (myAbstractTreeBuilder != null) {
      myAbstractTreeBuilder.dispose();
      myAbstractTreeBuilder = null;
    }

    myFileEditor = null;
    myAutoScrollFromSourceHandler.dispose();

  }

  public void centerSelectedRow() {
    TreePath path = getTree().getSelectionPath();
    if (path == null) return;
    myAutoScrollToSourceHandler.setShouldAutoScroll(false);
    TreeUtil.showRowCentered(getTree(), getTree().getRowForPath(path), false);
    myAutoScrollToSourceHandler.setShouldAutoScroll(true);
  }

  public void setActionActive(String name, boolean state) {
    saveStructureViewState();
    if (state) {
      myActiveActions.add(name);
    }
    else {
      myActiveActions.remove(name);
    }
      ((SmartTreeStructure)myAbstractTreeBuilder.getTreeStructure()).rebuildTree();
    myAbstractTreeBuilder.updateFromRoot();
    restoreStructureViewState();
  }

  public boolean isActionActive(String name) {
    return myActiveActions.contains(name);
  }

  public AbstractTreeStructure getTreeStructure() {
    return myAbstractTreeBuilder.getTreeStructure();
  }

  public JTree getTree() {
    return myAbstractTreeBuilder.getTree();
  }

  private final class MyAutoScrollToSourceHandler extends AutoScrollToSourceHandler {
    private boolean myShouldAutoScroll = true;

    public MyAutoScrollToSourceHandler(Project project) {
      super(project);
    }

    public void setShouldAutoScroll(boolean shouldAutoScroll) {
      myShouldAutoScroll = shouldAutoScroll;
    }

    protected boolean isAutoScrollMode() {
      return myShouldAutoScroll && ((StructureViewFactoryImpl)StructureViewFactory.getInstance(myProject)).AUTOSCROLL_MODE;
    }

    protected void setAutoScrollMode(boolean state) {
        ((StructureViewFactoryImpl)StructureViewFactory.getInstance(myProject)).AUTOSCROLL_MODE = state;
    }

    protected void scrollToSource(JTree tree) {
      myAutoscrollFeedback = true;

      Navigatable editSourceDescriptor = (Navigatable)DataManager.getInstance().getDataContext(getTree())
        .getData(DataConstants.NAVIGATABLE);
      if (myFileEditor != null && editSourceDescriptor != null && editSourceDescriptor.canNavigate()) {
        editSourceDescriptor.navigate(false);
      }
    }
  }

  private class MyAutoScrollFromSourceHandler extends AutoScrollFromSourceHandler {
    private CaretListener myEditorCaretListener;

    private MyAutoScrollFromSourceHandler(Project project) {
      super(project);
    }

    public void install() {
      addEditorCaretListener();
    }

    public void dispose() {
      EditorFactory.getInstance().getEventMulticaster().removeCaretListener(myEditorCaretListener);
    }

    private void addEditorCaretListener() {
      myEditorCaretListener = new CaretListener() {
        public void caretPositionChanged(final CaretEvent e) {
          Editor editor = e.getEditor();
          FileEditor fileEditor = getFileEditorForEditor(editor);
          scrollToElementAtCaret(fileEditor);
        }

        private FileEditor getFileEditorForEditor(Editor editor) {
          VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
          if (file == null) return null;
          return FileEditorManager.getInstance(myProject).getSelectedEditor(file);
        }
      };
      EditorFactory.getInstance().getEventMulticaster().addCaretListener(myEditorCaretListener);
    }

    protected boolean isAutoScrollMode() {
      StructureViewFactoryImpl structureViewFactory = (StructureViewFactoryImpl)StructureViewFactory.getInstance(myProject);
      return structureViewFactory.AUTOSCROLL_FROM_SOURCE;
    }

    protected void setAutoScrollMode(boolean state) {
      StructureViewFactoryImpl structureViewFactory = (StructureViewFactoryImpl)StructureViewFactory.getInstance(myProject);
      structureViewFactory.AUTOSCROLL_FROM_SOURCE = state;
      Editor selectedTextEditor = FileEditorManager.getInstance(myProject).getSelectedTextEditor();
      VirtualFile file = FileDocumentManager.getInstance().getFile(selectedTextEditor.getDocument());
      if (file != null) {
        if (state) scrollToElementAtCaret(FileEditorManager.getInstance(myProject).getSelectedEditor(file));
      }
    }
  }

  public Object getData(String dataId) {
    if (DataConstants.PSI_ELEMENT.equals(dataId)) {
      TreePath path = getSelectedPath();
      if (path == null) return null;
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      NodeDescriptor descriptor = (NodeDescriptor)node.getUserObject();
      Object element = descriptor.getElement();
      //if (element instanceof PropertyElement) {
      //  PsiElement[] elements = ((PropertyElement)element).getPsiElements();
      //  if (elements[0].isValid()) return elements[0];
      //}
      if (!(element instanceof PsiElement)) return null;
      if (!((PsiElement)element).isValid()) return null;
      return element;
    }
    else if (DataConstantsEx.PSI_ELEMENT_ARRAY.equals(dataId)) {
      return getSelectedElements();
    }
    else if (DataConstants.FILE_EDITOR.equals(dataId)) {
      return myFileEditor;
    }
    else if (DataConstantsEx.CUT_PROVIDER.equals(dataId)) {
      return myCopyPasteDelegator.getCutProvider();
    }
    else if (DataConstantsEx.COPY_PROVIDER.equals(dataId)) {
      return myCopyPasteDelegator.getCopyProvider();
    }
    else if (DataConstantsEx.PASTE_PROVIDER.equals(dataId)) {
      return myCopyPasteDelegator.getPasteProvider();
    }
    else if (DataConstantsEx.NAVIGATABLE.equals(dataId)) {
      Object[] selectedElements = getSelectedElements();
      if (selectedElements == null || selectedElements.length == 0) return null;
      if (selectedElements[0] instanceof Navigatable) return selectedElements[0];
      return null;
    }
    return null;
  }

  private TreePath getSelectedPath() {
    return getTree().getSelectionPath();
  }
}
