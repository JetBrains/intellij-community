package com.intellij.psi.impl.source.tree;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.java.IJavaDocElementType;

public interface JavaDocElementType {
  //chameleon
  IElementType DOC_COMMENT_TEXT = new IJavaDocElementType("DOC_COMMENT_TEXT");

  IElementType DOC_TAG = new IJavaDocElementType("DOC_TAG");
  IElementType DOC_TAG_VALUE = new IJavaDocElementType("DOC_TAG_VALUE");
  IElementType DOC_INLINE_TAG = new IJavaDocElementType("DOC_INLINE_TAG");
  IElementType DOC_METHOD_OR_FIELD_REF = new IJavaDocElementType("DOC_METHOD_OR_FIELD_REF");
  IElementType DOC_PARAMETER_REF = new IJavaDocElementType("DOC_PARAMETER_REF");
}
