// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.componentTree;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.LwInspectionSuppression;
import com.intellij.uiDesigner.radComponents.RadButtonGroup;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

final class ComponentTreeStructure extends AbstractTreeStructure {
  private static final Logger LOG = Logger.getInstance(ComponentTreeStructure.class);

  private final Object myRootElement;
  private final GuiEditor myEditor;

  ComponentTreeStructure(final @NotNull GuiEditor editor) {
    myRootElement = new ComponentTreeStructureRoot();
    myEditor = editor;
  }

  @Override
  public @NotNull Object getRootElement() {
    return myRootElement;
  }

  @Override
  public Object @NotNull [] getChildElements(final @NotNull Object element) {
    if (element == myRootElement) {
      ArrayList<Object> elements = new ArrayList<>();
      final RadRootContainer rootContainer = myEditor.getRootContainer();
      elements.add(new ComponentPtr(myEditor, rootContainer));
      final LwInspectionSuppression[] suppressions = rootContainer.getInspectionSuppressions();
      if (suppressions.length > 0) {
        elements.add(suppressions);
      }
      RadButtonGroup[] buttonGroups = rootContainer.getButtonGroups();
      if (buttonGroups.length > 0) {
        elements.add(buttonGroups);
      }
      return elements.toArray();
    }
    else if (element instanceof ComponentPtr ptr) {
      LOG.assertTrue(ptr.isValid()); // pointer must be valid
      final RadComponent component = ptr.getComponent();
      if (component instanceof RadContainer container) {
        final ComponentPtr[] ptrs = new ComponentPtr[container.getComponentCount()];
        for (int i = 0; i < ptrs.length; i++) {
          ptrs[i] = new ComponentPtr(myEditor, container.getComponent(i));
        }
        return ptrs;
      }
      else {
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
      }
    }
    else if (element instanceof LwInspectionSuppression[]) {
      ArrayList<LwInspectionSuppression> result = new ArrayList<>();
      for (LwInspectionSuppression suppression : (LwInspectionSuppression[])element) {
        if (suppression.getComponentId() == null ||
            FormEditingUtil.findComponent(myEditor.getRootContainer(), suppression.getComponentId()) != null) {
          result.add(suppression);
        }
      }
      return ArrayUtil.toObjectArray(result);
    }
    else if (element instanceof RadButtonGroup[]) {
      return (RadButtonGroup[])element;
    }
    else if (element instanceof LwInspectionSuppression || element instanceof RadButtonGroup) {
      return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }
    else {
      throw new IllegalArgumentException("unknown element: " + element);
    }
  }

  @Override
  public Object getParentElement(final @NotNull Object element) {
    if (element instanceof ComponentTreeStructureRoot) {
      return null;
    }
    else if (element instanceof LwInspectionSuppression[] || element instanceof RadButtonGroup[]) {
      return myRootElement;
    }
    else if (element instanceof LwInspectionSuppression) {
      return myEditor.getRootContainer().getInspectionSuppressions();
    }
    else if (element instanceof RadButtonGroup) {
      return myEditor.getRootContainer().getButtonGroups();
    }
    else if (element instanceof ComponentPtr ptr) { // RadContainer is also RadComponent
      if (!ptr.isValid()) return myRootElement;
      final RadComponent component = ptr.getComponent();
      if (component instanceof RadRootContainer) {
        return myRootElement;
      }
      else {
        return component.getParent() != null ? new ComponentPtr(myEditor, component.getParent(), false) : null;
      }
    }
    else {
      throw new IllegalArgumentException("unknown element: " + element);
    }
  }

  @Override
  public @NotNull NodeDescriptor<?> createDescriptor(final @NotNull Object element, final NodeDescriptor parentDescriptor) {
    if (element == myRootElement) {
      return new RootDescriptor(parentDescriptor, myRootElement);
    }
    else if (element instanceof ComponentPtr) {
      return new ComponentPtrDescriptor(parentDescriptor, (ComponentPtr)element);
    }
    else if (element instanceof LwInspectionSuppression[]) {
      return new SuppressionGroupDescriptor(parentDescriptor, (LwInspectionSuppression[])element);
    }
    else if (element instanceof LwInspectionSuppression suppression) {
      RadComponent target = (RadComponent)(suppression.getComponentId() == null
                                           ? null
                                           : FormEditingUtil.findComponent(myEditor.getRootContainer(), suppression.getComponentId()));
      return new SuppressionDescriptor(parentDescriptor, target, suppression);
    }
    else if (element instanceof RadButtonGroup[]) {
      return new ButtonGroupListDescriptor(parentDescriptor, (RadButtonGroup[])element);
    }
    else if (element instanceof RadButtonGroup) {
      return new ButtonGroupDescriptor(parentDescriptor, (RadButtonGroup)element);
    }
    else {
      throw new IllegalArgumentException("unknown element: " + element);
    }
  }

  /**
   * Only tree root (it's invisible) node and RadRootContainer are auto-expanded
   */
  boolean isAutoExpandNode(final @NotNull NodeDescriptor<?> descriptor) {
    final Object element = descriptor.getElement();
    return element == myRootElement || element == myEditor.getRootContainer();
  }

  @Override
  public void commit() {}

  /**
   * Nothing to commit
   */
  @Override
  public boolean hasSomethingToCommit() {
    return false;
  }

  private class ComponentTreeStructureRoot {
    @Override
    public String toString() {
      return "root of component tree structure " + ComponentTreeStructure.this;
    }
  }
}
