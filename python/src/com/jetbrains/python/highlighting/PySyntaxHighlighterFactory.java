package com.jetbrains.python.highlighting;

import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.FactoryMap;
import com.jetbrains.python.console.parsing.PyConsoleHighlightingLexer;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.lexer.PythonHighlightingLexer;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PySyntaxHighlighterFactory extends SyntaxHighlighterFactory {
  @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
  private final FactoryMap<LanguageLevel, PyHighlighter> myMap = new FactoryMap<LanguageLevel, PyHighlighter>() {
    @Override
    protected PyHighlighter create(LanguageLevel key) {
      return new PyHighlighter(key);
    }
  };

  private final FactoryMap<LanguageLevel, PyHighlighter> myConsoleMap = new FactoryMap<LanguageLevel, PyHighlighter>() {
      @Override
      protected PyHighlighter create(LanguageLevel key) {
        return new PyHighlighter(key) {
          @Override
          protected PythonHighlightingLexer createHighlightingLexer(LanguageLevel languageLevel) {
            return new PyConsoleHighlightingLexer(languageLevel);
          }
        };
      }
    };

  @NotNull
  public SyntaxHighlighter getSyntaxHighlighter(final Project project, final VirtualFile virtualFile) {
    LanguageLevel languageLevel = virtualFile != null ? LanguageLevel.forFile(virtualFile) : LanguageLevel.getDefault();
    if (virtualFile != null && PydevConsoleRunner.isInPydevConsole(virtualFile)) {
      return myConsoleMap.get(languageLevel);
    }
    return myMap.get(languageLevel);
  }
}
