/*
 * @author max
 */
package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.codeInsight.daemon.impl.TextEditorBackgroundHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class PsiAwareTextEditorImpl extends TextEditorImpl {
  private TextEditorBackgroundHighlighter myBackgroundHighlighter;

  public PsiAwareTextEditorImpl(@NotNull final Project project, @NotNull final VirtualFile file, final TextEditorProvider provider) {
    super(project, file, provider);
  }

  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    if (myBackgroundHighlighter == null) {
      myBackgroundHighlighter = new TextEditorBackgroundHighlighter(myProject, getEditor());
    }
    return myBackgroundHighlighter;
  }
}