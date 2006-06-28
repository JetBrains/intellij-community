package com.intellij.ide.util;

import com.intellij.ide.IconUtilEx;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.TreeToolTipHandler;
import com.intellij.util.Icons;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.javaee.model.common.ejb.EntityBean;
import com.intellij.javaee.model.common.ejb.CmpField;
import com.intellij.javaee.model.common.ejb.CmrField;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.List;

public class MemberChooser extends DialogWrapper {
  protected Tree myTree;
  private DefaultTreeModel myTreeModel;
  private JCheckBox myCopyJavadocCheckbox;
  private JCheckBox myInsertOverrideAnnotationCheckbox;

  private ArrayList mySelectedNodes = new ArrayList();

  private boolean mySorted = false;
  private boolean myShowClasses = true;
  private boolean myAllowEmptySelection = false;
  private boolean myAllowMultiSelection;
  private final boolean myIsInsertOverrideVisible;

  protected Object[] myElements;
  private HashMap<DefaultMutableTreeNode,DefaultMutableTreeNode> myNodeToParentMap = new HashMap<DefaultMutableTreeNode, DefaultMutableTreeNode>();
  private HashMap myElementToNodeMap = new HashMap();
  private ArrayList<ElementNode> myClassNodes = new ArrayList<ElementNode>();
  private WeakReference[] mySelectedElements;

  @NonNls private final static String PROP_SORTED = "MemberChooser.sorted";
  @NonNls private final static String PROP_SHOWCLASSES = "MemberChooser.showClasses";
  @NonNls private final static String PROP_COPYJAVADOC = "MemberChooser.copyJavadoc";
  @NonNls private final static String PROP_INSERT_OVERRIDE = "MemberChooser.insertOverride";

  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.MemberChooser");

  public MemberChooser(Object[] elements,
                       boolean allowEmptySelection,
                       boolean allowMultiSelection,
                       Project project) {
    this(elements, allowEmptySelection, allowMultiSelection, project, false);
  }

