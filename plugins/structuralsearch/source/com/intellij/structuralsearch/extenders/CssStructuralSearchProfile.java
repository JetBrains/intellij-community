package com.intellij.structuralsearch.extenders;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.psi.css.CssSupportLoader;
import com.intellij.structuralsearch.StructuralSearchProfileBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class CssStructuralSearchProfile extends StructuralSearchProfileBase {
  @NotNull
  @Override
  protected String[] getVarPrefixes() {
    return new String[] {"aaaaaaaaa"};
  }

  @NotNull
  @Override
  protected LanguageFileType getFileType() {
    return CssSupportLoader.CSS_FILE_TYPE;
  }

  @Nullable
  @Override
  public String getContext(@NotNull String pattern, @Nullable Language language) {
    return pattern.indexOf('{') < 0
           ? ".c { $$PATTERN_PLACEHOLDER$$ }"
           : super.getContext(pattern, language);
  }
}
