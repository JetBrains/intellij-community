package com.intellij.structuralsearch.extenders;

import com.intellij.lang.Language;
import com.intellij.lang.javascript.JavaScriptSupportLoader;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.JSStructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchProfileBase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class JSStructuralSearchProfile2 extends StructuralSearchProfileBase {
  @NotNull
  @Override
  protected LanguageFileType getFileType() {
    return JavaScriptSupportLoader.JAVASCRIPT;
  }

  @NotNull
  @Override
  public Language getLanguage(PsiElement element) {
    return JSStructuralSearchProfile.getLanguageForElement(element);
  }
}
