package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.renderers.InsetsPropertyRenderer;
import org.jetbrains.annotations.NotNull;

import java.awt.Insets;
import java.lang.reflect.Method;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class IntroInsetsProperty extends IntrospectedProperty<Insets> {
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

  public void write(final Insets value, final XmlWriter writer) {
    writer.addAttribute("top",value.top);
    writer.addAttribute("left",value.left);
    writer.addAttribute("bottom",value.bottom);
    writer.addAttribute("right",value.right);
  }

  @NotNull
  public Property[] getChildren(){
    return myChildren;
  }

  @NotNull
  public PropertyRenderer<Insets> getRenderer(){
    return myRenderer;
  }

  public PropertyEditor<Insets> getEditor(){
    return null;
  }
}
