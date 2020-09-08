// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.rest;

import com.intellij.lexer.Lexer;
import com.intellij.psi.templateLanguages.TemplateDataElementType;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.lexer.PythonHighlightingLexer;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * User : catherine
 */
public class RestPythonTemplateType extends TemplateDataElementType {

  public RestPythonTemplateType(String python_block_data, RestLanguage instance, RestElementType line, IElementType restInjection) {
    super(python_block_data, instance, line, restInjection);
  }

  @Override
  protected Lexer createBaseLexer(final TemplateLanguageFileViewProvider viewProvider) {
    final Lexer baseLexer = new PythonHighlightingLexer(LanguageLevel.getDefault());
    return baseLexer;
  }

}
