package com.jetbrains.typoscript.lang.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.jetbrains.typoscript.lang.TypoScriptFileType;
import com.jetbrains.typoscript.lang.TypoScriptLanguage;
import org.jetbrains.annotations.NotNull;

/**
 * @author lene
 *         Date: 11.04.12
 */
public class TypoScriptFile  extends PsiFileBase {

  public TypoScriptFile(@NotNull FileViewProvider viewProvider) {
    super(viewProvider, TypoScriptLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return TypoScriptFileType.INSTANCE;
  }

  @Override
  public String toString() {
    return "TypoScript File";
  }
}
