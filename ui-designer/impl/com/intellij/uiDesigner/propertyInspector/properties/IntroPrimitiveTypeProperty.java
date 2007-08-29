package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.PrimitiveTypeEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

/**
 * @author yole
 */
public final class IntroPrimitiveTypeProperty<T> extends IntrospectedProperty<T> {
  private LabelPropertyRenderer<T> myRenderer;
  private PrimitiveTypeEditor<T> myEditor;
  private final Class<T> myClass;

  public IntroPrimitiveTypeProperty(final String name, final Method readMethod, final Method writeMethod, final boolean storeAsClient, 
                                    final Class<T> aClass){
    super(name, readMethod, writeMethod, storeAsClient);
    myClass = aClass;
  }

  @NotNull
  public PropertyRenderer<T> getRenderer(){
    if (myRenderer == null) {
      myRenderer = new LabelPropertyRenderer<T>();
    }
    return myRenderer;
  }

  public PropertyEditor<T> getEditor(){
    if (myEditor == null) {
      myEditor = new PrimitiveTypeEditor<T>(myClass);
    }
    return myEditor;
  }
}