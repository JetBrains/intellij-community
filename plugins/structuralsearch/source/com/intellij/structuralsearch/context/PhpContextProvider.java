package com.intellij.structuralsearch.context;

import com.intellij.openapi.fileTypes.FileType;
import com.jetbrains.php.lang.PhpFileType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class PhpContextProvider implements SSRContextProvider {
  @Override
  public String getContext(@NotNull FileType fileType, @NotNull String pattern) {
    if (!(fileType == PhpFileType.INSTANCE)) {
      return null;
    }

    return "<?php $$PATTERN_PLACEHOLDER$$";
  }
}
