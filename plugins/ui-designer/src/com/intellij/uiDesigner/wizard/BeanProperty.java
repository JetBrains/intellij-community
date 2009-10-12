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

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class BeanProperty implements Comparable<BeanProperty>{
  /**
   * Property name.
   */
  @NotNull public final String myName;
  /**
   * Property type.
   * There are two possible types:
   * <ul>
   *  <li>java.lang.String</li>
   *  <li>boolean</li>
   * </ul>
   */
  @NotNull public final String myType;

  public BeanProperty(@NotNull final String name, @NonNls @NotNull final String type) {
    if(!"java.lang.String".equals(type) && !"boolean".equals(type)){
      throw new IllegalArgumentException("unknown type: " + type);
    }

    myName = name;
    myType = type;
  }

  public int compareTo(final BeanProperty property) {
    if(property == null){
      return 1;
    }
    else{
      return myName.compareTo(property.myName);
    }
  }

  /**
   * This method is used by ComboBox editor of {@link BindToExistingBeanStep.MyTableCellEditor}
   */
  public String toString() {
    return myName;
  }

  public boolean equals(final Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof BeanProperty)) return false;
    return myName.equals(((BeanProperty)obj).myName);
  }

  public int hashCode() {
    return myName.hashCode();
  }
}
