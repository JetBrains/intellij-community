package com.intellij.uiDesigner.wizard;

import com.intellij.uiDesigner.lw.LwComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class FormProperty {
  @NotNull private LwComponent myLwComponent;
  @NotNull private String myComponentPropertyGetterName;
  @NotNull private String myComponentPropertySetterName;
  @NotNull private String myComponentPropertyClassName;

  public FormProperty(
    final @NotNull LwComponent component,
    final @NotNull @NonNls String componentPropertyGetterName,
    final @NotNull @NonNls String componentPropertySetterName,
    final @NotNull @NonNls String componentPropertyClassName
  ) {
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
      throw new IllegalArgumentException("unknown componentPropertyClassName: " +componentPropertyClassName);
    }

    myLwComponent = component;
    myComponentPropertyGetterName = componentPropertyGetterName;
    myComponentPropertySetterName = componentPropertySetterName;
    myComponentPropertyClassName = componentPropertyClassName;
  }

  @NotNull public LwComponent getLwComponent() {
    return myLwComponent;
  }

  @NotNull public String getComponentPropertyGetterName() {
    return myComponentPropertyGetterName;
  }

  @NotNull public String getComponentPropertySetterName() {
    return myComponentPropertySetterName;
  }

  /**
   * @return This method can return only one of the following values:
   * "int", "float", "double", "long", "boolean", "char", "byte", "short", "java.lang.String"
   */
  @NotNull public String getComponentPropertyClassName() {
    return myComponentPropertyClassName;
  }
}
