package com.jetbrains.rest;

import com.intellij.psi.tree.IFileElementType;

/**
 * User : catherine
 */
public interface RestElementTypes {
  IFileElementType REST_FILE = new IFileElementType("REST_FILE", RestLanguage.INSTANCE);
  RestElementType REFERENCE_TARGET = new RestElementType("REFERENCE");
  RestElementType DIRECTIVE_BLOCK = new RestElementType("DIRECTIVE_BLOCK");
  RestElementType INLINE_BLOCK = new RestElementType("INLINE_BLOCK");
}

