package com.intellij.structuralsearch;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor;
import com.intellij.structuralsearch.impl.matcher.PatternTreeContext;
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor;
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.impl.ReplacementContext;
import com.intellij.util.ArrayUtil;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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

  public static String getTypeName(FileType fileType) {
    return fileType.getName().toLowerCase();
  }

  @NotNull
  public String[] getFileTypeSearchVariants() {
    List<String> result = new ArrayList<String>();
    for (FileType fileType : getFileTypes()) {
      result.add(getTypeName(fileType));
    }
    return ArrayUtil.toStringArray(result);
  }

  @NotNull
  public abstract FileType[] getFileTypes();

  @Nullable
  public FileType getFileTypeBySearchVariant(@NotNull String searchVariant) {
    if (!ArrayUtil.contains(searchVariant, getFileTypeSearchVariants())) {
      return null;
    }
    FileType[] types = getFileTypes();
    if (types.length == 1) {
      return types[0];
    }
    for (FileType fileType : types) {
      if (searchVariant.equals(getTypeName(fileType))) {
        return fileType;
      }
    }
    assert types.length > 0;
    return types[0];
  }

  @Nullable
  public String getFileExtensionBySearchVariant(@NotNull String searchVariant) {
    FileType fileType = getFileTypeBySearchVariant(searchVariant);
    if (fileType == null) {
      return null;
    }
    return fileType.getDefaultExtension();
  }

  public String getSearchVariant(@NotNull FileType fileType, @Nullable String extension) {
    return getTypeName(fileType);
  }

  public abstract boolean isMyLanguage(@NotNull Language language);

  public boolean isMyFile(PsiFile file, @NotNull Language language, Language... patternLanguages) {
    if (isMyLanguage(language) && ArrayUtil.find(patternLanguages, language) >= 0) {
      return true;
    }
    return false;
  }

  @NotNull
  public PsiElement[] createPatternTree(@NotNull String text,
                                        @NotNull PatternTreeContext context,
                                        @NotNull FileType fileType,
                                        @NotNull String extension,
                                        @NotNull Project project,
                                        boolean physical) {
    return PsiFileFactory.getInstance(project)
      .createFileFromText("__dummy." + extension, fileType, text, LocalTimeCounter.currentTime(), physical, true).getChildren();
  }


  @NotNull
  public PsiElement[] createPatternTree(@NotNull String text,
                                        @NotNull PatternTreeContext context,
                                        @NotNull FileType fileType,
                                        @NotNull Project project,
                                        boolean physical) {
    return createPatternTree(text, context, fileType, fileType.getDefaultExtension(), project, physical);
  }

  @NotNull
  public String detectFileType(@NotNull PsiElement context) {
    String[] fileTypes = getFileTypeSearchVariants();
    assert fileTypes.length > 0 : "getFileTypes() must return at least one element";
    return fileTypes[0];
  }

  @Nullable
  public StructuralReplaceHandler getReplaceHandler(@NotNull ReplacementContext context) {
    return null;
  }

  public void checkReplacementPattern(Project project, ReplaceOptions options) {
    String fileType = getTypeName(options.getMatchOptions().getFileType());
    throw new UnsupportedPatternException(SSRBundle.message("replacement.not.supported.for.filetype", fileType));
  }

  @NotNull
  public Language getLanguage(PsiElement element) {
    return element.getLanguage();
  }
}
