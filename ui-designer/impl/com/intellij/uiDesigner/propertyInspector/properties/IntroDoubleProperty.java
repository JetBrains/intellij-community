package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.DoubleEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class IntroDoubleProperty extends IntrospectedProperty<Double> {
  private LabelPropertyRenderer<Double> myRenderer;
  private DoubleEditor myEditor;

  public IntroDoubleProperty(final String name, final Method readMethod, final Method writeMethod, final boolean storeAsClient){
    super(name, readMethod, writeMethod, storeAsClient);
  }

  @NotNull
  public PropertyRenderer<Double> getRenderer(){
    if (myRenderer == null) {
      myRenderer = new LabelPropertyRenderer<Double>();
    }
    return myRenderer;
  }

  public PropertyEditor<Double> getEditor(){
    if (myEditor == null) {
      myEditor = new DoubleEditor();
    }
    return myEditor;
  }
}
