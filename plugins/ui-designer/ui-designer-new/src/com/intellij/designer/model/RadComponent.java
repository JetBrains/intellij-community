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
import com.intellij.designer.designSurface.ICaption;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.StaticDecorator;
import com.intellij.designer.designSurface.tools.DragTracker;
import com.intellij.designer.designSurface.tools.InputTool;
import com.intellij.designer.propertyTable.RadPropertyTable;
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
public abstract class RadComponent extends PropertiesContainer {
  private static final String ERROR_KEY = "Inspection.Errors";

  protected MetaModel myMetaModel;
  private RadComponent myParent;
  private RadLayout myLayout;
  private final Map<Object, Object> myClientProperties = new HashMap<Object, Object>();

  @Override
  public List<Property> getProperties() {
    return Collections.emptyList();
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // MetaModel
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public MetaModel getMetaModel() {
    return myMetaModel;
  }

  public MetaModel getMetaModelForProperties() throws Exception {
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

  public void add(@NotNull RadComponent component, @Nullable RadComponent insertBefore) {
    component.setParent(this);

    int index;
    List<RadComponent> children = getChildren();
    if (insertBefore == null) {
      index = children.size();
      children.add(component);
    }
    else {
      index = children.indexOf(insertBefore);
      children.add(index, component);
    }

    if (myLayout != null) {
      myLayout.addComponentToContainer(component, index);
    }
  }

  public void remove(@NotNull RadComponent component) {
    getChildren().remove(component);

    if (myLayout != null) {
      myLayout.removeComponentFromContainer(component);
    }

    component.setParent(null);
  }

  public void removeFromParent() {
    getParent().remove(this);
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

  public void addStaticDecorators(List<StaticDecorator> decorators, List<RadComponent> selection) {
  }

  public void addSelectionActions(DesignerEditorPanel designer,
                                  DefaultActionGroup actionGroup,
                                  JComponent shortcuts,
                                  List<RadComponent> selection) {
  }

  @Nullable
  public ICaption getCaption() {
    return null;
  }
  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Layout
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public RadLayout getLayout() {
    return myLayout;
  }

  public void setLayout(@Nullable RadLayout layout) {
    myLayout = layout;
    if (myLayout != null) {
      myLayout.setContainer(this);
    }
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

  public List<Property> getInplaceProperties() throws Exception {
    List<Property> properties = new ArrayList<Property>();

    if (myMetaModel != null) {
      List<Property> allProperties = getProperties();
      for (String name : myMetaModel.getInplaceProperties()) {
        Property property = RadPropertyTable.findProperty(allProperties, name);
        if (property != null) {
          properties.add(property);
        }
      }
    }

    return properties;
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

  @Nullable
  public RadComponent morphingTo(MetaModel target) throws Exception {
    return null;
  }

  @Nullable
  public RadComponent wrapIn(MetaModel target) throws Exception {
    return null;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Utils
  //
  //////////////////////////////////////////////////////////////////////////////////////////


  public static List<ErrorInfo> getError(RadComponent component) {
    List<ErrorInfo> errorInfos = component.getClientProperty(ERROR_KEY);
    return errorInfos == null ? Collections.<ErrorInfo>emptyList() : errorInfos;
  }

  public static void addError(RadComponent component, ErrorInfo errorInfo) {
    List<ErrorInfo> errorInfos = component.getClientProperty(ERROR_KEY);
    if (errorInfos == null) {
      errorInfos = new ArrayList<ErrorInfo>();
      component.setClientProperty(ERROR_KEY, errorInfos);
    }
    errorInfos.add(errorInfo);
  }

  public static void clearErrors(RadComponent component) {
    component.accept(new RadComponentVisitor() {
      @Override
      public void endVisit(RadComponent component) {
        component.extractClientProperty(ERROR_KEY);
      }
    }, true);
  }


  public static Set<RadComponent> getParents(List<RadComponent> components) {
    Set<RadComponent> parents = new HashSet<RadComponent>();
    for (RadComponent component : components) {
      RadComponent parent = component.getParent();
      if (parent != null) {
        parents.add(parent);
      }
    }
    return parents;
  }

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