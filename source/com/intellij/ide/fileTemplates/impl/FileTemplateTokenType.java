package com.intellij.ide.fileTemplates.impl;

import com.intellij.psi.tree.IElementType;

interface FileTemplateTokenType {
  IElementType TEXT = new IElementType("TEXT");
  IElementType MACRO = new IElementType("MACRO");
  IElementType DIRECTIVE = new IElementType("DIRECTIVE");
}