/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.lang.Language;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import org.intellij.lang.regexp.*;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

public class XsdRegExpParserDefinition extends RegExpParserDefinition {

  public static final Language LANGUAGE = new Language(RegExpLanguage.INSTANCE, "XsdRegExp") {};

  private static final IFileElementType XSD_REGEXP_FILE = new IFileElementType("XSD_REGEXP_FILE", LANGUAGE);

  private final EnumSet<RegExpCapability> CAPABILITIES = EnumSet.of(RegExpCapability.XML_SCHEMA_MODE,
                                                                    RegExpCapability.NESTED_CHARACTER_CLASSES,
                                                                    RegExpCapability.ALLOW_HORIZONTAL_WHITESPACE_CLASS,
                                                                    RegExpCapability.UNICODE_CATEGORY_SHORTHAND);

  @NotNull
  public Lexer createLexer(Project project) {
    return new RegExpLexer(CAPABILITIES);
  }

  @Override
  public PsiParser createParser(Project project) {
    return new RegExpParser(CAPABILITIES);
  }

  @Override
  public IFileElementType getFileNodeType() {
    return XSD_REGEXP_FILE;
  }

  @Override
  public PsiFile createFile(FileViewProvider viewProvider) {
    return new RegExpFile(viewProvider, LANGUAGE);
  }
}
