package com.intellij.ide.fileTemplates.impl;

import com.intellij.psi.tree.IElementType;

interface FileTemplateTokenType {
  IElementType TEXT = new IElementType("TEXT", null);
  IElementType MACRO = new IElementType("MACRO", null);
  IElementType DIRECTIVE = new IElementType("DIRECTIVE", null);
}