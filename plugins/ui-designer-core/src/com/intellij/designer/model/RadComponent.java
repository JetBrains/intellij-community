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
import com.intellij.designer.propertyTable.PropertyTable;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
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
  private final Map<Object, Object> myClientProperties = new HashMap<>();

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

  public final <T extends RadComponent> T getParent(Class<T> clazz) {
    return (T)myParent;
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

  /**
   * Whether this view is considered to be the background.
   * This can for example return true for the root layout, such that marquee
   * selection, and Select All, will not include it in marquee selection or select
   * all operations.
   *
   * @return true if this view should be considered part of the background
   */
  public boolean isBackground() {
    return false;
  }

  /**
   * Returns true if this component is of the same logical type as the given other component.
   * This is used to for example select "Select Same Type".
   */
  public boolean isSameType(@NotNull RadComponent other) {
    return other.getClass() == this.getClass();
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Visual
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Returns the bounds of this {@linkplain RadComponent} in the model.
   * <p/>
   * Caller should <b>not</b> modify this rectangle.
   *
   * @return the bounds of this {@linkplain RadComponent} in the model coordinate system
   *         (e.g. unaffected by a view zoom for example)
   */
  public Rectangle getBounds() {
    return null;
  }

  /**
   * Returns the bounds of this {@linkplain RadComponent} in the coordinate system of
   * the given Swing component. This will scale the coordinates if the view is zoomed
   * (see {@link DesignerEditorPanel#zoom(com.intellij.designer.designSurface.ZoomType)})
   * and will apply any relative position offsets of views in the hierarchy between the
   * two components.
   * <p/>
   * Returns a new {@link Rectangle}, so callers are free to modify the result.
   *
   * @param relativeTo the component whose coordinate system the model bounds should
   *                   be shifted and scaled into
   * @return the bounds of this {@linkplain RadComponent} in the given coordinate system
   */
  public Rectangle getBounds(Component relativeTo) {
    return null;
  }

  /**
   * Converts the given rectangle (in model coordinates) to coordinates in the given
   * target component's coordinate system. The model coordinate system refers to
   * the same coordinate system as the bounds returned by {@link #getBounds()}.
   * <p/>
   * This means that calling {@link #getBounds(java.awt.Component)} is equivalent
   * to calling this method and passing in {@link #getBounds()}.
   * <p/>
   * Returns a new {@link Rectangle}, so callers are free to modify the result.
   *
   * @param target    the component whose coordinate system the rectangle should be
   *                  translated into
   * @param rectangle the model rectangle to convert
   * @return the rectangle converted to the coordinate system of the target
   */
  public Rectangle fromModel(@NotNull Component target, @NotNull Rectangle rectangle) {
    return null;
  }

  /**
   * Converts the given rectangle (in coordinates relative to the given component)
   * into the equivalent rectangle in model coordinates.
   * <p/>
   * Returns a new {@link Rectangle}, so callers are free to modify the result.
   *
   * @param source    the component which defines the coordinate system of the rectangle
   * @param rectangle the rectangle to be converted into model coordinates
   * @return the rectangle converted to the model coordinate system
   */
  public Rectangle toModel(@NotNull Component source, @NotNull Rectangle rectangle) {
    return null;
  }

  /**
   * Converts the given point (in model coordinates) to coordinates in the given
   * target component's coordinate system. The model coordinate system refers to
   * the same coordinate system as the bounds returned by {@link #getBounds()}.
   * <p/>
   * Returns a new {@link Point}, so callers are free to modify the result.
   *
   * @param target the component whose coordinate system the point should be
   *               translated into
   * @param point  the model point to convert
   * @return the point converted to the coordinate system of the target
   */
  public Point fromModel(@NotNull Component target, @NotNull Point point) {
    return null;
  }

  /**
   * Converts the given point (in coordinates relative to the given component)
   * into the equivalent point in model coordinates.
   * <p/>
   * Returns a new {@link Point}, so callers are free to modify the result.
   *
   * @param source the component which defines the coordinate system of the point
   * @param point  the point to be converted into model coordinates
   * @return the point converted to the model coordinate system
   */
  public Point toModel(@NotNull Component source, @NotNull Point point) {
    return null;
  }

  /**
   * Converts the given rectangle (in model coordinates) to coordinates in the given
   * target component's coordinate system. The model coordinate system refers to
   * the same coordinate system as the bounds returned by {@link #getBounds()}.
   * <p/>
   * Returns a new {@link Dimension}, so callers are free to modify the result.
   *
   * @param target the component whose coordinate system the dimension should be
   *               translated into
   * @param size   the model dimension to convert
   * @return the size converted to the coordinate system of the target
   */
  public Dimension fromModel(@NotNull Component target, @NotNull Dimension size) {
    return null;
  }

  /**
   * Converts the given size (in coordinates relative to the given component)
   * into the equivalent size in model coordinates.
   * <p/>
   * Returns a new {@link Dimension}, so callers are free to modify the result.
   *
   * @param source the component which defines the coordinate system of the size
   * @param size   the dimension to be converted into model coordinates
   * @return the size converted to the model coordinate system
   */
  public Dimension toModel(@NotNull Component source, @NotNull Dimension size) {
    return null;
  }

  /**
   * Returns the point in the model coordinate space (see {@link #getBounds()}) given
   * a coordinate {@code x, y} in the given Swing component.
   * <p/>
   * Returns a new {@link Point}, so callers are free to modify the result.
   *
   * @param relativeFrom the component whose coordinate system defines the rectangle
   * @param x            the x coordinate of the point
   * @param y            the y coordinate of the point
   * @return a corresponding {@link Point} in the model coordinate system
   */
  public Point convertPoint(Component relativeFrom, int x, int y) {
    return null;
  }

  public InputTool getDragTracker(Point location, InputEvent event, boolean isTree) {
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

  @Override
  public List<Property> getProperties() {
    return Collections.emptyList();
  }

  public List<Property> getProperties(String key) {
    return Collections.emptyList();
  }

  @Nullable
  public String getPropertyValue(String name) {
    if (getProperties() == null) {
      throw new NullPointerException("Component " +
                                     this +
                                     ", " +
                                     myLayout +
                                     ", " +
                                     myMetaModel.getTag() +
                                     ", " +
                                     myMetaModel.getTarget() +
                                     " without properties");
    }
    Property property = PropertyTable.findProperty(getProperties(), name);
    if (property != null) {
      try {
        return String.valueOf(property.getValue(this));
      }
      catch (Exception e) {
        return null;
      }
    }
    return null;
  }

  public List<Property> getInplaceProperties() throws Exception {
    List<Property> properties = new ArrayList<>();

    if (myMetaModel != null) {
      List<Property> allProperties = getProperties();
      for (String name : myMetaModel.getInplaceProperties()) {
        Property property = PropertyTable.findProperty(allProperties, name);
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
      acceptChildren(visitor, forward);
      visitor.endVisit(this);
    }
  }

  public void acceptChildren(RadComponentVisitor visitor, boolean forward) {
    List<RadComponent> children = getChildrenForAccept(visitor);
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
  }

  protected List<RadComponent> getChildrenForAccept(RadComponentVisitor visitor) {
    return getChildren();
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
      errorInfos = new ArrayList<>();
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

  /**
   * Partitions the given list of components into a map where each value is a list of siblings,
   * in the same order as in the original list, and where the keys are the parents (or null
   * for the components that do not have a parent).
   * <p/>
   * The value lists will never be empty. The parent key will be null for components without parents.
   *
   * @param components the components to be grouped
   * @return a map from parents (or null) to a list of components with the corresponding parent
   */
  @NotNull
  public static Map<RadComponent, List<RadComponent>> groupSiblings(@NotNull List<? extends RadComponent> components) {
    Map<RadComponent, List<RadComponent>> siblingLists = new HashMap<>();

    if (components.isEmpty()) {
      return siblingLists;
    }
    if (components.size() == 1) {
      RadComponent component = components.get(0);
      siblingLists.put(component.getParent(), Collections.singletonList(component));
      return siblingLists;
    }

    for (RadComponent component : components) {
      RadComponent parent = component.getParent();
      List<RadComponent> children = siblingLists.get(parent);
      if (children == null) {
        children = new ArrayList<>();
        siblingLists.put(parent, children);
      }
      children.add(component);
    }

    return siblingLists;
  }

  public static Set<RadComponent> getParents(List<RadComponent> components) {
    Set<RadComponent> parents = new HashSet<>();
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
    List<RadComponent> components = new ArrayList<>();

    for (RadComponent component : selection) {
      if (!isParentsContainedIn(selection, component)) {
        components.add(component);
      }
    }

    return components;
  }

  public boolean isAncestorFor(@NotNull RadComponent component, boolean strict) {
    RadComponent parent = strict ? component.getParent() : component;
    while (true) {
      if (parent == null) {
        return false;
      }
      if (parent == this) {
        return true;
      }
      parent = parent.getParent();
    }
  }
}