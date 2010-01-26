package com.jetbrains.python.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public enum LanguageLevel {
  PYTHON24(false, true, false),
  PYTHON25(false, true, false),
  PYTHON26(true, true, false),
  PYTHON30(true, false, true),
  PYTHON31(true, false, true);

  public static LanguageLevel getDefault() {
    return PYTHON26;
  }

  private boolean myHasWithStatement;
  private boolean myHasPrintStatement;
  private boolean myIsPy3K;

  LanguageLevel(boolean hasWithStatement, boolean hasPrintStatement, boolean isPy3K) {
    myHasWithStatement = hasWithStatement;
    myHasPrintStatement = hasPrintStatement;
    myIsPy3K = isPy3K;
  }

  public boolean hasWithStatement() {
    return myHasWithStatement;
  }

  public boolean hasPrintStatement() {
    return myHasPrintStatement;
  }

  public boolean isPy3K() {
    return myIsPy3K;
  }

  public static LanguageLevel fromPythonVersion(String pythonVersion) {
    if (pythonVersion.startsWith("2.6")) {
      return PYTHON26;
    }
    if (pythonVersion.startsWith("2.5")) {
      return PYTHON25;
    }
    if (pythonVersion.startsWith("3.0") || pythonVersion.startsWith("3.1")) {
      return PYTHON30;
    }
    return PYTHON24;
  }

  public static final Key<LanguageLevel> KEY = new Key<LanguageLevel>("python.language.level");

  @NotNull
  public static LanguageLevel forFile(@NotNull VirtualFile virtualFile) {
    final VirtualFile folder = virtualFile.getParent();
    if (folder != null) {
      final LanguageLevel level = folder.getUserData(KEY);
      if (level != null) return level;
    }
    else if (ApplicationManager.getApplication().isUnitTestMode()) {
      final LanguageLevel languageLevel = PythonLanguageLevelPusher.FORCE_LANGUAGE_LEVEL;
      if (languageLevel != null) {
        return languageLevel;
      }
    }

    return getDefault();
  }
}
