package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.RadContainer;
import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.IntEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.IntRenderer;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class VGapProperty extends Property{
  private final IntRenderer myRenderer;
  private final IntEditor myEditor;

  public VGapProperty(){
    super(null," Vertical Gap");
    myRenderer = new IntRenderer();
    myEditor = new IntEditor(-1);
  }

  public Object getValue(final RadComponent component){
    if(!(component instanceof RadContainer)){
      throw new IllegalArgumentException("component must be an instance of RadContainer: "+component);
    }
    final RadContainer container=(RadContainer)component;
    final AbstractLayout layoutManager=(AbstractLayout)container.getLayout();
    return new Integer(layoutManager.getVGap());
  }

  protected void setValueImpl(final RadComponent component,final Object value) throws Exception{
    if(!(component instanceof RadContainer)){
      throw new IllegalArgumentException("component must be an instance of RadContainer: "+component);
    }
    final RadContainer container=(RadContainer)component;
    final AbstractLayout layoutManager=(AbstractLayout)container.getLayout();
    layoutManager.setVGap(((Integer)value).intValue());
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
