// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.wizard;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

final class BeanProperty implements Comparable<BeanProperty>{
  /**
   * Property name.
   */
  @NotNull public final @NlsSafe String myName;
  /**
   * Property type.
   * There are two possible types:
   * <ul>
   *  <li>java.lang.String</li>
   *  <li>boolean</li>
   * </ul>
   */
  @NotNull public final @NlsSafe String myType;

  BeanProperty(@NotNull final String name, @NonNls @NotNull final String type) {
    if(!"java.lang.String".equals(type) && !"boolean".equals(type)){
      throw new IllegalArgumentException("unknown type: " + type);
    }

    myName = name;
    myType = type;
  }

  @Override
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
