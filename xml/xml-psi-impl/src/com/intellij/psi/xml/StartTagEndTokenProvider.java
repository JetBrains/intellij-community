package com.intellij.psi.xml;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.tree.IElementType;

public interface StartTagEndTokenProvider {
  ExtensionPointName<StartTagEndTokenProvider> EP_NAME = new ExtensionPointName<StartTagEndTokenProvider>("com.intellij.xml.startTagEndToken");

  IElementType[] getTypes();
}
