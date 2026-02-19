// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class Property<T extends RadComponent, V> implements IProperty {
  public static final Property[] EMPTY_ARRAY=new Property[]{};

  private static final Logger LOG = Logger.getInstance(Property.class);

  /**
   * Parent property
   */
  private final Property myParent;
  /**
   * Propertie's name
   */
  private final String myName;

  public Property(final Property parent, final @NotNull @NonNls String name) {
    myParent = parent;
    myName = name;
  }

  /**
   * @return property's name.
   */
  @Override
  public final @NotNull
  @NlsSafe String getName() {
    return myName;
  }

  @Override
  public Object getPropertyValue(final IComponent component) {
    //noinspection unchecked
    return getValue((T) component);
  }

  /**
   * This method can extract value of the property from the
   * instance of the RadComponent. This value is passed to the
   * PropertyRenderer and PropertyEditor.
   */
  public abstract V getValue(T component);

  /**
   * Do not invoke this method outside Property class, bacuse
   * {@code setValue(Component,Object)} does some additional work.
   * This method exists only for convenience.
   *
   * @see #setValue(RadComponent,Object)
   */
  protected abstract void setValueImpl(T component, V value) throws Exception;


  /**
   * Sets the {@code value} of the property. This method is invoked
   * after editing is complete.
   *
   * @param component component which property should be set
   * @param value new propertie's value
   *
   * @exception Exception if passed {@code value} cannot
   * be applied to the {@code component}. Note, the exception's
   * message will be shown to the user.
   */
  public final void setValue(final T component, final V value) throws Exception{
    setValueImpl(component, value);
    markTopmostModified(component, true);
    component.getDelegee().invalidate();
  }

  public final void setValueEx(T component, V value) {
    try {
      setValue(component, value);
    }
    catch(Exception ex) {
      LOG.error(ex);
    }
  }

  protected void markTopmostModified(final T component, final boolean modified) {
    Property topmostParent = this;
    while (topmostParent.getParent() != null) {
      topmostParent = topmostParent.getParent();
    }
    if (modified) {
      component.markPropertyAsModified(topmostParent);
    }
    else {
      component.removeModifiedProperty(topmostParent);
    }
  }

  /**
   * @return property which is the parent for this property.
   * The method can return {@code null} if the property
   * doesn't have parent.
   */
  public final @Nullable Property getParent() {
    return myParent;
  }

  /**
   * @return child properties.
   */
  public Property @NotNull [] getChildren(final RadComponent component) {
    return EMPTY_ARRAY;
  }

  /**
   * @return property's renderer.
   */
  public abstract @NotNull PropertyRenderer<V> getRenderer();

  /**
   * @return property's editor. The method allows to return {@code null}.
   * In this case property is not editable.
   */
  public abstract @Nullable PropertyEditor<V> getEditor();

  public boolean appliesTo(T component) {
    return true;
  }

  public boolean isModified(final T component) {
    return false;
  }

  public void resetValue(T component) throws Exception {
  }

  public boolean appliesToSelection(List<RadComponent> selection) {
    return true;
  }

  public boolean needRefreshPropertyList() {
    return false;
  }
}
