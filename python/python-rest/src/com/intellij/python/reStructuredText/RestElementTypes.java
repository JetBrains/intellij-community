// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText;

import com.intellij.psi.tree.IFileElementType;

/**
 * User : catherine
 */
public interface RestElementTypes {
  IFileElementType REST_FILE = new IFileElementType("REST_FILE", RestLanguage.INSTANCE);
  RestElementType REFERENCE_TARGET = new RestElementType("REFERENCE");
  RestElementType DIRECTIVE_BLOCK = new RestElementType("DIRECTIVE_BLOCK");
  RestElementType INLINE_BLOCK = new RestElementType("INLINE_BLOCK");

  RestElementType LINE_TEXT = new RestElementType("LINE_TEXT");
  RestElementType FIELD_LIST = new RestElementType("FIELD_LIST");
}

