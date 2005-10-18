package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.RadContainer;
import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.BooleanEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.BooleanRenderer;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class SameSizeVerticallyProperty extends Property{
  private final BooleanRenderer myRenderer;
  private final BooleanEditor myEditor;

  public SameSizeVerticallyProperty(){
    super(null,"Same Size Vertically");
    myRenderer = new BooleanRenderer();
    myEditor = new BooleanEditor();
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
    return new Boolean(gridLayoutManager.isSameSizeVertically());
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
    gridLayoutManager.setSameSizeVertically(((Boolean)value).booleanValue());
  }

  public Property[] getChildren(){
    return EMPTY_ARRAY;
  }

  public PropertyRenderer getRenderer(){
    return myRenderer;
  }

  public PropertyEditor getEditor(){
    return myEditor;
  }
}
