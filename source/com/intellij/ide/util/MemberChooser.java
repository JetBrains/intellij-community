package com.intellij.ide.util;

import com.intellij.codeInsight.generation.*;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.TreeToolTipHandler;
import com.intellij.util.Icons;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class MemberChooser<T extends ClassMember> extends DialogWrapper implements TypeSafeDataProvider {
  protected Tree myTree;
  private DefaultTreeModel myTreeModel;
  private JCheckBox myCopyJavadocCheckbox;
  private JCheckBox myInsertOverrideAnnotationCheckbox;

  private ArrayList<MemberNode<T>> mySelectedNodes = new ArrayList<MemberNode<T>>();

  private boolean mySorted = false;
  private boolean myShowClasses = true;
  private boolean myAllowEmptySelection = false;
  private boolean myAllowMultiSelection;
  private final boolean myIsInsertOverrideVisible;

  protected T[] myElements;
  private HashMap<MemberNode,ParentNode> myNodeToParentMap = new HashMap<MemberNode, ParentNode>();
  private HashMap<ClassMember, MemberNode> myElementToNodeMap = new HashMap<ClassMember, MemberNode>();
  private ArrayList<ContainerNode> myContainerNodes = new ArrayList<ContainerNode>();
  private LinkedHashSet<T> mySelectedElements;

  @NonNls private final static String PROP_SORTED = "MemberChooser.sorted";
  @NonNls private final static String PROP_SHOWCLASSES = "MemberChooser.showClasses";
  @NonNls private final static String PROP_COPYJAVADOC = "MemberChooser.copyJavadoc";
  @NonNls private final static String PROP_INSERT_OVERRIDE = "MemberChooser.insertOverride";

  public MemberChooser(T[] elements,
                       boolean allowEmptySelection,
                       boolean allowMultiSelection,
                       Project project) {
    this(elements, allowEmptySelection, allowMultiSelection, project, false);
  }

  public MemberChooser(T[] elements,
                       boolean allowEmptySelection,
                       boolean allowMultiSelection,
                       Project project,
                       boolean isInsertOverrideVisible) {
    super(project, true);
    myElements = elements;
    myAllowEmptySelection = allowEmptySelection;
    myAllowMultiSelection = allowMultiSelection;
    myIsInsertOverrideVisible = isInsertOverrideVisible;
    myTree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode()));
    resetData();
    init();
  }

  protected void resetData() {
    mySelectedNodes.clear();
    myNodeToParentMap.clear();
    myElementToNodeMap.clear();
    myContainerNodes.clear();

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        myTreeModel = buildModel();
      }
    });
    myTree.setModel(myTreeModel);
    myTree.setRootVisible(false);

    TreeUtil.expandAll(myTree);
    myCopyJavadocCheckbox = new JCheckBox(IdeBundle.message("checkbox.copy.javadoc"));
    if (myIsInsertOverrideVisible) {
      myInsertOverrideAnnotationCheckbox = new JCheckBox(IdeBundle.message("checkbox.insert.at.override"));
    }
  }

  /**
   * should be invoked in read action
   */
  private DefaultTreeModel buildModel() {
    final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
    final Ref<Integer> count = new Ref<Integer>(0);
    final FactoryMap<MemberChooserObject,ParentNode> map = new FactoryMap<MemberChooserObject,ParentNode>() {
      protected ParentNode create(final MemberChooserObject key) {
        ParentNode node = null;
        if (key instanceof PsiElementMemberChooserObject) {
            final ContainerNode containerNode = new ContainerNode(rootNode, key, count);
            node = containerNode;
            myContainerNodes.add(containerNode);
        }
        if (node == null) {
          node = new ParentNode(rootNode, key, count);
        }
        return node;
      }
    };

    for (T object : myElements) {
      final ParentNode parentNode = map.get(object.getParentNodeDelegate());
      final MemberNode elementNode = new MemberNode(parentNode, object, count);
      myNodeToParentMap.put(elementNode, parentNode);
      myElementToNodeMap.put(object, elementNode);
    }
    return new DefaultTreeModel(rootNode);
  }

  public void selectElements(ClassMember[] elements) {
    ArrayList<TreePath> selectionPaths = new ArrayList<TreePath>();
    for (ClassMember element : elements) {
      MemberNode treeNode = myElementToNodeMap.get(element);
      if (treeNode != null) {
        selectionPaths.add(new TreePath(treeNode.getPath()));
      }
    }
    myTree.setSelectionPaths(selectionPaths.toArray(new TreePath[selectionPaths.size()]));
  }


  protected Action[] createActions() {
    if (myAllowEmptySelection) {
      return new Action[]{getOKAction(), new SelectNoneAction(), getCancelAction()};
    }
    else {
      return new Action[]{getOKAction(), getCancelAction()};
    }
  }

  protected void doHelpAction() {
  }

  protected JComponent createSouthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    JPanel optionsPanel = new JPanel(new VerticalFlowLayout());
    ActionListener actionListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
              myTree.requestFocus();
            }
          };

    if (myIsInsertOverrideVisible) {
      myInsertOverrideAnnotationCheckbox.setSelected(PropertiesComponent.getInstance().isTrueValue(PROP_INSERT_OVERRIDE));
      myInsertOverrideAnnotationCheckbox.addActionListener(actionListener);
      optionsPanel.add(myInsertOverrideAnnotationCheckbox);
    }

    myCopyJavadocCheckbox.setSelected(PropertiesComponent.getInstance().isTrueValue(PROP_COPYJAVADOC));
    myCopyJavadocCheckbox.addActionListener(actionListener);
    optionsPanel.add(myCopyJavadocCheckbox);



    panel.add(
      optionsPanel,
      new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.NONE,
                             new Insets(0, 0, 0, 5), 0, 0)
    );

    if (myElements == null || myElements.length == 0) {
      setOKActionEnabled(false);
    }
    panel.add(
      super.createSouthPanel(),
      new GridBagConstraints(1, 0, 1, 1, 0, 0, GridBagConstraints.SOUTH, GridBagConstraints.NONE,
                             new Insets(0, 0, 0, 0), 0, 0)
    );
    return panel;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());

    // Toolbar

    DefaultActionGroup group = new DefaultActionGroup();

    SortEmAction sortAction = new SortEmAction();
    sortAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_A, KeyEvent.ALT_MASK)), myTree);
    setSorted(PropertiesComponent.getInstance().isTrueValue(PROP_SORTED));
    group.add(sortAction);

    ShowContainersAction showContainersAction = getShowContainersAction();
    showContainersAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.ALT_MASK)), myTree);
    setShowClasses(PropertiesComponent.getInstance().isTrueValue(PROP_SHOWCLASSES));
    group.add(showContainersAction);

    ExpandAllAction expandAllAction = new ExpandAllAction();
    expandAllAction.registerCustomShortcutSet(
      new CustomShortcutSet(
        KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, SystemInfo.isMac ? KeyEvent.META_MASK : KeyEvent.CTRL_MASK)),
      myTree);
    group.add(expandAllAction);

    CollapseAllAction collapseAllAction = new CollapseAllAction();
    collapseAllAction.registerCustomShortcutSet(
      new CustomShortcutSet(
        KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, SystemInfo.isMac ? KeyEvent.META_MASK : KeyEvent.CTRL_MASK)),
      myTree);
    group.add(collapseAllAction);

    panel.add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true).getComponent(),
              BorderLayout.NORTH);

    // Tree

    myTree.setCellRenderer(new ColoredTreeCellRenderer() {
    public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded,
                                      boolean leaf, int row, boolean hasFocus) {
      if (value instanceof ElementNode) {
        ((ElementNode) value).getDelegate().renderTreeNode(this, tree);
      }
    }
  }
);
    UIUtil.setLineStyleAngled(myTree);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.addKeyListener(new TreeKeyListener());
    myTree.addTreeSelectionListener(new MyTreeSelectionListener());

    if (!myAllowMultiSelection) {
      myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    }

    if ((getRootNode()).getChildCount() > 0) {
      myTree.expandRow(0);
      myTree.setSelectionRow(1);
    }
    TreeUtil.expandAll(myTree);
    new TreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      @Nullable
      public String convert(TreePath path) {
        final MemberChooserObject delegate = ((ElementNode)path.getLastPathComponent()).getDelegate();
        return delegate.getText();
      }
    });
    myTree.addMouseListener(
      new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() == 2) {
            if (myTree.getPathForLocation(e.getX(), e.getY()) != null) {
              doOKAction();
            }
          }
        }
      }
    );
    TreeToolTipHandler.install(myTree);
    TreeUtil.installActions(myTree);
    JScrollPane scrollPane = new JScrollPane(myTree);
    scrollPane.setPreferredSize(new Dimension(350, 450));
    panel.add(scrollPane, BorderLayout.CENTER);

    return panel;
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.ide.util.MemberChooser";
  }

  public JComponent getPreferredFocusedComponent() {
    return myTree;
  }

  @Nullable
  private LinkedHashSet<T> getSelectedElementsList() {
    return getExitCode() != OK_EXIT_CODE ? null : mySelectedElements;
  }

  @Nullable
  public List<T> getSelectedElements() {
    final LinkedHashSet<T> list = getSelectedElementsList();
    return list == null ? null : new ArrayList<T>(list);
  }

  @Nullable
  public T[] getSelectedElements(T[] a) {
    LinkedHashSet<T> list = getSelectedElementsList();
    if (list == null) return null;
    return list.toArray(a);
  }

  protected final boolean areElementsSelected() {
    return mySelectedElements != null && mySelectedElements.size() > 0;
  }

  public void setCopyJavadocVisible(boolean state) {
    myCopyJavadocCheckbox.setVisible(state);
  }

  public boolean isCopyJavadoc() {
    return myCopyJavadocCheckbox.isSelected();
  }

  public boolean isInsertOverrideAnnotation () {
    return myIsInsertOverrideVisible && myInsertOverrideAnnotationCheckbox.isSelected();
  }

  private boolean isSorted() {
    return mySorted;
  }

  private void setSorted(boolean sorted) {
    if (mySorted == sorted) return;
    mySorted = sorted;

    Pair<ElementNode,List<ElementNode>> pair = storeSelection();

    Enumeration<ParentNode<T>> children = getRootNodeChildren();
    while (children.hasMoreElements()) {
      ParentNode<T> classNode = children.nextElement();
      sortNode(classNode, sorted);
      myTreeModel.nodeStructureChanged(classNode);
    }

    restoreSelection(pair);
  }

  private void sortNode(ParentNode<T> node, boolean sorted) {
    ArrayList<MemberNode<T>> arrayList = new ArrayList<MemberNode<T>>();
    Enumeration<MemberNode<T>> children = node.children();
    while (children.hasMoreElements()) {
      arrayList.add(children.nextElement());
    }

    Collections.sort(arrayList, sorted ? new AlphaComparator() : new OrderComparator());

    replaceChildren(node, arrayList);
  }

  private static void replaceChildren(final DefaultMutableTreeNode node, final Collection<? extends ElementNode> arrayList) {
    node.removeAllChildren();
    for (ElementNode child : arrayList) {
      node.add(child);
    }
  }

  private void setShowClasses(boolean showClasses) {
    myShowClasses = showClasses;

    Pair<ElementNode,List<ElementNode>> selection = storeSelection();

    DefaultMutableTreeNode root = getRootNode();
    if (!myShowClasses || myContainerNodes.size() == 0) {
      List<ParentNode> otherObjects = new ArrayList<ParentNode>();
      Enumeration<ParentNode<T>> children = getRootNodeChildren();
      ParentNode<T> newRoot = new ParentNode<T>(null, new MemberChooserObjectBase(getAllContainersNodeName()), new Ref<Integer>(0));
      while (children.hasMoreElements()) {
        final ParentNode nextElement = children.nextElement();
        if (nextElement instanceof ContainerNode) {
          final ContainerNode<T> containerNode = (ContainerNode<T>)nextElement;
          Enumeration<MemberNode<T>> memberNodes = containerNode.children();
          List<MemberNode<T>> memberNodesList = new ArrayList<MemberNode<T>>();
          while (memberNodes.hasMoreElements()) {
            memberNodesList.add(memberNodes.nextElement());
          }
          for (MemberNode<T> memberNode : memberNodesList) {
            newRoot.add(memberNode);
          }
        } else {
          otherObjects.add(nextElement);
        }
      }
      replaceChildren(root, otherObjects);
      sortNode(newRoot, mySorted);
      if (newRoot.children().hasMoreElements()) root.add(newRoot);
    }
    else {
      Enumeration<ParentNode<T>> children = getRootNodeChildren();
      if (children.hasMoreElements()) {
        ParentNode<T> allClassesNode = children.nextElement();
        Enumeration<MemberNode<T>> memberNodes = allClassesNode.children();
        ArrayList<MemberNode> arrayList = new ArrayList<MemberNode>();
        while (memberNodes.hasMoreElements()) {
          arrayList.add(memberNodes.nextElement());
        }
        for (MemberNode memberNode : arrayList) {
          myNodeToParentMap.get(memberNode).add(memberNode);
        }
      }
      replaceChildren(root, myContainerNodes);
    }
    myTreeModel.nodeStructureChanged(root);

    TreeUtil.expandAll(myTree);

    restoreSelection(selection);
  }

  protected String getAllContainersNodeName() {
    return IdeBundle.message("node.memberchooser.all.classes");
  }

  private Enumeration<ParentNode<T>> getRootNodeChildren() {
    return getRootNode().children();
  }

  private DefaultMutableTreeNode getRootNode() {
    return (DefaultMutableTreeNode)myTreeModel.getRoot();
  }

  private Pair<ElementNode,List<ElementNode>> storeSelection() {
    List<ElementNode> selectedNodes = new ArrayList<ElementNode>();
    TreePath[] paths = myTree.getSelectionPaths();
    if (paths != null) {
      for (TreePath path : paths) {
        selectedNodes.add((ElementNode)path.getLastPathComponent());
      }
    }
    TreePath leadSelectionPath = myTree.getLeadSelectionPath();
    return Pair.create(leadSelectionPath != null ? (ElementNode)leadSelectionPath.getLastPathComponent() : null, selectedNodes);
  }


  private void restoreSelection(Pair<ElementNode,List<ElementNode>> pair) {
    List<ElementNode> selectedNodes = pair.second;

    DefaultMutableTreeNode root = getRootNode();

    ArrayList<TreePath> toSelect = new ArrayList<TreePath>();
    for (ElementNode node : selectedNodes) {
      if (root.isNodeDescendant(node)) {
        toSelect.add(new TreePath(node.getPath()));
      }
    }

    if (toSelect.size() > 0) {
      myTree.setSelectionPaths(toSelect.toArray(new TreePath[toSelect.size()]));
    }

    ElementNode leadNode = pair.first;
    if (leadNode != null) {
      myTree.setLeadSelectionPath(new TreePath(leadNode.getPath()));
    }
  }

  public void dispose() {
    PropertiesComponent instance = PropertiesComponent.getInstance();
    instance.setValue(PROP_SORTED, Boolean.toString(isSorted()));
    instance.setValue(PROP_SHOWCLASSES, Boolean.toString(myShowClasses));
    instance.setValue(PROP_COPYJAVADOC, Boolean.toString(myCopyJavadocCheckbox.isSelected()));
    if (myIsInsertOverrideVisible) {
      instance.setValue(PROP_INSERT_OVERRIDE, Boolean.toString(myInsertOverrideAnnotationCheckbox.isSelected()));
    }

    getContentPane().removeAll();
    mySelectedNodes.clear();
    myElements = null;
    super.dispose();
  }

  public void calcData(final DataKey key, final DataSink sink) {
    if (key.equals(DataKeys.PSI_ELEMENT)) {
      if (mySelectedElements != null && !mySelectedElements.isEmpty()) {
        T selectedElement = mySelectedElements.iterator().next();
        if (selectedElement instanceof PsiElementClassMember) {
          sink.put(DataKeys.PSI_ELEMENT, ((PsiElementClassMember) selectedElement).getElement());
        }
      }
    }
  }

  private class MyTreeSelectionListener implements TreeSelectionListener {
    public void valueChanged(TreeSelectionEvent e) {
      TreePath[] paths = e.getPaths();
      if (paths == null) return;
      for (int i = 0; i < paths.length; i++) {
        Object node = paths[i].getLastPathComponent();
        if (node instanceof MemberNode) {
          final MemberNode<T> memberNode = (MemberNode<T>)node;
          if (e.isAddedPath(i)) {
            if (!mySelectedNodes.contains(memberNode)) {
              mySelectedNodes.add(memberNode);
            }
          }
          else {
            mySelectedNodes.remove(memberNode);
          }
        }
      }
      mySelectedElements = new LinkedHashSet<T>();
      for (MemberNode<T> selectedNode : mySelectedNodes) {
        mySelectedElements.add(selectedNode.getDelegate());
      }
    }
  }

  private abstract static class ElementNode extends DefaultMutableTreeNode {
    private int myOrder;
    private final MemberChooserObject myDelegate;

    public ElementNode(@Nullable DefaultMutableTreeNode parent, MemberChooserObject delegate, Ref<Integer> order) {
      myOrder = order.get();
      order.set(myOrder + 1);
      myDelegate = delegate;
      if (parent != null) {
        parent.add(this);
      }
    }

    public MemberChooserObject getDelegate() {
      return myDelegate;
    }

    public int getOrder() {
      return myOrder;
    }
  }

  private static class MemberNode<T extends ClassMember> extends ElementNode {

    public MemberNode(ParentNode parent, ClassMember delegate, Ref<Integer> order) {
      super(parent, delegate, order);
    }

    public T getDelegate() {
      return (T)super.getDelegate();
    }
  }

  private static class ParentNode<T extends ClassMember> extends ElementNode {
    public ParentNode(@Nullable DefaultMutableTreeNode parent, MemberChooserObject delegate, Ref<Integer> order) {
      super(parent, delegate, order);
    }

    public Enumeration<MemberNode<T>> children() {
      return super.children();
    }
  }

  private static class ContainerNode<T extends ClassMember> extends ParentNode<T> {
    public ContainerNode(DefaultMutableTreeNode parent, MemberChooserObject delegate, Ref<Integer> order) {
      super(parent, delegate, order);
    }
  }

  private class SelectNoneAction extends AbstractAction {
    public SelectNoneAction() {
      super(IdeBundle.message("action.select.none"));
    }

    public void actionPerformed(ActionEvent e) {
      myTree.clearSelection();
      doOKAction();
    }
  }

  private class TreeKeyListener extends KeyAdapter {
    public void keyPressed(KeyEvent e) {
      TreePath path = myTree.getLeadSelectionPath();
      if (path == null) return;
      final Object lastComponent = path.getLastPathComponent();
      if (e.getKeyCode() == KeyEvent.VK_ENTER) {
        if (lastComponent instanceof ParentNode) return;
        doOKAction();
        e.consume();
      }
      else if (e.getKeyCode() == KeyEvent.VK_INSERT) {
        if (lastComponent instanceof ElementNode) {
          final ElementNode node = (ElementNode)lastComponent;
          if (!mySelectedNodes.contains(node)) {
            if (node.getNextNode() != null) {
              myTree.setSelectionPath(new TreePath(node.getNextNode().getPath()));
            }
          }
          else {
            if (node.getNextNode() != null) {
              myTree.removeSelectionPath(new TreePath(node.getPath()));
              myTree.setSelectionPath(new TreePath(node.getNextNode().getPath()));
              myTree.repaint();
            }
          }
          e.consume();
        }
      }
    }
  }

  private class SortEmAction extends ToggleAction {
    public SortEmAction() {
      super(IdeBundle.message("action.sort.alphabetically"),
            IdeBundle.message("action.sort.alphabetically"), IconLoader.getIcon("/objectBrowser/sorted.png"));
    }

    public boolean isSelected(AnActionEvent event) {
      return isSorted();
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      setSorted(flag);
    }
  }

  protected ShowContainersAction getShowContainersAction() {
    return new ShowContainersAction(IdeBundle.message("action.show.classes"),  Icons.CLASS_ICON);
  }

  protected class ShowContainersAction extends ToggleAction {
    public ShowContainersAction(final String text, final Icon icon) {
      super(text, text, icon);
    }

    public boolean isSelected(AnActionEvent event) {
      return myShowClasses;
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      setShowClasses(flag);
    }

    public void update(AnActionEvent e) {
      super.update(e);
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(myContainerNodes.size() > 1);
    }
  }

  private class ExpandAllAction extends AnAction {
    public ExpandAllAction() {
      super(IdeBundle.message("action.expand.all"), IdeBundle.message("action.expand.all"),
            IconLoader.getIcon("/actions/expandall.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      TreeUtil.expandAll(myTree);
    }
  }

  private class CollapseAllAction extends AnAction {
    public CollapseAllAction() {
      super(IdeBundle.message("action.collapse.all"), IdeBundle.message("action.collapse.all"),
            IconLoader.getIcon("/actions/collapseall.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      TreeUtil.collapseAll(myTree, 1);
    }
  }

  private static class AlphaComparator implements Comparator<ElementNode> {
    public int compare(ElementNode n1, ElementNode n2) {
      return n1.getDelegate().getText().compareToIgnoreCase(n2.getDelegate().getText());
    }
  }

  private static class OrderComparator implements Comparator<ElementNode> {
    public int compare(ElementNode n1, ElementNode n2) {
      return n1.getOrder() - n2.getOrder();
    }
  }
}
