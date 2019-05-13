package com.intellij.tasks.youtrack.lang;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class YouTrackFile extends PsiFileBase {
  public YouTrackFile(@NotNull FileViewProvider viewProvider) {
    super(viewProvider, YouTrackLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return YouTrackFileType.INSTANCE;
  }
}
