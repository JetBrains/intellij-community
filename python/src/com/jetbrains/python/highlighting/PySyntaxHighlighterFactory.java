package com.jetbrains.python.highlighting;

import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.FactoryMap;
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

  @NotNull
  public SyntaxHighlighter getSyntaxHighlighter(final Project project, final VirtualFile virtualFile) {
   LanguageLevel languageLevel = virtualFile != null ? LanguageLevel.forFile(virtualFile) : LanguageLevel.getDefault();
    return myMap.get(languageLevel);
  }
}
