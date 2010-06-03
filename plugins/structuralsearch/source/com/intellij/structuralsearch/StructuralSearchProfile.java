package com.intellij.structuralsearch;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor;
import com.intellij.structuralsearch.impl.matcher.PatternTreeContext;
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor;
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.impl.ReplacementContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class StructuralSearchProfile {
  public static final ExtensionPointName<StructuralSearchProfile> EP_NAME =
    ExtensionPointName.create("com.intellij.structuralsearch.profile");

  public abstract void compile(PsiElement element, @NotNull GlobalCompilingVisitor globalVisitor);

  @NotNull
  public abstract PsiElementVisitor createMatchingVisitor(@NotNull GlobalMatchingVisitor globalVisitor);

  @NotNull
  public abstract PsiElementVisitor createLexicalNodesFilter(@NotNull LexicalNodesFilter filter);

  @NotNull
  public abstract CompiledPattern createCompiledPattern();

  @NotNull
  public abstract LanguageFileType[] getFileTypes();

  public abstract boolean isMyLanguage(@NotNull Language language);

  @NotNull
  public abstract PsiElement[] createPatternTree(@NotNull String text,
                                                 @NotNull PatternTreeContext context,
                                                 @NotNull FileType fileType,
                                                 @NotNull Project project,
                                                 boolean physical);

  @NotNull
  public FileType detectFileType(@NotNull PsiElement context) {
    LanguageFileType[] fileTypes = getFileTypes();
    assert fileTypes.length > 0 : "getFileTypes() must return at least one element";
    return fileTypes[0];
  }

  @Nullable
  public StructuralReplaceHandler getReplaceHandler(ReplacementContext context) {
    return null;
  }

  public void checkReplacementPattern(Project project, ReplaceOptions options) {
    String fileType = options.getMatchOptions().getFileType().getName().toLowerCase();
    throw new UnsupportedPatternException(SSRBundle.message("replacement.not.supported.for.filetype", fileType));
  }
}
