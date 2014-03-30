/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
