// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.highlighting;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.FactoryMap;
import com.jetbrains.python.console.PydevConsoleRunnerUtil;
import com.jetbrains.python.lexer.PythonHighlightingLexer;
import com.jetbrains.python.parsing.console.PyConsoleHighlightingLexer;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;


public final class PySyntaxHighlighterFactory extends SyntaxHighlighterFactory {
  private final Map<LanguageLevel, PyHighlighter> myMap = FactoryMap.create(key -> new PyHighlighter(key));

  private final Map<LanguageLevel, PyHighlighter> myConsoleMap = FactoryMap.create(key -> new PyHighlighter(key) {
    @Override
    protected PythonHighlightingLexer createHighlightingLexer(LanguageLevel languageLevel) {
      return new PyConsoleHighlightingLexer(languageLevel);
    }
  });

  @Override
  @NotNull
  public SyntaxHighlighter getSyntaxHighlighter(@Nullable final Project project, @Nullable final VirtualFile virtualFile) {
    final LanguageLevel level = getLanguageLevel(project, virtualFile);
    if (useConsoleLexer(project, virtualFile)) {
      return myConsoleMap.get(level);
    }
    return getSyntaxHighlighterForLanguageLevel(level);
  }

  /**
   * Returns a syntax highlighter for Python console.
   */
  @NotNull
  public SyntaxHighlighter getConsoleSyntaxHighlighter(@Nullable final Project project, @Nullable final VirtualFile virtualFile) {
    final LanguageLevel level = getLanguageLevel(project, virtualFile);
    return myConsoleMap.get(level);
  }

  /**
   * Returns a syntax highlighter targeting the specified version of Python.
   */
  @NotNull
  public SyntaxHighlighter getSyntaxHighlighterForLanguageLevel(@NotNull LanguageLevel level) {
    return myMap.get(level);
  }

  @NotNull
  private static LanguageLevel getLanguageLevel(@Nullable final Project project, @Nullable final VirtualFile virtualFile) {
    return project != null && virtualFile != null ?
           PythonLanguageLevelPusher.getLanguageLevelForVirtualFile(project, virtualFile) :
           LanguageLevel.getDefault();
  }

  private static boolean useConsoleLexer(@Nullable final Project project, @Nullable final VirtualFile virtualFile) {
    if (virtualFile == null || project == null || virtualFile instanceof VirtualFileWindow) {
      return false;
    }
    PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
    return psiFile != null && PydevConsoleRunnerUtil.isInPydevConsole(psiFile);
  }
}
