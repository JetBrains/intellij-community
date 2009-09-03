package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.IntRegexEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.RectangleRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;

import java.awt.Rectangle;
import java.lang.reflect.Method;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class IntroRectangleProperty extends IntrospectedProperty<Rectangle> {
  private final RectangleRenderer myRenderer;
  private final Property[] myChildren;
  private final IntRegexEditor<Rectangle> myEditor;

  public IntroRectangleProperty(final String name, final Method readMethod, final Method writeMethod, final boolean storeAsClient){
    super(name, readMethod, writeMethod, storeAsClient);
    myRenderer=new RectangleRenderer();
    myChildren=new Property[]{
      new IntFieldProperty(this, "x", Integer.MIN_VALUE, new Rectangle(0, 0, 0, 0)),
      new IntFieldProperty(this, "y", Integer.MIN_VALUE, new Rectangle(0, 0, 0, 0)),
      new IntFieldProperty(this, "width", 0, new Rectangle(0, 0, 0, 0)),
      new IntFieldProperty(this, "height", 0, new Rectangle(0, 0, 0, 0)),
    };
    myEditor = new IntRegexEditor<Rectangle>(Rectangle.class, myRenderer, new int[] { Integer.MIN_VALUE, Integer.MIN_VALUE, 0, 0 });
  }

  public void write(final Rectangle value,final XmlWriter writer){
    writer.addAttribute("x",value.x);
    writer.addAttribute("y",value.y);
    writer.addAttribute("width",value.width);
    writer.addAttribute("height",value.height);
  }

  @NotNull
  public Property[] getChildren(final RadComponent component){
    return myChildren;
  }

  @NotNull
  public PropertyRenderer<Rectangle> getRenderer() {
    return myRenderer;
  }

  public PropertyEditor<Rectangle> getEditor() {
    return myEditor;
  }
}
