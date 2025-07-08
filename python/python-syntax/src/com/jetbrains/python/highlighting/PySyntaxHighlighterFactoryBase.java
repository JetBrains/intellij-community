// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.highlighting;

import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.FactoryMap;
import com.jetbrains.python.PyLanguageFacade;
import com.jetbrains.python.lexer.PythonHighlightingLexer;
import com.jetbrains.python.parsing.console.PyConsoleHighlightingLexer;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class PySyntaxHighlighterFactoryBase extends SyntaxHighlighterFactory {
  private final Map<LanguageLevel, PyHighlighter> myMap = FactoryMap.create(key -> new PyHighlighter(key));

  private final Map<LanguageLevel, PyHighlighter> myConsoleMap = FactoryMap.create(key -> new PyHighlighter(key) {
    @Override
    protected PythonHighlightingLexer createHighlightingLexer(LanguageLevel languageLevel) {
      return new PyConsoleHighlightingLexer(languageLevel);
    }
  });

  @Override
  public @NotNull SyntaxHighlighter getSyntaxHighlighter(final @Nullable Project project, final @Nullable VirtualFile virtualFile) {
    final LanguageLevel level = getLanguageLevel(project, virtualFile);
    if (useConsoleLexer(project, virtualFile)) {
      return myConsoleMap.get(level);
    }
    return getSyntaxHighlighterForLanguageLevel(level);
  }

  /**
   * Returns a syntax highlighter for Python console.
   */
  public @NotNull SyntaxHighlighter getConsoleSyntaxHighlighter(final @Nullable Project project, final @Nullable VirtualFile virtualFile) {
    final LanguageLevel level = getLanguageLevel(project, virtualFile);
    return myConsoleMap.get(level);
  }

  /**
   * Returns a syntax highlighter targeting the specified version of Python.
   */
  public @NotNull SyntaxHighlighter getSyntaxHighlighterForLanguageLevel(@NotNull LanguageLevel level) {
    return myMap.get(level);
  }

  private static @NotNull LanguageLevel getLanguageLevel(final @Nullable Project project, final @Nullable VirtualFile virtualFile) {
    return project != null && virtualFile != null ?
           PyLanguageFacade.getINSTANCE().getEffectiveLanguageLevel(project, virtualFile) :
           LanguageLevel.getDefault();
  }

  protected boolean useConsoleLexer(final @Nullable Project project, final @Nullable VirtualFile virtualFile) {
    return false;
  }
}
