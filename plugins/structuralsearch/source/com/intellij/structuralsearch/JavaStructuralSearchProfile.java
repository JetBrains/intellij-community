package com.intellij.structuralsearch;

import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor;
import com.intellij.structuralsearch.impl.matcher.JavaCompiledPattern;
import com.intellij.structuralsearch.impl.matcher.JavaMatchingVisitor;
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor;
import com.intellij.structuralsearch.impl.matcher.compiler.JavaCompilingVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class JavaStructuralSearchProfile implements StructuralSearchProfile {
  @NotNull
  public PsiElementVisitor createCompilingVisitor(@NotNull GlobalCompilingVisitor globalVisitor) {
    return new JavaCompilingVisitor(globalVisitor);
  }

  @NotNull
  public PsiElementVisitor createMatchingVisitor(@NotNull GlobalMatchingVisitor globalVisitor) {
    return new JavaMatchingVisitor(globalVisitor);
  }

  @NotNull
  public CompiledPattern createCompiledPattern() {
    return new JavaCompiledPattern();
  }

  @NotNull
  public LanguageFileType[] getFileTypes() {
    return new LanguageFileType[]{StdFileTypes.JAVA};
  }

  public boolean isMyLanguage(@NotNull Language language) {
    return language == StdLanguages.JAVA;
  }
}
