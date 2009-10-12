/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uiDesigner.wizard;

import com.intellij.uiDesigner.lw.LwComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class FormProperty {
  @NotNull private final LwComponent myLwComponent;
  @NotNull private final String myComponentPropertyGetterName;
  @NotNull private final String myComponentPropertySetterName;
  @NotNull private final String myComponentPropertyClassName;

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
