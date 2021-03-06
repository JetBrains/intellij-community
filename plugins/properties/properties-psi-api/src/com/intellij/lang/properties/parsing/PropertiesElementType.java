// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.parsing;

import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;

public class PropertiesElementType extends IElementType {
  public PropertiesElementType(@NonNls String debugName) {
    super(debugName, PropertiesLanguage.INSTANCE);
  }

  public String toString() {
    return "Properties:" + super.toString();
  }
}
