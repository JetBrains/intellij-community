// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.xml;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nullable;

public interface XmlAttlistDecl extends XmlElement {
  XmlAttlistDecl[] EMPTY_ARRAY = new XmlAttlistDecl[0];

  XmlElement getNameElement();
  @Nullable @NlsSafe String getName();
  XmlAttributeDecl[] getAttributeDecls();
}
