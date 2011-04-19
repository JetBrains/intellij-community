package com.intellij.structuralsearch.context;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.css.CssSupportLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class CssContextProvider implements SSRContextProvider {
  @Nullable
  @Override
  public String getContext(@NotNull FileType fileType, @NotNull String pattern) {
    if (!(fileType == CssSupportLoader.CSS_FILE_TYPE)) {
      return null;
    }

    if (pattern.indexOf('{') >= 0) {
      return null;
    }

    return ".c { $$PATTERN_PLACEHOLDER$$ }";
  }
}
