package com.intellij.uiDesigner.propertyInspector;

import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.util.ArrayUtil;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class IntrospectedProperty extends Property<RadComponent> {
  protected final static Object[] EMPTY_OBJECT_ARRAY=new Object[]{};

  /**
   * This method is used to set property value to "delegee" JComponent
   */
  protected final Method myReadMethod;
  /**
   * This method is used to get property value from "delegee" JComponent
   */
  private final Method myWriteMethod;

  public IntrospectedProperty(
    final String name,
    final Method readMethod,
    final Method writeMethod
  ){
    super(null, name);
    myReadMethod = readMethod;
    myWriteMethod = writeMethod;
  }

  /**
   * <b>Do not overide this method without serious reason!</b>
   */
  public Object getValue(final RadComponent component){
    try {
      return myReadMethod.invoke(component.getDelegee(), EMPTY_OBJECT_ARRAY);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * <b>Do not overide this method without serious reason!</b>
   */
  protected void setValueImpl(final RadComponent component,final Object value) throws Exception{
    myWriteMethod.invoke(component.getDelegee(), value);
  }

  /**
   * Serializes (writes) propertie's value
   *
   * @param value property value which should be serialized.
   * @param writer writer which should be used for serialization. It is assumed that
   * before invocation of this method <code>writer</code> already has opened tag
   * that corresponds to this property. You can just append some attributes
   * here or add some subtags.
   */
  public abstract void write(@NotNull Object value, XmlWriter writer);

  @Override public boolean isModified(final RadComponent component) {
    return component.isMarkedAsModified(this);
  }

  @Override public void resetValue(RadComponent component) throws Exception {
    final Object defaultValue = getDefaultValue(component);
    myWriteMethod.invoke(component.getDelegee(), defaultValue);
    markTopmostModified(component, false);
  }

  private Object getDefaultValue(final RadComponent component) throws Exception {
    final Constructor constructor = component.getComponentClass().getConstructor(ArrayUtil.EMPTY_CLASS_ARRAY);
    constructor.setAccessible(true);
    JComponent newComponent = (JComponent)constructor.newInstance(ArrayUtil.EMPTY_OBJECT_ARRAY);
    return myReadMethod.invoke(newComponent, EMPTY_OBJECT_ARRAY);
  }

  public void importSnapshotValue(final JComponent component, final RadComponent radComponent) {
    try {
      Object value = myReadMethod.invoke(component, EMPTY_OBJECT_ARRAY);
      Object defaultValue = getDefaultValue(radComponent);
      if (!Comparing.equal(value, defaultValue)) {
        setValue(radComponent, value);
      }
    }
    catch (Exception e) {
      // ignore
    }
  }
}
