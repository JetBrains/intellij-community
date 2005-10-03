package com.intellij.uiDesigner.wizard;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.lw.LwComponent;
import org.jetbrains.annotations.NonNls;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class FormProperty {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.wizard.FormProperty");

  private LwComponent myLwComponent;
  private String myComponentPropertyGetterName;
  private String myComponentPropertySetterName;
  private String myComponentPropertyClassName;

  public FormProperty(
    final LwComponent component,
    final @NonNls String componentPropertyGetterName,
    final @NonNls String componentPropertySetterName,
    final @NonNls String componentPropertyClassName
  ) {
    LOG.assertTrue(component != null);
    LOG.assertTrue(componentPropertyGetterName != null);
    LOG.assertTrue(componentPropertySetterName != null);
    LOG.assertTrue(componentPropertyClassName != null);

    if(
      !String.class.getName().equals(componentPropertyClassName) &&
      !int.class.getName().equals(componentPropertyClassName) &&
      !float.class.getName().equals(componentPropertyClassName) &&
      !double.class.getName().equals(componentPropertyClassName) &&
      !long.class.getName().equals(componentPropertyClassName) &&
      !boolean.class.getName().equals(componentPropertyClassName) &&
      !char.class.getName().equals(componentPropertyClassName) &&
      !byte.class.getName().equals(componentPropertyClassName) &&
      !short.class.getName().equals(componentPropertyClassName)
    ){
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("unknown componentPropertyClassName: " +componentPropertyClassName);
    }

    myLwComponent = component;
    myComponentPropertyGetterName = componentPropertyGetterName;
    myComponentPropertySetterName = componentPropertySetterName;
    myComponentPropertyClassName = componentPropertyClassName;
  }

  /**
   * @return never <code>null</code>.
   */
  public LwComponent getLwComponent() {
    return myLwComponent;
  }

  /**
   * @return never <code>null</code>
   */
  public String getComponentPropertyGetterName() {
    return myComponentPropertyGetterName;
  }

  /**
   * @return never <code>null</code>
   */
  public String getComponentPropertySetterName() {
    return myComponentPropertySetterName;
  }

  /**
   * @return never <code>null</code>. This method can return only one of the following values:
   * "int", "float", "double", "long", "boolean", "char", "byte", "short", "java.lang.String"
   */
  public String getComponentPropertyClassName() {
    return myComponentPropertyClassName;
  }
}
