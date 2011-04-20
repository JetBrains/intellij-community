package com.intellij.structuralsearch.extenders;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.structuralsearch.StructuralSearchProfileBase;
import com.jetbrains.php.lang.PhpFileType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class PhpStructuralSearchProfile extends StructuralSearchProfileBase {
  @NotNull
  @Override
  protected LanguageFileType getFileType() {
    return PhpFileType.INSTANCE;
  }

  @Override
  public String getContext(@NotNull String pattern) {
    return "<?php $$PATTERN_PLACEHOLDER$$";
  }
}