  public MemberChooser(Object[] elements,
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
    mySelectedNodes = new ArrayList();

    myNodeToParentMap = new HashMap<DefaultMutableTreeNode, DefaultMutableTreeNode>();
    myElementToNodeMap = new HashMap();
    myClassNodes = new ArrayList<ElementNode>();

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        myTreeModel = buildModel();
      }
    });
    myTree.setModel(myTreeModel);
    expandAll();
    myCopyJavadocCheckbox = new JCheckBox(IdeBundle.message("checkbox.copy.javadoc"));
    if (myIsInsertOverrideVisible) {
      myInsertOverrideAnnotationCheckbox = new JCheckBox(IdeBundle.message("checkbox.insert.at.override"));
    }
  }

  /**
   * should be invoked in read action
   */
  private DefaultTreeModel buildModel() {
    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
    Map map = new HashMap();

    for (int i = 0; i < myElements.length; i++) {
      Object object = myElements[i];
      if (object instanceof PsiElement) {
        final PsiElement psiElement = (PsiElement)object;
        LOG.assertTrue(psiElement instanceof PsiMember);
        final PsiClass psiClass = ((PsiMember)psiElement).getContainingClass();
        ElementNode classNode = (ElementNode)map.get(psiClass);
        if (classNode == null) {
          classNode = new ElementNode(psiClass, myClassNodes.size() * (i + 1));
          formatNode(classNode);
          rootNode.add(classNode);
          map.put(psiClass, classNode);
          myClassNodes.add(classNode);
        }
        ElementNode memberNode = new ElementNode(psiElement, myClassNodes.size() * (i + 1) + classNode.getChildCount());
        formatNode(memberNode);
        classNode.add(memberNode);
        myNodeToParentMap.put(memberNode, classNode);
        myElementToNodeMap.put(psiElement, memberNode);
      }
      else if (object instanceof CandidateInfo) {
        PsiElement element = ((CandidateInfo)object).getElement();
        PsiClass aClass = (PsiClass)element.getParent();
        //A class cannot inherit from the same class/interface twice with different substitutors
        ElementNode classNode = (ElementNode)map.get(aClass);
        if (classNode == null) {
          PsiSubstitutor substitutor = ((CandidateInfo)object).getSubstitutor();
          CandidateInfo info = new CandidateInfo(aClass, substitutor);
          classNode = new GenericElementNode(info, myClassNodes.size() * (i + 1));
          formatNode(classNode);
          rootNode.add(classNode);
          map.put(aClass, classNode);
          myClassNodes.add(classNode);
        }
        ElementNode memberNode = new GenericElementNode((CandidateInfo)object,
                                                        myClassNodes.size() * (i + 1) + classNode.getChildCount());
        formatNode(memberNode);
        classNode.add(memberNode);
        myNodeToParentMap.put(memberNode, classNode);
        myElementToNodeMap.put(object, memberNode);
      }

      else if (object instanceof CmpField) {
        CmpField field = (CmpField)object;
        final EntityBean bean = field.getEntityBean();
        EntityBeanNode beanNode = (EntityBeanNode)map.get(bean);

        if (beanNode == null) {
          beanNode = new EntityBeanNode(bean);
          rootNode.add(beanNode);
          map.put(bean, beanNode);
        }

        CmpFieldNode fieldNode = new CmpFieldNode(field);
        beanNode.add(fieldNode);
        myNodeToParentMap.put(fieldNode, beanNode);
        myElementToNodeMap.put(field, fieldNode);
      }
      else if (object instanceof CmrField) {
        CmrField field = (CmrField)object;
        final EntityBean bean = field.getOppositeField().getOppositeEntity();
        EntityBeanNode beanNode = (EntityBeanNode)map.get(bean);

        if (beanNode == null) {
          beanNode = new EntityBeanNode(bean);
          rootNode.add(beanNode);
          map.put(bean, beanNode);
        }

        CmrFieldNode fieldNode = new CmrFieldNode(field);
        beanNode.add(fieldNode);
        myNodeToParentMap.put(fieldNode, beanNode);
        myElementToNodeMap.put(field, fieldNode);
      }
    }
    return new DefaultTreeModel(rootNode);
  }

  public void selectElements(Object[] elements) {
    ArrayList<TreePath> selectionPaths = new ArrayList<TreePath>();
    for (Object element : elements) {
      DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)myElementToNodeMap.get(element);
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

    ShowClassesAction showClassesAction = new ShowClassesAction();
    showClassesAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.ALT_MASK)), myTree);
    setShowClasses(PropertiesComponent.getInstance().isTrueValue(PROP_SHOWCLASSES));
    group.add(showClassesAction);

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

    myTree.setCellRenderer(new CellRenderer());
    UIUtil.setLineStyleAngled(myTree);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.addKeyListener(new TreeKeyListener());
    myTree.addTreeSelectionListener(new MyTreeSelectionListener());

    if (!myAllowMultiSelection) {
      myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    }

    if (((DefaultMutableTreeNode)myTreeModel.getRoot()).getChildCount() > 0) {
      myTree.expandRow(0);
      myTree.setSelectionRow(1);
    }
    expandAll();
    new TreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      public String convert(TreePath path) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)(path).getLastPathComponent();
        if (node instanceof ElementNode) {
          ElementNode elementNode = (ElementNode)node;
          PsiElement psiElement = elementNode.getElement();
          if (psiElement instanceof PsiClass) return null;
          return elementNode.getText();
        }
        return null;
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

  private List getSelectedElementsList() {
    if (getExitCode() != OK_EXIT_CODE) {
      return null;
    }
    if (mySelectedElements == null) {
      return null;
    }
    ArrayList arrayList = new ArrayList();
    for (int i = 0; i < mySelectedElements.length; i++) {
      Object element = mySelectedElements[i].get();
      if (element != null) arrayList.add(element);
    }
    return arrayList;
  }

  public Object[] getSelectedElements() {
    List list = getSelectedElementsList();
    if (list == null) return null;
    return list.toArray(new Object[list.size()]);
  }

  public Object[] getSelectedElements(Object[] a) {
    List list = getSelectedElementsList();
    if (list == null) return null;
    return list.toArray(a);
  }

  public boolean areElementsSelected() {
    return mySelectedElements != null && mySelectedElements.length > 0;
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

    Pair pair = storeSelection();

    DefaultMutableTreeNode root = (DefaultMutableTreeNode)myTreeModel.getRoot();
    Enumeration children = root.children();
    while (children.hasMoreElements()) {
      DefaultMutableTreeNode classNode = (DefaultMutableTreeNode)children.nextElement();
      sortNode(classNode, sorted);
      myTreeModel.nodeStructureChanged(classNode);
    }

    restoreSelection(pair);
  }

  private void sortNode(DefaultMutableTreeNode node, boolean sorted) {
    ArrayList<ElementNode> arrayList = new ArrayList<ElementNode>();
    Enumeration children = node.children();
    while (children.hasMoreElements()) {
      arrayList.add((ElementNode)children.nextElement());
    }

    Collections.sort(arrayList, sorted ? new AlphaComparator() : new OrderComparator());

    node.removeAllChildren();
    for (int i = 0; i < arrayList.size(); i++) {
      node.add(arrayList.get(i));
    }
  }

  private void setShowClasses(boolean showClasses) {
    myShowClasses = showClasses;

    Pair selection = storeSelection();

    DefaultMutableTreeNode root = (DefaultMutableTreeNode)myTreeModel.getRoot();
    if (!myShowClasses || myClassNodes.size() == 0) {
      List<DefaultMutableTreeNode> otherObjects = new ArrayList<DefaultMutableTreeNode>();
      Enumeration children = root.children();
      ElementNode newRoot = new AllClassesNode();
      while (children.hasMoreElements()) {
        final DefaultMutableTreeNode nextElement = (DefaultMutableTreeNode)children.nextElement();
        if (!(nextElement instanceof ElementNode)) {
          otherObjects.add(nextElement);
          continue;
        }
        ElementNode classNode = (ElementNode)nextElement;
        Enumeration memberNodes = classNode.children();
        ArrayList memberNodesArray = new ArrayList();
        while (memberNodes.hasMoreElements()) {
          memberNodesArray.add(memberNodes.nextElement());
        }
        for (int i = 0; i < memberNodesArray.size(); i++) {
          ElementNode memberNode = (ElementNode)memberNodesArray.get(i);
          newRoot.add(memberNode);
        }
      }
      root.removeAllChildren();
      for (Iterator<DefaultMutableTreeNode> iterator = otherObjects.iterator(); iterator.hasNext();) {
        DefaultMutableTreeNode node = iterator.next();
        root.add(node);
      }
      sortNode(newRoot, mySorted);
      if (newRoot.children().hasMoreElements()) root.add(newRoot);
    }
    else {
      Enumeration children = root.children();
      if (children.hasMoreElements()) {
        DefaultMutableTreeNode allClassesNode = (DefaultMutableTreeNode)children.nextElement();
        Enumeration memberNodes = allClassesNode.children();
        ArrayList arrayList = new ArrayList();
        while (memberNodes.hasMoreElements()) {
          arrayList.add(memberNodes.nextElement());
        }
        for (int i = 0; i < arrayList.size(); i++) {
          DefaultMutableTreeNode memberNode = (DefaultMutableTreeNode)arrayList.get(i);
          DefaultMutableTreeNode classNode = myNodeToParentMap.get(memberNode);
          classNode.add(memberNode);
        }
      }
      root.removeAllChildren();
      for (int i = 0; i < myClassNodes.size(); i++) {
        DefaultMutableTreeNode classNode = (DefaultMutableTreeNode)myClassNodes.get(i);
        root.add(classNode);
      }
    }
    myTreeModel.nodeStructureChanged(root);

    expandAll();

    restoreSelection(selection);
  }

  private Pair storeSelection() {
    ArrayList selectedNodes = new ArrayList();
    TreePath[] paths = myTree.getSelectionPaths();
    if (paths != null) {
      for (int i = 0; i < paths.length; i++) {
        selectedNodes.add(paths[i].getLastPathComponent());
      }
    }
    TreePath leadSelectionPath = myTree.getLeadSelectionPath();
    return new Pair(leadSelectionPath != null ? leadSelectionPath.getLastPathComponent() : null, selectedNodes);
  }


  private void restoreSelection(Pair pair) {
    ArrayList selectedNodes = (ArrayList)pair.second;

    DefaultMutableTreeNode root = (DefaultMutableTreeNode)myTreeModel.getRoot();

    ArrayList<TreePath> toSelect = new ArrayList<TreePath>();
    for (int i = 0; i < selectedNodes.size(); i++) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectedNodes.get(i);
      if (root.isNodeDescendant(node)) {
        toSelect.add(new TreePath(node.getPath()));
      }
    }

    if (toSelect.size() > 0) {
      myTree.setSelectionPaths((TreePath[])toSelect.toArray(new TreePath[toSelect.size()]));
    }

    DefaultMutableTreeNode leadNode = (DefaultMutableTreeNode)pair.first;
    if (leadNode != null) {
      myTree.setLeadSelectionPath(new TreePath(leadNode.getPath()));
    }
  }

  protected void doOKAction() {
    super.doOKAction();
  }

  private void updateSelectedElements() {
    Set set = new LinkedHashSet();
    for (int i = 0; i < mySelectedNodes.size(); i++) {
      final Object o = mySelectedNodes.get(i);
      if (o instanceof GenericElementNode) {
        set.add(((GenericElementNode)o).getCandidateInfo());
      }
      else if (o instanceof ElementNode) {
        PsiElement element = ((ElementNode)o).getElement();
        if (element != null) set.add(element);
      }
      else if (o instanceof CmpFieldNode) {
        set.add(((CmpFieldNode)o).getField());
      }
      else if (o instanceof CmrFieldNode) {
        set.add(((CmrFieldNode)o).getField());
      }
    }
    mySelectedElements = new WeakReference[set.size()];
    int i = 0;
    for (Iterator it = set.iterator(); it.hasNext(); i++) {
      mySelectedElements[i] = new WeakReference(it.next());
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

  private class MyTreeSelectionListener implements TreeSelectionListener {
    public void valueChanged(TreeSelectionEvent e) {
      TreePath[] paths = e.getPaths();
      if (paths == null) return;
      for (int i = 0; i < paths.length; i++) {
        Object node = paths[i].getLastPathComponent();
        if (node instanceof ElementNode && ((ElementNode)node).getElement() instanceof PsiClass) continue;
        if (e.isAddedPath(i)) {
          if (!mySelectedNodes.contains(node)) {
            mySelectedNodes.add(node);
          }
        }
        else {
          mySelectedNodes.remove(node);
        }
      }
      updateSelectedElements();

    }
  }

  /**
   * should be invoked in run action
   */
  private static void formatNode(ElementNode node) {
    final PsiElement element = node.getElement();
    if (element instanceof PsiClass) {
      node.setText(PsiFormatUtil.formatClass((PsiClass)element, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_FQ_NAME));
      node.setIcon(element.getIcon(0));
    }
    else if (element instanceof PsiMethod) {
      int methodOptions = PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER |
                          PsiFormatUtil.SHOW_PARAMETERS;
      int paramOptions = PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER;
      node.setText(PsiFormatUtil.formatMethod((PsiMethod)element, PsiSubstitutor.EMPTY, methodOptions, paramOptions));
      node.setIcon(element.getIcon(Iconable.ICON_FLAG_VISIBILITY));
    }
    else if (element instanceof PsiField) {
      int options = PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER;
      node.setText(PsiFormatUtil.formatVariable((PsiField)element, options, PsiSubstitutor.EMPTY));
      node.setIcon(element.getIcon(Iconable.ICON_FLAG_VISIBILITY));
    }
  }

  private void expandAll() {
    Enumeration children = ((DefaultMutableTreeNode)myTreeModel.getRoot()).children();
    while (children.hasMoreElements()) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode)children.nextElement();
      myTree.expandPath(new TreePath(child.getPath()));
    }
  }

  private void collapseAll() {
    Enumeration children = ((DefaultMutableTreeNode)myTreeModel.getRoot()).children();
    while (children.hasMoreElements()) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode)children.nextElement();
      myTree.collapsePath(new TreePath(child.getPath()));
    }
  }

  private static class ElementNode extends DefaultMutableTreeNode {
    private Icon myIcon;
    private String myText;
    protected PsiElement myElement;
    private int myOrder;
    private boolean myIsDeprecated;

    private boolean isDeprecated() {
      return myIsDeprecated;
    }

    public ElementNode(PsiElement element, int order) {
      myElement = element;
      myOrder = order;
      myIsDeprecated = element instanceof PsiDocCommentOwner && ((PsiDocCommentOwner)element).isDeprecated();
    }

    public Icon getIcon() {
      return myIcon;
    }

    public void setIcon(Icon icon) {
      myIcon = icon;
    }

    public String getText() {
      return myText;
    }

    public void setText(String text) {
      myText = text;
    }

    public PsiElement getElement() {
      return myElement;
    }

    public int getOrder() {
      return myOrder;
    }
  }

  private static class GenericElementNode extends ElementNode {
    public CandidateInfo getCandidateInfo() {
      return myCandidate;
    }

    private CandidateInfo myCandidate;

    public GenericElementNode(CandidateInfo info, int order) {
      super(info.getElement(), order);
      myCandidate = info;
    }

    public String getText() {
      PsiSubstitutor substitutor = myCandidate.getSubstitutor();
      if (substitutor == PsiSubstitutor.EMPTY) return super.getText();

      if (myElement instanceof PsiClass) {
        PsiType classType = myElement.getManager().getElementFactory().createType((PsiClass)myElement, substitutor);
        return PsiFormatUtil.formatType(classType, 0, PsiSubstitutor.EMPTY);
      }
      else if (myElement instanceof PsiMethod) {
        int methodOptions = PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER |
                            PsiFormatUtil.SHOW_PARAMETERS;
        int paramOptions = PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER;
        return PsiFormatUtil.formatMethod((PsiMethod)myElement, substitutor, methodOptions, paramOptions);
      }
      else {
        return super.getText();
      }
    }
  }

  private static class EntityBeanNode extends DefaultMutableTreeNode {
    private Icon myIcon;
    private String myText;

    public EntityBeanNode(EntityBean bean) {
      myText = bean.getEjbName().getValue();
      myIcon = IconUtilEx.getIcon(bean, 0, null);
    }

    public Icon getIcon() {
      return myIcon;
    }

    public String getText() {
      return myText;
    }
  }

  private static class CmpFieldNode extends DefaultMutableTreeNode {
    private Icon myIcon;
    private String myText;
    private CmpField myField;

    public CmpFieldNode(CmpField field) {
      myField = field;
      myText = field.getFieldName().getValue();
      myIcon = IconUtilEx.getIcon(field, 0, null);
    }

    public Icon getIcon() {
      return myIcon;
    }

    public String getText() {
      return myText;
    }

    public CmpField getField() {
      return myField;
    }
  }

  private static class CmrFieldNode extends DefaultMutableTreeNode {
    private Icon myIcon;
    private String myText;
    private CmrField myField;

    public CmrFieldNode(CmrField field) {
      myField = field;
      myText = field.getCmrFieldName().getValue();
      myIcon = IconUtilEx.getIcon(field, 0, null);
    }

    public Icon getIcon() {
      return myIcon;
    }

    public String getText() {
      return myText;
    }

    public CmrField getField() {
      return myField;
    }
  }

  private static class AllClassesNode extends ElementNode {
    public AllClassesNode() {
      super(null, 0);
    }

    public String getText() {
      return IdeBundle.message("node.memberchooser.all.classes");
    }

    public PsiElement getElement() {
      return null;
    }
  }

  private class CellRenderer extends ColoredTreeCellRenderer {
    public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded,
                                      boolean leaf, int row, boolean hasFocus) {
      SimpleTextAttributes attributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN,
                                                                 tree.getForeground());
      if (value instanceof ElementNode) {
        ElementNode node = (ElementNode)value;
        if (node.isDeprecated()) {
          attributes =
          new SimpleTextAttributes(
            node.isDeprecated() ? SimpleTextAttributes.STYLE_STRIKEOUT : SimpleTextAttributes.STYLE_PLAIN,
            tree.getForeground());
        }
        append(node.getText(), attributes);
        setIcon(node.getIcon());
      }
      else if (value instanceof CmpFieldNode) {
        CmpFieldNode node = (CmpFieldNode)value;
        append(node.getText(), attributes);
        setIcon(node.getIcon());
      }
      else if (value instanceof CmrFieldNode) {
        CmrFieldNode node = (CmrFieldNode)value;
        append(node.getText(), attributes);
        setIcon(node.getIcon());
      }
      else if (value instanceof EntityBeanNode) {
        EntityBeanNode node = (EntityBeanNode)value;
        append(node.getText(), attributes);
        setIcon(node.getIcon());
      }
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
      if (e.getKeyCode() == KeyEvent.VK_ENTER) {
        TreePath path = myTree.getLeadSelectionPath();
        if (path == null) return;
        if (path.getLastPathComponent() instanceof ElementNode) {
          ElementNode node = (ElementNode)path.getLastPathComponent();
          if (node.getElement() instanceof PsiClass) return;
        }
        doOKAction();
        e.consume();
      }
      else if (e.getKeyCode() == KeyEvent.VK_INSERT) {
        TreePath path = myTree.getLeadSelectionPath();
        if (path == null) return;
        if (path.getLastPathComponent() instanceof ElementNode) {
          ElementNode node = (ElementNode)path.getLastPathComponent();
          if (node.getElement() instanceof PsiClass) return;
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

  private class ShowClassesAction extends ToggleAction {
    public ShowClassesAction() {
      super(IdeBundle.message("action.show.classes"), IdeBundle.message("action.show.classes"), Icons.CLASS_ICON);
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
      presentation.setEnabled(myClassNodes.size() > 1);
    }
  }

  private class ExpandAllAction extends AnAction {
    public ExpandAllAction() {
      super(IdeBundle.message("action.expand.all"), IdeBundle.message("action.expand.all"),
            IconLoader.getIcon("/actions/expandall.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      expandAll();
    }
  }

  private class CollapseAllAction extends AnAction {
    public CollapseAllAction() {
      super(IdeBundle.message("action.collapse.all"), IdeBundle.message("action.collapse.all"),
            IconLoader.getIcon("/actions/collapseall.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      collapseAll();
    }
  }

  private static class AlphaComparator implements Comparator<ElementNode> {
    public int compare(ElementNode n1, ElementNode n2) {
      return n1.getText().compareToIgnoreCase(n2.getText());
    }
  }

  private static class OrderComparator implements Comparator<ElementNode> {
    public int compare(ElementNode n1, ElementNode n2) {
      return n1.getOrder() - n2.getOrder();
    }
  }
}
