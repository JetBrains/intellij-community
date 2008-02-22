package com.jetbrains.python;

import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PySyntaxHighlighterFactory extends SyntaxHighlighterFactory {
  private final PyHighlighter myHighlighter = new PyHighlighter();

  @NotNull
  public SyntaxHighlighter getSyntaxHighlighter(final Project project, final VirtualFile virtualFile) {
    return myHighlighter;
  }
}
