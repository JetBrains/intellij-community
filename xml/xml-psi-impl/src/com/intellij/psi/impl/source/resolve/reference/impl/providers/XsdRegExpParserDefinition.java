// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
  @NotNull
  public Lexer createLexer(Project project) {
    return new RegExpLexer(CAPABILITIES);
  }

  @Override
  public @NotNull PsiParser createParser(Project project) {
    return new RegExpParser(CAPABILITIES);
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return XSD_REGEXP_FILE;
  }

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return new RegExpFile(viewProvider, LANGUAGE);
  }
}
