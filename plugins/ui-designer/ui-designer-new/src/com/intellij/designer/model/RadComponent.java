/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.designer.model;

import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.tools.DragTracker;
import com.intellij.designer.designSurface.tools.InputTool;
import com.intellij.designer.propertyTable.Property;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class RadComponent {
  protected MetaModel myMetaModel;
  private RadComponent myParent;
  private RadLayout myLayout;
  private final Map<Object, Object> myClientProperties = new HashMap<Object, Object>();

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // MetaModel
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public MetaModel getMetaModel() {
    return myMetaModel;
  }

  public void setMetaModel(MetaModel metaModel) {
    myMetaModel = metaModel;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Hierarchy
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public RadComponent getRoot() {
    return myParent == null ? this : myParent.getRoot();
  }

  public final RadComponent getParent() {
    return myParent;
  }

  public final void setParent(RadComponent parent) {
    myParent = parent;
  }

  public List<RadComponent> getChildren() {
    return Collections.emptyList();
  }

  public Object[] getTreeChildren() {
    return getChildren().toArray();
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Visual
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public Rectangle getBounds() {
    return null;
  }

  public Rectangle getBounds(Component relativeTo) {
    return null;
  }

  public Point convertPoint(Component relativeFrom, int x, int y) {
    return null;
  }

  public InputTool getDragTracker() {
    return new DragTracker(this);
  }

  public void processDropOperation(OperationContext context) {
  }

  public void addSelectionActions(DesignerEditorPanel designer,
                                  DefaultActionGroup actionGroup,
                                  JComponent shortcuts,
                                  List<RadComponent> selection) {
  }
  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // layout
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public RadLayout getLayout() {
    return myLayout;
  }

  public void setLayout(RadLayout layout) {
    myLayout = layout;
    myLayout.setContainer(this);
  }

  @Nullable
  public RadLayoutData getLayoutData() {
    return null;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Properties
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public List<Property> getProperties() {
    return Collections.emptyList();
  }

  @SuppressWarnings("unchecked")
  public final <T> T getClientProperty(@NotNull String key) {
    return (T)myClientProperties.get(key);
  }

  @SuppressWarnings("unchecked")
  public final <T> T extractClientProperty(@NotNull String key) {
    return (T)myClientProperties.remove(key);
  }

  public final void setClientProperty(@NotNull Object key, Object value) {
    myClientProperties.put(key, value);
  }


  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Visitor
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public void accept(RadComponentVisitor visitor, boolean forward) {
    if (visitor.visit(this)) {
      List<RadComponent> children = getChildren();
      if (forward) {
        for (RadComponent child : children) {
          child.accept(visitor, forward);
        }
      }
      else {
        int size = children.size();
        for (int i = size - 1; i >= 0; i--) {
          children.get(i).accept(visitor, forward);
        }
      }
      visitor.endVisit(this);
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Operations
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public boolean canDelete() {
    return myMetaModel == null || myMetaModel.canDelete();
  }

  public void delete() throws Exception {
  }

  public void copyTo(Element parentElement) throws Exception {
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Utils
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public static boolean isParentsContainedIn(List<RadComponent> components, RadComponent component) {
    RadComponent parent = component.getParent();
    while (parent != null) {
      if (components.contains(parent)) {
        return true;
      }
      parent = parent.getParent();
    }
    return false;
  }

  public static List<RadComponent> getPureSelection(List<RadComponent> selection) {
    List<RadComponent> components = new ArrayList<RadComponent>();

    for (RadComponent component : selection) {
      if (!isParentsContainedIn(selection, component)) {
        components.add(component);
      }
    }

    return components;
  }
}