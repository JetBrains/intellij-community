package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.renderers.InsetsPropertyRenderer;

import java.awt.*;
import java.lang.reflect.Method;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class IntroInsetsProperty extends IntrospectedProperty{
  private final Property[] myChildren;
  private final InsetsPropertyRenderer myRenderer;

  public IntroInsetsProperty(final String name,final Method readMethod,final Method writeMethod){
    super(name, readMethod, writeMethod);
    myChildren=new Property[]{
      new AbstractInsetsProperty.MyTopProperty(this),
      new AbstractInsetsProperty.MyLeftProperty(this),
      new AbstractInsetsProperty.MyBottomProperty(this),
      new AbstractInsetsProperty.MyRightProperty(this)
    };
    myRenderer=new InsetsPropertyRenderer();
  }

  public void write(final Object value,final XmlWriter writer){
    final Insets insets=(Insets)value;
    writer.addAttribute("top",insets.top);
    writer.addAttribute("left",insets.left);
    writer.addAttribute("bottom",insets.bottom);
    writer.addAttribute("right",insets.right);
  }

  public Property[] getChildren(){
    return myChildren;
  }

  public PropertyRenderer getRenderer(){
    return myRenderer;
  }

  public PropertyEditor getEditor(){
    return null;
  }
}
