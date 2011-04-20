package com.intellij.structuralsearch.extenders;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.structuralsearch.StructuralSearchProfileBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * @author Eugene.Kudelevsky
 */
public class GroovyStructuralSearchProfile extends StructuralSearchProfileBase {
  @NotNull
  @Override
  protected LanguageFileType getFileType() {
    return GroovyFileType.GROOVY_FILE_TYPE;
  }

  /*@Override
  protected String getContext(@NotNull String pattern) {
    return "class C {\n" +
           "  def f() { $$PATTERN_PLACEHOLDER$$ }\n" +
           "}";
  }*/
}
