/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.editors.BooleanEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.BooleanRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

/**
 * @author yole
 */
public abstract class AbstractGridLayoutProperty extends Property {
  protected final BooleanRenderer myRenderer = new BooleanRenderer();
  protected final BooleanEditor myEditor = new BooleanEditor();

  public AbstractGridLayoutProperty(final Property parent, @NotNull @NonNls final String name) {
    super(parent, name);
  }

  public Object getValue(final RadComponent component){
    if(!(component instanceof RadContainer)){
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("component must be an instance of RadContainer: "+component);
    }
    final RadContainer container=(RadContainer)component;
    final AbstractLayout layoutManager=(AbstractLayout)container.getLayout();
    if (!(layoutManager instanceof GridLayoutManager)) {
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("grid layout expected: "+layoutManager);
    }
    final GridLayoutManager gridLayoutManager = (GridLayoutManager)layoutManager;
    return getGridLayoutPropertyValue(gridLayoutManager);
  }

  protected void setValueImpl(final RadComponent component,final Object value) throws Exception{
    if(!(component instanceof RadContainer)){
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("component must be an instance of RadContainer: "+component);
    }
    final RadContainer container=(RadContainer)component;
    final AbstractLayout layoutManager=(AbstractLayout)container.getLayout();
    if (!(layoutManager instanceof GridLayoutManager)) {
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("grid layout expected: "+layoutManager);
    }
    final GridLayoutManager gridLayoutManager = (GridLayoutManager)layoutManager;
    final boolean booleanValue = ((Boolean)value).booleanValue();
    setGridLayoutPropertyValue(gridLayoutManager, booleanValue);
  }

  protected abstract boolean getGridLayoutPropertyValue(GridLayoutManager gridLayoutManager);

  protected abstract void setGridLayoutPropertyValue(GridLayoutManager gridLayoutManager, boolean booleanValue);

  @NotNull
  public PropertyRenderer getRenderer(){
    return myRenderer;
  }

  public PropertyEditor getEditor(){
    return myEditor;
  }

  @Override public boolean isModified(final RadComponent component) {
    return ((Boolean) getValue(component)).booleanValue();
  }

  @Override public void resetValue(RadComponent component) throws Exception {
    setValueImpl(component, Boolean.FALSE);
  }
}
