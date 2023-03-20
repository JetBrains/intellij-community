package com.jetbrains.performancePlugin.lang.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.jetbrains.performancePlugin.lang.IJPerfFileType;
import com.jetbrains.performancePlugin.lang.IJPerfLanguage;
import org.jetbrains.annotations.NotNull;

public class IJPerfFile extends PsiFileBase {

  public IJPerfFile(@NotNull FileViewProvider viewProvider) {
    super(viewProvider, IJPerfLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return IJPerfFileType.INSTANCE;
  }

  @Override
  public String toString() {
    return "IntegrationPerformanceTestScriptFile";
  }
}
