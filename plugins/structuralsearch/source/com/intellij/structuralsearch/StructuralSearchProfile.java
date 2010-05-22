package com.intellij.structuralsearch;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor;
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public interface StructuralSearchProfile {

  @NotNull
  PsiElementVisitor createCompilingVisitor(@NotNull GlobalCompilingVisitor globalVisitor);

  @NotNull
  PsiElementVisitor createMatchingVisitor(@NotNull GlobalMatchingVisitor globalVisitor);

  @NotNull
  CompiledPattern createCompiledPattern();

  @NotNull
  LanguageFileType[] getFileTypes();

  boolean isMyLanguage(@NotNull Language language);
}
