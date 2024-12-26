package com.jetbrains.performanceScripts.lang.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.jetbrains.performanceScripts.lang.IJPerfFileType;
import com.jetbrains.performanceScripts.lang.IJPerfLanguage;
import org.jetbrains.annotations.NotNull;

public class IJPerfFile extends PsiFileBase {

  public IJPerfFile(@NotNull FileViewProvider viewProvider) {
    super(viewProvider, IJPerfLanguage.INSTANCE);
  }

  @Override
  public @NotNull FileType getFileType() {
    return IJPerfFileType.INSTANCE;
  }

  @Override
  public String toString() {
    return "IntegrationPerformanceTestScriptFile";
  }
}
