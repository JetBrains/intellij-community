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

import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.tools.DragTracker;
import com.intellij.designer.designSurface.tools.InputTool;
import com.intellij.designer.propertyTable.Property;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Alexander Lobas
 */
public abstract class RadComponent {
  protected MetaModel myMetaModel;
  private RadComponent myParent;
  private RadLayout myLayout;
  private final Map<Object, Object> myClientProperties = new HashMap<Object, Object>();

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
    return null;
  }

  public final Object getClientProperty(@NotNull Object key) {
    return myClientProperties.get(key);
  }

  public final void putClientProperty(@NotNull Object key, Object value) {
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
}