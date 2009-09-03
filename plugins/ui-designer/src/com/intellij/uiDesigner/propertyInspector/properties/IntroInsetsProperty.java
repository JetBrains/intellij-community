package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.IntRegexEditor;
import com.intellij.uiDesigner.propertyInspector.editors.InsetsEditor;
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
  private final IntRegexEditor<Insets> myEditor;

  public IntroInsetsProperty(final String name, final Method readMethod, final Method writeMethod, final boolean storeAsClient){
    super(name, readMethod, writeMethod, storeAsClient);
    myChildren=new Property[]{
      new IntFieldProperty(this, "top", 0, new Insets(0, 0, 0, 0)),
      new IntFieldProperty(this, "left", 0, new Insets(0, 0, 0, 0)),
      new IntFieldProperty(this, "bottom", 0, new Insets(0, 0, 0, 0)),
      new IntFieldProperty(this, "right", 0, new Insets(0, 0, 0, 0)),
    };
    myRenderer=new InsetsPropertyRenderer();
    myEditor = new InsetsEditor(myRenderer);
  }

  public void write(final Insets value, final XmlWriter writer) {
    writer.writeInsets(value);
  }

  @NotNull
  public Property[] getChildren(final RadComponent component) {
    return myChildren;
  }

  @NotNull
  public PropertyRenderer<Insets> getRenderer() {
    return myRenderer;
  }

  public PropertyEditor<Insets> getEditor() {
    return myEditor;
  }
}
