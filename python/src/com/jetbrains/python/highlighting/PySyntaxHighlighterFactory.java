// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.highlighting;

import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.FactoryMap;
import com.jetbrains.python.console.parsing.PyConsoleHighlightingLexer;
import com.jetbrains.python.lexer.PythonHighlightingLexer;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author yole
 */
public class PySyntaxHighlighterFactory extends SyntaxHighlighterFactory {
  @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
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
    final LanguageLevel level = project != null && virtualFile != null ?
                                PyUtil.getLanguageLevelForVirtualFile(project, virtualFile) :
                                LanguageLevel.getDefault();
    return myMap.get(level);
  }
}
