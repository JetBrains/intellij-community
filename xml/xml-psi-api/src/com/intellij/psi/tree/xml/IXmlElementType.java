// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.tree.xml;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;

public class IXmlElementType extends IElementType {
  public IXmlElementType(@NonNls String debugName) {
    super(debugName, XMLLanguage.INSTANCE);
  }
}
