package com.intellij.codeInsight.template.impl;

import com.intellij.psi.tree.IElementType;

interface TemplateTokenType {
  IElementType TEXT = new IElementType("TEXT");
  IElementType VARIABLE = new IElementType("VARIABLE");
  IElementType ESCAPE_DOLLAR = new IElementType("ESCAPE_DOLLAR");
}