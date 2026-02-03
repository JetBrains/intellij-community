// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.regexp;

import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import org.intellij.lang.regexp.RegExpCapability;
import org.intellij.lang.regexp.RegExpFile;
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
  public @NotNull EnumSet<RegExpCapability> getCapabilities() {
    return VERBOSE_CAPABILITIES;
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
