package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.IntRegexEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.DimensionRenderer;
import org.jetbrains.annotations.NotNull;

import java.awt.Dimension;
import java.lang.reflect.Method;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class IntroDimensionProperty extends IntrospectedProperty<Dimension> {
  private final Property[] myChildren;
  private final DimensionRenderer myRenderer;
  private final IntRegexEditor<Dimension> myEditor;

  public IntroDimensionProperty(final String name, final Method readMethod, final Method writeMethod, final boolean storeAsClient){
    super(name, readMethod, writeMethod, storeAsClient);
    myChildren = new Property[]{
      new IntFieldProperty(this, "width", -1, new Dimension(0, 0)),
      new IntFieldProperty(this, "height", -1, new Dimension(0, 0)),
    };
    myRenderer = new DimensionRenderer();
    myEditor = new IntRegexEditor<Dimension>(Dimension.class, myRenderer, new int[] { -1, -1 });
  }

  public void write(final Dimension value, final XmlWriter writer) {
    writer.addAttribute("width", value.width);
    writer.addAttribute("height", value.height);
  }

  @NotNull
  public Property[] getChildren(final RadComponent component) {
    return myChildren;
  }

  @NotNull
  public PropertyRenderer<Dimension> getRenderer() {
    return myRenderer;
  }

  public PropertyEditor<Dimension> getEditor() {
    return myEditor;
  }
}
