// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.componentTree;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.HierarchyChangeListener;
import com.intellij.uiDesigner.SelectionWatcher;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.propertyInspector.DesignerToolWindowManager;
import com.intellij.uiDesigner.propertyInspector.PropertyInspector;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class ComponentTreeBuilder implements Disposable {
  private static final Logger LOG = Logger.getInstance(ComponentTreeBuilder.class);

  private final GuiEditor myEditor;
  private final MySelectionWatcher mySelectionWatcher;
  private final StructureTreeModel<ComponentTreeStructure> myStructureTreeModel;
  private final ComponentTree myTree;
  private final ComponentTreeStructure myTreeStructure;
  /**
   * More than 0 if we are inside some change. In this case we have not
   * react on our own events.
   */
  private int myInsideChange;
  private final MyHierarchyChangeListener myHierarchyChangeListener;
  private MyTreeSelectionListener myTreeSelectionListener;

  public ComponentTreeBuilder(final ComponentTree tree, final @NotNull GuiEditor editor) {
    myTree = tree;
    myTreeStructure = new ComponentTreeStructure(editor);
    myStructureTreeModel = new StructureTreeModel<>(myTreeStructure, MyComparator.ourComparator, this);
    tree.setModel(new AsyncTreeModel(myStructureTreeModel, this));

    myEditor = editor;
    mySelectionWatcher = new MySelectionWatcher(editor);
    mySelectionWatcher.setupListeners();

    syncSelection();

    myTreeSelectionListener = new MyTreeSelectionListener();
    myHierarchyChangeListener = new MyHierarchyChangeListener();
    myTree.getSelectionModel().addTreeSelectionListener(myTreeSelectionListener);
    editor.addHierarchyChangeListener(myHierarchyChangeListener);
  }


  @Override
  public void dispose() {
    myEditor.removeHierarchyChangeListener(myHierarchyChangeListener);
    if (myTreeSelectionListener != null) {
      myTree.getSelectionModel().removeTreeSelectionListener(myTreeSelectionListener);
      myTreeSelectionListener = null;
    }
    mySelectionWatcher.dispose();
  }

  // TODO Support auto expand for async tree model?
  @SuppressWarnings("unused")
  private boolean isAutoExpandNode(final NodeDescriptor<?> descriptor){
    return myTreeStructure.isAutoExpandNode(descriptor);
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
  @RequiresEdt
  private void syncSelection() {
    // Found selected components
    final RadContainer rootContainer = myEditor.getRootContainer();
    final ArrayList<RadComponent> selection = new ArrayList<>();
    FormEditingUtil.iterate(
      rootContainer,
      new FormEditingUtil.ComponentVisitor<RadComponent>() {
        @Override
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

    List<Promise<TreeVisitor>> treeVisitors = new ArrayList<>(selection.size());
    for (RadComponent s : selection) {
      ComponentPtr componentPtr = new ComponentPtr(myEditor, s);
      treeVisitors.add(myStructureTreeModel.promiseVisitor(componentPtr));
    }

    // Set selection in the tree
    Promises.collectResults(treeVisitors).onProcessed(visitors -> {
      TreeUtil.promiseSelect(myTree, visitors.stream()).onProcessed(__ -> {
        // Notify the ComponentTree that selected component changed
        myEditor.fireSelectedComponentChanged();
      });
    });
  }

  /**
   * Compares RadComponent based on their natural order in the container.
   */
  private static final class MyComparator implements Comparator<NodeDescriptor<?>>{
    public static final MyComparator ourComparator=new MyComparator();

    private static int indexOf(final RadContainer container, final RadComponent component){
      if (container != null) {
        for(int i = container.getComponentCount() - 1; i >= 0 ; i--){
          if(component.equals(container.getComponent(i))){
            return i;
          }
        }
      }
      return -1;
    }

    @Override
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
    @Override
    public void hierarchyChanged(){
      invalidateAndSyncSelection();
    }
  }

  /**
   * Synchronizes selection in the tree with selection in the editor
   */
  private final class MySelectionWatcher extends SelectionWatcher{
    MySelectionWatcher(final GuiEditor editor) {
      super(editor);
    }

    @Override
    protected void selectionChanged(final RadComponent component, final boolean ignored) {
      updateSelection();
    }
  }

  public void invalidateAsync() {
    myStructureTreeModel.invalidateAsync();
  }

  private void invalidateAndSyncSelection() {
    myStructureTreeModel.invalidateAsync().thenRun(() -> {
      ApplicationManager.getApplication().invokeLater(() -> {
        // After updating the tree we have to synchronize the selection in the tree
        // with selected element in the hierarchy
        syncSelection();
      });
    });
  }

  private void updateSelection() {
    final PropertyInspector propertyInspector = DesignerToolWindowManager.getInstance(myEditor).getPropertyInspector();
    if (propertyInspector.isEditing()) {
      propertyInspector.stopEditing();
    }

    invalidateAndSyncSelection();
  }

  /**
   * Synchronizes GuiEditor with the tree
   */
  private final class MyTreeSelectionListener implements TreeSelectionListener {
    @Override
    public void valueChanged(final TreeSelectionEvent e) {
      if (myInsideChange>0) {
        return;
      }

      final Set<ComponentPtr> selectedElements = TreeUtil.collectSelectedObjectsOfType(myTree, ComponentPtrDescriptor.class)
        .stream()
        .map(descriptor -> descriptor.getElement())
        .collect(Collectors.toSet());

      myInsideChange++;
      try{
        FormEditingUtil.clearSelection(myEditor.getRootContainer());
        boolean hasComponentInTab = false;
        int count = 0;
        for(ComponentPtr ptr: selectedElements) {
          ptr.validate();
          if(ptr.isValid()) {
            final RadComponent component=ptr.getComponent();
            LOG.assertTrue(component!=null);
            if (!hasComponentInTab) {
              hasComponentInTab = FormEditingUtil.selectComponent(myEditor, component);
            }
            else {
              component.setSelected(true);
            }
            if (++count == selectedElements.size()) {
              myEditor.scrollComponentInView(component);
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
