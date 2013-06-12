package com.intellij.lexer;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.tree.IElementType;

/**
 * @author Dmitry Avdeev
 */
public interface HtmlEmbeddedTokenTypesProvider {

  ExtensionPointName<HtmlEmbeddedTokenTypesProvider> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.html.embeddedTokenTypesProvider");

  /** style or script */
  String getName();
  IElementType getElementType();
  IElementType getInlineElementType();
}
