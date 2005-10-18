package com.intellij.uiDesigner.propertyInspector;

import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.XmlWriter;

import java.lang.reflect.Method;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class IntrospectedProperty extends Property{
  private final static Object[] EMPTY_OBJECT_ARRAY=new Object[]{};

  /**
   * This method is used to set property value to "delegee" JComponent
   */
  private final Method myReadMethod;
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
    myWriteMethod.invoke(component.getDelegee(), new Object[]{value});
  }

  /**
   * Serializes (writes) propertie's value
   *
   * @param value property value which should be serialized. Must not be null.
   * @param writer writer which should be used for serialization. It is assumed that
   * before invocation of this method <code>writer</code> already has opened tag
   * that corresponds to this property. You can just append some attributes
   * here or add some subtags.
   */
  public abstract void write(Object value, XmlWriter writer);
}
