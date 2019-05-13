package com.intellij.tasks.jira.jql;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JqlFile extends PsiFileBase {
  public JqlFile(@NotNull FileViewProvider viewProvider) {
    super(viewProvider, JqlLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return JqlFileType.INSTANCE;
  }
}
