package com.intellij.codeInsight.template.impl;

import com.intellij.psi.tree.IElementType;

interface TemplateTokenType {
  IElementType TEXT = new IElementType("TEXT", null);
  IElementType VARIABLE = new IElementType("VARIABLE", null);
  IElementType ESCAPE_DOLLAR = new IElementType("ESCAPE_DOLLAR", null);
}