/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.jetbrains.rest;

import com.intellij.lexer.Lexer;
import com.intellij.psi.templateLanguages.TemplateDataElementType;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.jetbrains.python.lexer.PythonHighlightingLexer;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * User : catherine
 */
public class RestPythonTemplateType extends TemplateDataElementType {

  public RestPythonTemplateType(String python_block_data, RestLanguage instance, RestElementType line, RestElementType restInjection) {
    super(python_block_data, instance, line, restInjection);
  }

  protected Lexer createBaseLexer(final TemplateLanguageFileViewProvider viewProvider) {
    final Lexer baseLexer = new PythonHighlightingLexer(LanguageLevel.getDefault());
    return baseLexer;
  }

}
