// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.regexp;

import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import org.intellij.lang.regexp.RegExpCapability;
import org.intellij.lang.regexp.RegExpFile;
import org.intellij.lang.regexp.RegExpLexer;
import org.intellij.lang.regexp.RegExpParser;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;


public final class PythonVerboseRegexpParserDefinition extends PythonRegexpParserDefinition {
  public static final IFileElementType VERBOSE_PYTHON_REGEXP_FILE = new IFileElementType("VERBOSE_PYTHON_REGEXP_FILE", PythonVerboseRegexpLanguage.INSTANCE);
  private final EnumSet<RegExpCapability> VERBOSE_CAPABILITIES;

  public PythonVerboseRegexpParserDefinition() {
    VERBOSE_CAPABILITIES = EnumSet.copyOf(CAPABILITIES);
    VERBOSE_CAPABILITIES.add(RegExpCapability.COMMENT_MODE);
  }

  @Override
  @NotNull
  public Lexer createLexer(Project project) {
    return new RegExpLexer(VERBOSE_CAPABILITIES);
  }

  @Override
  public @NotNull PsiParser createParser(Project project) {
    return new RegExpParser(VERBOSE_CAPABILITIES);
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return VERBOSE_PYTHON_REGEXP_FILE;
  }

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return new RegExpFile(viewProvider, PythonVerboseRegexpLanguage.INSTANCE);
  }
}
