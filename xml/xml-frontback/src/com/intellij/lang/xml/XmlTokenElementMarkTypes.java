// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.xml;

import com.intellij.psi.tree.IElementType;

public interface XmlTokenElementMarkTypes {
  IElementType XML_WHITE_SPACE_MARK = new IElementType("XML_WHITE_SPACE_MARK", XMLLanguage.INSTANCE);
}
