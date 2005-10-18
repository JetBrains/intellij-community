package com.intellij.uiDesigner;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.DispatchThreadProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.make.FormSourceCodeGenerator;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.ComponentItemDialog;
import com.intellij.uiDesigner.palette.GroupItem;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.CommonBundle;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.text.MessageFormat;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class GuiDesignerConfigurable implements Configurable, ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.GuiDesignerConfigurable");

  private final Project myProject;
  private MyGeneralUI myGeneralUI;
  private MyPaletteUI myPaletteUI;

  /** Invoked by reflection */
  GuiDesignerConfigurable(final Project project) {
    myProject = project;
  }

  public void projectOpened() {}

  public void projectClosed() {}

  public String getComponentName() {
    return "uidesigner-configurable";
  }

  public void initComponent() {}

  public void disposeComponent() {}

  public String getDisplayName() {
    return UIDesignerBundle.message("title.gui.designer");
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/uiDesigner.png");
  }

  public String getHelpTopic() {
    return "project.propGUI";
  }

  public JComponent createComponent() {
    LOG.assertTrue(myGeneralUI == null);
    LOG.assertTrue(myPaletteUI == null);

    myGeneralUI = new MyGeneralUI();
    myPaletteUI = new MyPaletteUI();

    final TabbedPaneWrapper wrapper = new TabbedPaneWrapper();
    wrapper.addTab(UIDesignerBundle.message("tab.general"), myGeneralUI.myPanel);
    wrapper.addTab(UIDesignerBundle.message("tab.palette"), myPaletteUI.myPanel);

    return wrapper.getComponent();
  }

  private boolean isPalatteModified(){
    final ArrayList<GroupItem> oldGroups = Palette.getInstance(myProject).getGroups();
    final ArrayList<GroupItem> newGroups = getGroupsFromTree();

    if (newGroups.size() != oldGroups.size()) {
      return true;
    }

    for (int i = 0; i < newGroups.size(); i++){
      final GroupItem _groupItem = newGroups.get(i);

      if (!_groupItem.getName().equals(oldGroups.get(i).getName())) {
        return true;
      }

      final ArrayList<ComponentItem> _items = _groupItem.getItems();
      final ArrayList<ComponentItem> items = oldGroups.get(i).getItems();

      if (!items.equals(_items)) {
        return true;
      }
    }

    return false;
  }

  public boolean isModified() {
    final GuiDesignerConfiguration configuration = GuiDesignerConfiguration.getInstance(myProject);

    if (myGeneralUI.myChkCopyFormsRuntime.isSelected() != configuration.COPY_FORMS_RUNTIME_TO_OUTPUT) {
      return true;
    }

    if (configuration.INSTRUMENT_CLASSES != myGeneralUI.myRbInstrumentClasses.isSelected()) {
      return true;
    }

    // compare palettes
    if(isPalatteModified()){
      return true;
    }

    return false;
  }

  private static boolean isRemovable(final GroupItem group){
    LOG.assertTrue(group != null);

    final ArrayList<ComponentItem> items = group.getItems();
    for(int i = items.size() - 1; i >=0; i--){
      if(!items.get(i).isRemovable()){
        return false;
      }
    }
    return true;
  }

  public void apply() {
    final DispatchThreadProgressWindow progressWindow = new DispatchThreadProgressWindow(false, myProject);
    progressWindow.setTitle(UIDesignerBundle.message("title.converting.project"));
    progressWindow.start();

    GuiDesignerConfiguration.getInstance(myProject).COPY_FORMS_RUNTIME_TO_OUTPUT = myGeneralUI.myChkCopyFormsRuntime.isSelected();

    // We have to store value of the radio button here because myGeneralUI will be cleared
    // just after apply is invoked (applyImpl is invoked later)
    final boolean instrumentClasses = myGeneralUI.myRbInstrumentClasses.isSelected();
    ApplicationManager.getApplication().invokeLater(new MyApplyRunnable(progressWindow, instrumentClasses), progressWindow.getModalityState());

    /*Set new palette if it was modified*/
    if(isPalatteModified()){
      final ArrayList<GroupItem> groupList = getGroupsFromTree();
      final ArrayList<GroupItem> clonedGroupList = new ArrayList<GroupItem>();
      for(int i = 0; i < groupList.size(); i++){
        // We have to clone GroupItem here becouse after applying changes user can continue
        // editing of palette (and "Cancel" button should work)
        clonedGroupList.add(groupList.get(i).clone());
      }

      /*apply changes*/
      Palette.getInstance(myProject).setGroups(clonedGroupList);
    }
  }

  public void reset() {
    final GuiDesignerConfiguration configuration = GuiDesignerConfiguration.getInstance(myProject);

    /*general*/
    if (configuration.INSTRUMENT_CLASSES) {
      myGeneralUI.myRbInstrumentClasses.setSelected(true);
    }
    else {
      myGeneralUI.myRbInstrumentSources.setSelected(true);
    }
    myGeneralUI.myChkCopyFormsRuntime.setSelected(configuration.COPY_FORMS_RUNTIME_TO_OUTPUT);

    /*palette*/
    final DefaultTreeModel model = buildModel(Palette.getInstance(myProject));
    myPaletteUI.myTree.setModel(model);
    myPaletteUI.myTree.setCellRenderer(new MyTreeCellRenderer());

    // a. Expand and preselect first group
    final DefaultMutableTreeNode root = (DefaultMutableTreeNode)model.getRoot();
    LOG.assertTrue(root.getChildCount() >= 1); // there should be at least on group
    final TreePath path = new TreePath(new Object[]{root, root.getChildAt(0)});
    myPaletteUI.myTree.expandPath(path);
    myPaletteUI.myTree.setSelectionPath(path);
  }

  public void disposeUIResources() {
    myGeneralUI = null;
    myPaletteUI = null;
  }

  /** Helper method.*/
  private static DefaultMutableTreeNode buildNodeForComponent(final ComponentItem item){
    LOG.assertTrue(item != null);
    final DefaultMutableTreeNode node = new DefaultMutableTreeNode(item);
    return node;
  }

  /** Helper method.*/
  private static DefaultMutableTreeNode buildNodeForGroup(final GroupItem groupItem){
    LOG.assertTrue(groupItem != null);

    final GroupItem _groupItem = groupItem.clone();
    final DefaultMutableTreeNode result = new DefaultMutableTreeNode(_groupItem);
    final ArrayList<ComponentItem> items = _groupItem.getItems();
    for(int i = 0; i < items.size(); i++){
      final DefaultMutableTreeNode node = buildNodeForComponent(items.get(i));
      result.add(node);
    }

    return result;
  }

  /**
   * @return list of all groups from the tree. Returned groups have the same order as in the tree.
   */
  private ArrayList<GroupItem> getGroupsFromTree(){
    final ArrayList<GroupItem> result = new ArrayList<GroupItem>();
    final DefaultMutableTreeNode root = (DefaultMutableTreeNode)myPaletteUI.myTree.getModel().getRoot();
    for(int i = 0; i < root.getChildCount(); i++){
      final DefaultMutableTreeNode groupNode = (DefaultMutableTreeNode)root.getChildAt(i);
      final GroupItem group = (GroupItem)groupNode.getUserObject();
      result.add(group);
    }
    return result;
  }

  /** Helper method.*/
  private static DefaultTreeModel buildModel(final Palette palette){
    LOG.assertTrue(palette != null);

    final DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    final ArrayList<GroupItem> groups = palette.getGroups();
    for(int i = 0; i < groups.size(); i++){
      final DefaultMutableTreeNode node = buildNodeForGroup(groups.get(i));
      root.add(node);
    }

    return new DefaultTreeModel(root);
  }

  /**
   * Creates and adds {@link GroupItem} just after the last selected {@link GroupItem}
   */
  private void addGroupItem(){
    // Ask group name
    final String groupName = Messages.showInputDialog(
      myPaletteUI.myPanel,
      UIDesignerBundle.message("message.enter.group.name"),
      UIDesignerBundle.message("title.add.group"),
      Messages.getQuestionIcon()
    );
    if(groupName == null){
      return;
    }

    // Check that name of the group is unique
    final ArrayList<GroupItem> groups = getGroupsFromTree();
    for(int i = groups.size() - 1; i >= 0; i--){
      if(groupName.equals(groups.get(i).getName())){
        Messages.showErrorDialog(myPaletteUI.myPanel,
                                 UIDesignerBundle.message("error.group.name.should.be.unique"),
                                 CommonBundle.getErrorTitle());
        return;
      }
    }

    final GroupItem groupToBeAdded = new GroupItem(groupName);
    final DefaultMutableTreeNode nodeToBeAdded = new DefaultMutableTreeNode(groupToBeAdded);

    final ArrayList<TreePath> expandedPaths = new ArrayList<TreePath>();
    TreeUtil.collectExpandedPaths(myPaletteUI.myTree, expandedPaths);

    // Add new group to the model
    final DefaultTreeModel model = (DefaultTreeModel)myPaletteUI.myTree.getModel();
    final DefaultMutableTreeNode root = (DefaultMutableTreeNode)model.getRoot();
    final DefaultMutableTreeNode lastSelectedNode = (DefaultMutableTreeNode)myPaletteUI.myTree.getLastSelectedPathComponent();
    if(lastSelectedNode != null){
      final DefaultMutableTreeNode selectedGroupNode;
      if(lastSelectedNode.getUserObject() instanceof GroupItem){
        selectedGroupNode = lastSelectedNode;
      }
      else if(lastSelectedNode.getUserObject() instanceof ComponentItem){
        selectedGroupNode = (DefaultMutableTreeNode)lastSelectedNode.getParent();
      }
      else{
        //noinspection HardCodedStringLiteral
        throw new RuntimeException("unknown userObject: " + lastSelectedNode.getUserObject());
      }

      final int index = root.getIndex(selectedGroupNode);
      LOG.assertTrue(index != -1);
      root.insert(nodeToBeAdded, index + 1);
    }
    else{
      // Add new group to the end
      root.add(nodeToBeAdded);
    }

    // Select and scroll to the added node
    model.nodeStructureChanged(root);
    TreeUtil.restoreExpandedPaths(myPaletteUI.myTree, expandedPaths);
    TreeUtil.selectNode(myPaletteUI.myTree, nodeToBeAdded);
  }

  /**
   * @return currently TreeNode for selected {@link GroupItem}. If {@link ComponentItem} is currently selected
   * then the method return parent node of of the selected item.
   */
  private DefaultMutableTreeNode getSelectedGroupNode(){
    final DefaultMutableTreeNode lastSelectedNode = (DefaultMutableTreeNode)myPaletteUI.myTree.getLastSelectedPathComponent();
    if(lastSelectedNode == null){
      return null;
    }

    final DefaultMutableTreeNode groupNode;
    if(lastSelectedNode.getUserObject() instanceof GroupItem){
      groupNode = lastSelectedNode;
    }
    else{
      groupNode = (DefaultMutableTreeNode)lastSelectedNode.getParent();
    }
    LOG.assertTrue(groupNode != null);

    return groupNode;
  }

  private void editGroupItem(){
    final DefaultMutableTreeNode groupNode = getSelectedGroupNode();
    LOG.assertTrue(groupNode != null);
    final GroupItem groupToBeEdited = (GroupItem)groupNode.getUserObject();

    // Ask group name
    final String groupName = Messages.showInputDialog(
      myPaletteUI.myPanel,
      UIDesignerBundle.message("edit.enter.group.name"),
      UIDesignerBundle.message("title.edit.group"),
      Messages.getQuestionIcon(),
      groupToBeEdited.getName(),
      null
    );
    if(groupName == null){
      return;
    }

    // Check that group name is unique
    final ArrayList<GroupItem> groups = getGroupsFromTree();
    for(int i = groups.size() - 1; i >= 0; i--){
      if(groupName.equals(groups.get(i).getName())){
        Messages.showErrorDialog(myPaletteUI.myPanel, UIDesignerBundle.message("error.group.name.unique"),
                                 CommonBundle.getErrorTitle());
        return;
      }
    }

    // Update tree
    groupToBeEdited.setName(groupName);
    final DefaultTreeModel model = (DefaultTreeModel)myPaletteUI.myTree.getModel();
    model.nodeChanged(groupNode);
  }

  private void removeGroupItem(){
    final DefaultMutableTreeNode groupNode = getSelectedGroupNode();
    LOG.assertTrue(groupNode != null);
    final GroupItem groupToBeRemoved = (GroupItem)groupNode.getUserObject();

    if(!isRemovable(groupToBeRemoved)){
      Messages.showInfoMessage(
        myPaletteUI.myPanel,
        UIDesignerBundle.message("error.cannot.remove.default.group"),
        CommonBundle.getErrorTitle()
      );
      return;
    }

    // Remove group and try to restore selection
    final ArrayList<TreePath> expandedPaths = new ArrayList<TreePath>();
    TreeUtil.collectExpandedPaths(myPaletteUI.myTree, expandedPaths);
    TreeUtil.removeLastPathComponent(
      myPaletteUI.myTree,
      new TreePath(groupNode.getPath())
    );
    TreeUtil.restoreExpandedPaths(myPaletteUI.myTree, expandedPaths);
  }

  /**
   * Creates and adds {@link ComponentItem} to the currently selected {@link GroupItem}
   */
  private void addComponentItem(){
    final TreePath path = myPaletteUI.myTree.getSelectionPath();
    final DefaultMutableTreeNode lastPathComponent = (DefaultMutableTreeNode)path.getLastPathComponent();
    LOG.assertTrue(lastPathComponent != null);

    // Determine group. #getUserObject() is a GroupItem
    final DefaultMutableTreeNode groupNode;

    if(lastPathComponent.getUserObject() instanceof GroupItem){
      groupNode = lastPathComponent;
    }
    else {
      final Object userObject = lastPathComponent.getUserObject();
      if(userObject instanceof ComponentItem){
        groupNode = (DefaultMutableTreeNode)lastPathComponent.getParent();
      }
      else{
        //noinspection HardCodedStringLiteral
        throw new RuntimeException("unknown user object: " + lastPathComponent.getUserObject());
      }
    }

    LOG.assertTrue(groupNode != null);

    // Show dialog
    final ComponentItem itemToBeAdded = new ComponentItem(
      "",
      null,
      null,
      new GridConstraints(),
      new HashMap<String, StringDescriptor>(),
      true/*all user defined components are removable*/
    );
    final ComponentItemDialog dialog = new ComponentItemDialog(myProject, myPaletteUI.myPanel, itemToBeAdded);
    dialog.setTitle(UIDesignerBundle.message("title.add.component"));
    dialog.show();
    if(!dialog.isOK()){
      return;
    }


    final GroupItem groupItem = (GroupItem)groupNode.getUserObject();

    // If the itemToBeAdded is already in palette do nothing
    if(groupItem.containsItem(itemToBeAdded)){
      return;
    }

    // add to the group
    groupItem.addItem(itemToBeAdded);

    // add to the tree
    final DefaultMutableTreeNode itemNode = new DefaultMutableTreeNode(itemToBeAdded);
    groupNode.add(itemNode);
    final DefaultTreeModel model = (DefaultTreeModel)myPaletteUI.myTree.getModel();
    model.nodeStructureChanged(groupNode);
    TreeUtil.selectNode(myPaletteUI.myTree, itemNode);
  }

  /**
   * Edits properties of currently selected {@link ComponentItem}. This method should be called
   * only when {@link ComponentItem} is selected in the tree.
   */
  private void editComponentItem(){
    final DefaultMutableTreeNode lastNode = (DefaultMutableTreeNode)myPaletteUI.myTree.getLastSelectedPathComponent();
    final ComponentItem selectedItem = (ComponentItem)lastNode.getUserObject();
    LOG.assertTrue(selectedItem != null);

    /*Edit selected item*/
    final ComponentItem itemToBeEdited = selectedItem.clone(); /*"Cancel" should work, so we need edit copy*/
    final ComponentItemDialog dialog = new ComponentItemDialog(myProject, myPaletteUI.myPanel, itemToBeEdited);
    dialog.setTitle(UIDesignerBundle.message("title.edit.component"));
    dialog.show();
    if(!dialog.isOK()){
      return;
    }

    /*Replace selected item with the edited one*/
    final DefaultMutableTreeNode groupNode = (DefaultMutableTreeNode)lastNode.getParent();
    final GroupItem group = (GroupItem)groupNode.getUserObject();
    group.replaceItem(selectedItem, itemToBeEdited);

    lastNode.setUserObject(itemToBeEdited);
    final DefaultTreeModel model = (DefaultTreeModel)myPaletteUI.myTree.getModel();
    model.nodeChanged(lastNode);
  }

  /**
   * Removed currently selected {@link ComponentItem}. This method should be called
   * only when {@link ComponentItem} is selected in the tree. If item is not removable
   * (belong to default palatte) the method shows warning dialog and does nothing.
   */
  private void removeComponentItem(){
    final DefaultMutableTreeNode lastNode = (DefaultMutableTreeNode)myPaletteUI.myTree.getLastSelectedPathComponent();
    final ComponentItem selectedItem = (ComponentItem)lastNode.getUserObject();

    LOG.assertTrue(selectedItem != null);

    if(!selectedItem.isRemovable()){
      Messages.showInfoMessage(
        myPaletteUI.myPanel,
        UIDesignerBundle.message("error.cannot.remove.default.palette"),
        CommonBundle.getErrorTitle()
      );
      return;
    }

    final DefaultMutableTreeNode groupNode = (DefaultMutableTreeNode)lastNode.getParent();
    final GroupItem group = (GroupItem)groupNode.getUserObject();

    group.removeItem(selectedItem);
    TreeUtil.removeSelected(myPaletteUI.myTree);
  }

  /*UI for "General" tab*/
  private static final class MyGeneralUI {
    public JPanel myPanel;
    public JRadioButton myRbInstrumentClasses;
    public JRadioButton myRbInstrumentSources;
    public JCheckBox myChkCopyFormsRuntime;

    public MyGeneralUI() {
      final ButtonGroup group = new ButtonGroup();
      group.add(myRbInstrumentClasses);
      group.add(myRbInstrumentSources);
    }
  }

  /*UI for "Palette" tab*/
  private final class MyPaletteUI{
    private JPanel myPanel;
    private JTree myTree;
    private JButton myBtnAddComponent;
    private JButton myBtnEditComponent;
    private JButton myBtnRemoveComponent;
    private JButton myBtnAddGroup;
    private JButton myBtnEditGroup;
    private JButton myBtnRemoveGroup;

    public MyPaletteUI() {
      final TreeSelectionModel selectionModel = myTree.getSelectionModel();
      selectionModel.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

      myTree.setRootVisible(false);
      myTree.setShowsRootHandles(true);

      selectionModel.addTreeSelectionListener(
        new TreeSelectionListener() {
          public void valueChanged(final TreeSelectionEvent e) {
            updateButtonStates();
          }
        }
      );

      myBtnAddGroup.addActionListener(
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            addGroupItem();
          }
        }
      );

      myBtnEditGroup.addActionListener(
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            editGroupItem();
          }
        }
      );

      myBtnRemoveGroup.addActionListener(
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            removeGroupItem();
          }
        }
      );

      myBtnAddComponent.addActionListener(
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            addComponentItem();
          }
        }
      );

      /*INSERT adds new component*/
      myPanel.registerKeyboardAction(
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            if(isAddComponentButtonEnabled()){
              addComponentItem();
            }
          }
        },
        KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0),
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
      );

      myBtnEditComponent.addActionListener(
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            editComponentItem();
          }
        }
      );

      /*Double click start editing of selected component*/
      myTree.addMouseListener(
        new MouseAdapter() {
          public void mouseClicked(final MouseEvent e) {
            if(e.getClickCount() != 2){
              return;
            }

            if(isEditComponentButtonEnabled()){
              editComponentItem();
            }
            else if(isEditGroupButtonEnabled()){
              editGroupItem();
            }

            e.consume();
          }
        }
      );

      myBtnRemoveComponent.addActionListener(
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            removeComponentItem();
          }
        }
      );

      /*DELETE remoeds selected component*/
      myPanel.registerKeyboardAction(
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            if(isRemoveComponentButtonEnabled()){
              removeComponentItem();
            }
          }
        },
        KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
      );
    }

    /*Helper method*/
    private boolean isAddComponentButtonEnabled(){
      final DefaultMutableTreeNode lastNode = (DefaultMutableTreeNode)myTree.getLastSelectedPathComponent();
      return lastNode != null;
    }

    /*Helper method*/
    private boolean isEditGroupButtonEnabled(){
      return getSelectedGroupNode() != null;
    }

    /*Helper method*/
    private boolean isRemoveGroupButtonEnabled(){
      return getSelectedGroupNode() != null;
    }

    /*Helper method*/
    private boolean isEditComponentButtonEnabled(){
      final DefaultMutableTreeNode lastNode = (DefaultMutableTreeNode)myTree.getLastSelectedPathComponent();
      return lastNode != null && (lastNode.getUserObject() instanceof ComponentItem);
    }

    /*Helper method*/
    private boolean isRemoveComponentButtonEnabled(){
      return isEditComponentButtonEnabled();
    }

    private void updateButtonStates(){
      myBtnEditGroup.setEnabled(isEditGroupButtonEnabled());
      myBtnRemoveGroup.setEnabled(isRemoveGroupButtonEnabled());

      myBtnAddComponent.setEnabled(isAddComponentButtonEnabled());
      myBtnEditComponent.setEnabled(isEditComponentButtonEnabled());
      myBtnRemoveComponent.setEnabled(isRemoveComponentButtonEnabled());
    }
  }

  private final class MyApplyRunnable implements Runnable {
    private final DispatchThreadProgressWindow myProgressWindow;
    private final boolean myInstrumentClasses;

    public MyApplyRunnable(final DispatchThreadProgressWindow progressWindow, final boolean instrumentClasses) {
      myProgressWindow = progressWindow;
      myInstrumentClasses = instrumentClasses;
    }

    /**
     * Removes all generated sources
     */
    private void vanishGeneratedSources() {
      final PsiShortNamesCache cache = PsiManager.getInstance(myProject).getShortNamesCache();
      final PsiMethod[] methods = cache.getMethodsByName(FormSourceCodeGenerator.METHOD_NAME,
                                                         GlobalSearchScope.projectScope(myProject));

      for (int i = 0; i < methods.length; i++) {
        final PsiMethod method = methods[i];
        final PsiClass aClass = method.getContainingClass();
        if (aClass != null) {
          try {
            final PsiFile psiFile = aClass.getContainingFile();
            LOG.assertTrue(psiFile != null);
            final VirtualFile vFile = psiFile.getVirtualFile();
            LOG.assertTrue(vFile != null);
            myProgressWindow.setText(UIDesignerBundle.message("progress.converting", vFile.getPresentableUrl()));
            myProgressWindow.setFraction(((double)i) / ((double)methods.length));
            FormSourceCodeGenerator.cleanup(aClass);
          }
          catch (IncorrectOperationException e) {
            e.printStackTrace();
          }
        }
      }
    }

    /**
     * Launches vanish/generate sources processes
     */
    private void applyImpl(final boolean instrumentClasses) {
      final GuiDesignerConfiguration configuration = GuiDesignerConfiguration.getInstance(myProject);
      configuration.INSTRUMENT_CLASSES = instrumentClasses;

      if (configuration.INSTRUMENT_CLASSES && !myProject.isDefault()) {
        CommandProcessor.getInstance().executeCommand(
          myProject,
          new Runnable() {
            public void run() {
              ApplicationManager.getApplication().runWriteAction(
                new Runnable() {
                  public void run() {
                    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
                    vanishGeneratedSources();
                  }
                }
              );
            }
          },
          "",
          null
        );
      }
    }

    public void run() {
      ProgressManager.getInstance().runProcess(
        new Runnable() {
          public void run() {
            applyImpl(myInstrumentClasses);
          }
        },
        myProgressWindow
      );
    }
  }

  private static final class MyTreeCellRenderer extends ColoredTreeCellRenderer{
    private final SimpleTextAttributes myAttr;
    private final SimpleTextAttributes myAttr2;

    public MyTreeCellRenderer() {
      myAttr = SimpleTextAttributes.REGULAR_ATTRIBUTES;
      myAttr2 = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, Color.GRAY);
    }

    public void customizeCellRenderer(
      final JTree tree,
      final Object value,
      final boolean selected,
      final boolean expanded,
      final boolean leaf,
      final int row,
      final boolean hasFocus
    ) {
      LOG.assertTrue(value != null);
      final Object obj = ((DefaultMutableTreeNode)value).getUserObject();

      if(obj instanceof ComponentItem){
        final ComponentItem item = ((ComponentItem)obj);
        setIcon(item.getSmallIcon());

        final String className = item.getClassName();
        final int lastDotIndex = className.lastIndexOf('.');
        if (lastDotIndex != -1 && lastDotIndex != className.length() - 1/*not the last char in class name*/) {
          append(className.substring(lastDotIndex + 1), myAttr);
          append(" (" , myAttr2);
          append(className.substring(0, lastDotIndex) , myAttr2);
          append(")" , myAttr2);
        }
        else{
          append(className, myAttr);
        }
      }
      else if(obj instanceof GroupItem){
        final GroupItem item = ((GroupItem)obj);
        append(item.getName(), myAttr);
      }
      else if(obj == null/*root node*/){
        // do nothing
      }
      else{
        //noinspection HardCodedStringLiteral
        throw new RuntimeException("unknown object: " + obj);
      }
    }
  }
}
