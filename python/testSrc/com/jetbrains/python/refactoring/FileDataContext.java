package com.jetbrains.python.refactoring;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * @author oleg
*/
public class FileDataContext implements DataContext {
  private final PsiFile myFile;

  public FileDataContext(final PsiFile file) {
    myFile = file;
  }

  @Nullable
  public Object getData(@NonNls String dataId) {
    if (LangDataKeys.LANGUAGE.is(dataId)) {
      return myFile.getLanguage();
    }
    if (PlatformDataKeys.PROJECT.is(dataId)) {
      return myFile.getProject();
    }
    if (LangDataKeys.PSI_FILE.is(dataId)) {
      return myFile;
    }

    throw new IllegalArgumentException("Data not supported: " + dataId);
  }
}