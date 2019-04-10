package com.intellij.bash;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.lineIndent.LineIndentProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BashLineIndentProvider implements LineIndentProvider {
  @Nullable
  @Override
  public String getLineIndent(@NotNull Project project, @NotNull Editor editor, @Nullable Language language, int offset) {
    return null;
  }

  @Override
  public boolean isSuitableFor(@Nullable Language language) {
    return language instanceof BashLanguage;
  }
}
