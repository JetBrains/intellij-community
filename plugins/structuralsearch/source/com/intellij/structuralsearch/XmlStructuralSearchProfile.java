package com.intellij.structuralsearch;

import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor;
import com.intellij.structuralsearch.impl.matcher.XmlCompiledPattern;
import com.intellij.structuralsearch.impl.matcher.XmlMatchingVisitor;
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor;
import com.intellij.structuralsearch.impl.matcher.compiler.XmlCompilingVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class XmlStructuralSearchProfile implements StructuralSearchProfile {
  @NotNull
  public PsiElementVisitor createCompilingVisitor(@NotNull GlobalCompilingVisitor globalVisitor) {
    return new XmlCompilingVisitor(globalVisitor);
  }

  @NotNull
  public PsiElementVisitor createMatchingVisitor(@NotNull GlobalMatchingVisitor globalVisitor) {
    return new XmlMatchingVisitor(globalVisitor);
  }

  @NotNull
  public CompiledPattern createCompiledPattern() {
    return new XmlCompiledPattern();
  }

  @NotNull
  public LanguageFileType[] getFileTypes() {
    return new LanguageFileType[] {StdFileTypes.XML, StdFileTypes.HTML};
  }

  public boolean isMyLanguage(@NotNull Language language) {
    return language instanceof XMLLanguage;
  }
}
