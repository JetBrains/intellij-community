package com.intellij.uiDesigner.componentTree;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.StatusBarProgress;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.HierarchyChangeListener;
import com.intellij.uiDesigner.SelectionWatcher;
import com.intellij.uiDesigner.propertyInspector.PropertyInspector;
import com.intellij.uiDesigner.propertyInspector.UIDesignerToolWindowManager;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ComponentTreeBuilder extends AbstractTreeBuilder{
  private static final Logger LOG = Logger.getInstance("#com.intellij.componentTree.ComponentTreeBuilder");

  private final GuiEditor myEditor;
  private final MySelectionWatcher mySelectionWatcher;
  /**
   * More then 0 if we are inside some change. In this case we have not
   * react on our own events.
   */
  private int myInsideChange;
  private ComponentTreeBuilder.MyHierarchyChangeListener myHierarchyChangeListener;
  private ComponentTreeBuilder.MyTreeSelectionListener myTreeSelectionListener;

  public ComponentTreeBuilder(final ComponentTree tree, @NotNull final GuiEditor editor){
    super(tree,(DefaultTreeModel)tree.getModel(),null,MyComparator.ourComparator);

    myEditor = editor;
    mySelectionWatcher = new MySelectionWatcher(editor);
    myTreeStructure = new ComponentTreeStructure(editor);

    initRootNode();
    syncSelection();

    myTreeSelectionListener = new MyTreeSelectionListener();
    myHierarchyChangeListener = new MyHierarchyChangeListener();
    myTree.getSelectionModel().addTreeSelectionListener(myTreeSelectionListener);
    editor.addHierarchyChangeListener(myHierarchyChangeListener);
  }

  public void dispose() {
    myEditor.removeHierarchyChangeListener(myHierarchyChangeListener);
    myTree.getSelectionModel().removeTreeSelectionListener(myTreeSelectionListener);
    mySelectionWatcher.dispose();
    super.dispose();
  }

  private ComponentTreeStructure getComponentTreeStructure(){
    return (ComponentTreeStructure)myTreeStructure;
  }

  protected boolean isAlwaysShowPlus(final NodeDescriptor descriptor){
    return false;
  }

  protected boolean isAutoExpandNode(final NodeDescriptor descriptor){
    return getComponentTreeStructure().isAutoExpandNode(descriptor);
  }

  public void beginUpdateSelection() {
    myInsideChange++;
  }

  public void endUpdateSelection() {
    myInsideChange--;
    updateSelection();
  }

  /**
   * This method synchronizes selection in the tree with the selected
   * RadComponent in the component hierarchy
   */
  private void syncSelection(){
    // Found selected components
    final RadContainer rootContainer=myEditor.getRootContainer();
    final ArrayList<RadComponent> selection = new ArrayList<RadComponent>();
    FormEditingUtil.iterate(
      rootContainer,
      new FormEditingUtil.ComponentVisitor<RadComponent>() {
        public boolean visit(final RadComponent component) {
          if(component.isSelected()){
            selection.add(component);
          }
          return true;
        }
      }
    );
    if(selection.size() == 0){
      // If there is no selected component in the hierarchy, then
      // we have to select RadRootContainer
      selection.add(rootContainer);
    }

    // Set selection in the tree
    myTree.clearSelection();
    TreePath firstSelectedPath = null;
    for(int i = selection.size() - 1; i >= 0; i--){
      final ComponentPtr ptr=new ComponentPtr(myEditor, selection.get(i));
      buildNodeForElement(ptr);
      final DefaultMutableTreeNode nodeForElement=getNodeForElement(ptr);
      if (nodeForElement == null) {
        //TODO[anton,vova] investigate!!!
        return;
      }

//      LOG.assertTrue(nodeForElement!=null);
      // Add selected path and scroll it to visible area
      final TreePath selectedPath=new TreePath(nodeForElement.getPath());
      myTree.addSelectionPath(selectedPath);
      if(firstSelectedPath == null){
        firstSelectedPath = selectedPath;
      }
    }
    LOG.assertTrue(firstSelectedPath != null);
    myTree.scrollPathToVisible(firstSelectedPath);

    // Notify the ComponentTree that selected component changed
    myEditor.fireSelectedComponentChanged();
  }

  @NotNull
  protected ProgressIndicator createProgressIndicator() {
    return new StatusBarProgress();
  }

  /**
   * Compares RadComponent based on their natural order in the container.
   */
  private static final class MyComparator implements Comparator<NodeDescriptor>{
    public static final MyComparator ourComparator=new MyComparator();

    private static int indexOf(final RadContainer container, final RadComponent component){
      for(int i = container.getComponentCount() - 1; i >= 0 ; i--){
        if(component.equals(container.getComponent(i))){
          return i;
        }
      }
      return -1;
    }

    public int compare(final NodeDescriptor descriptor1, final NodeDescriptor descriptor2) {
      if (descriptor1 instanceof ComponentPtrDescriptor && descriptor2 instanceof ComponentPtrDescriptor) {
        final RadComponent component1 = ((ComponentPtrDescriptor)descriptor1).getComponent();
        final RadComponent component2 = ((ComponentPtrDescriptor)descriptor2).getComponent();
        if (component1 == null || component2 == null) {
          return 0;
        }
        final RadContainer container1 = component1.getParent();
        final RadContainer container2 = component2.getParent();
        if(Comparing.equal(container1, container2)){
          return indexOf(container1, component1) - indexOf(container2, component2);
        }
        else{
          return 0;
        }
      }else{
        return 0;
      }
    }
  }

  /**
   * Synchronizes tree with GuiEditor
   */
  private final class MyHierarchyChangeListener implements HierarchyChangeListener{
    public void hierarchyChanged(){
      if(myInsideChange>0){
        return;
      }

      myInsideChange++;
      try{
        updateFromRoot();
        // After updating the tree we have to synchronize the selection in the tree
        // with selected elemenet in the hierarchy
        syncSelection();
      }finally{
        myInsideChange--;
      }
    }
  }

  /**
   * Synchronizes selection in the tree with selection in the editor
   */
  private final class MySelectionWatcher extends SelectionWatcher{
    public MySelectionWatcher(final GuiEditor editor) {
      super(editor);
    }

    protected void selectionChanged(final RadComponent component, final boolean ignored) {
      updateSelection();
    }
  }

  private void updateSelection() {
    final PropertyInspector propertyInspector = UIDesignerToolWindowManager.getInstance(myEditor.getProject()).getPropertyInspector();
    if (propertyInspector.isEditing()) {
      propertyInspector.stopEditing();
    }

    if(myInsideChange > 0){
      return;
    }
    myInsideChange++;
    try {
      updateFromRoot();
      syncSelection();
    } finally {
      myInsideChange--;
    }
  }

  /**
   * Synchronizes GuiEditor with the tree
   */
  private final class MyTreeSelectionListener implements TreeSelectionListener{
    public void valueChanged(final TreeSelectionEvent e){
      if(myInsideChange>0){
        return;
      }
      final TreePath[] paths = myTree.getSelectionPaths();
      if(paths==null){
        return;
      }

      myInsideChange++;
      try{
        FormEditingUtil.clearSelection(myEditor.getRootContainer());
        boolean hasComponentInTab = false;
        for(int i = paths.length - 1; i >= 0; i--){
          final DefaultMutableTreeNode lastComponent=(DefaultMutableTreeNode)paths[i].getLastPathComponent();
          LOG.assertTrue(lastComponent!=null);

          final ComponentPtrDescriptor descriptor=(ComponentPtrDescriptor)lastComponent.getUserObject();
          if(descriptor==null){
            // It can happen when  node is being collapsing and some of its children
            // is selected. In that case AbstractTreeBuilder (Valentin) destroyProcess all children nodes and
            // mutable nodes contain nulls.
            return;
          }

          final ComponentPtr ptr=(ComponentPtr)descriptor.getElement();
          if(ptr != null && ptr.isValid()){
            final RadComponent component=ptr.getComponent();
            LOG.assertTrue(component!=null);
            if (!hasComponentInTab) {
              hasComponentInTab = FormEditingUtil.selectComponent(myEditor, component);
            }
            else {
              component.setSelected(true);
            }
          }
        }

        // Notify ComponentTree that selected component changed
        myEditor.fireSelectedComponentChanged();
      }finally{
        myInsideChange--;
      }
    }
  }
}