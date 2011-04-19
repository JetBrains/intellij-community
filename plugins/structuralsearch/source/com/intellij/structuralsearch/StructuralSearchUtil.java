package com.intellij.structuralsearch;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.tokenindex.LanguageTokenizer;
import com.intellij.tokenindex.Tokenizer;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class StructuralSearchUtil {
  private static final List<StructuralSearchProfile> myRegisteredProfiles  = new ArrayList<StructuralSearchProfile>();
  public static final FileType DEFAULT_FILE_TYPE = StdFileTypes.JAVA;

  public static boolean ourUseUniversalMatchingAlgorithm = false;
  public static final String PATTERN_PLACEHOLDER = "$$PATTERN_PLACEHOLDER$$";

  private static final StructuralSearchProfile ourUniversalMatchingProfile = new StructuralSearchProfileImpl();

  static {
    Collections
      .addAll(myRegisteredProfiles, new JavaStructuralSearchProfile(), new XmlStructuralSearchProfile(), ourUniversalMatchingProfile);
  }

  private StructuralSearchUtil() {
  }

  @Nullable
  public static StructuralSearchProfile getProfileByPsiElement(@NotNull PsiElement element) {
    return getProfileByLanguage(element.getLanguage());
  }

  @Nullable
  public static StructuralSearchProfile getProfileByLanguage(@NotNull Language language) {
    if (ourUseUniversalMatchingAlgorithm && ourUniversalMatchingProfile.isMyLanguage(language)) {
      return ourUniversalMatchingProfile;
    }

    for (StructuralSearchProfile profile : StructuralSearchProfile.EP_NAME.getExtensions()) {
      if (profile.isMyLanguage(language)) {
        return profile;
      }
    }
    for (StructuralSearchProfile profile : myRegisteredProfiles) {
      if (profile.isMyLanguage(language)) {
        return profile;
      }
    }

    if (ourUniversalMatchingProfile.isMyLanguage(language)) {
      return ourUniversalMatchingProfile;
    }
    return null;
  }

  public static List<StructuralSearchProfile> getAllProfiles() {
    List<StructuralSearchProfile> profiles = new ArrayList<StructuralSearchProfile>();
    Collections.addAll(profiles, StructuralSearchProfile.EP_NAME.getExtensions());
    profiles.addAll(myRegisteredProfiles);
    return profiles;
  }

  @Nullable
  public static Tokenizer getTokenizerForLanguage(@NotNull Language language) {
    return LanguageTokenizer.INSTANCE.forLanguage(language);
  }

  public static boolean isTypedVariable(@NotNull final String name) {
    return name.charAt(0)=='$' && name.charAt(name.length()-1)=='$';
  }

  @Nullable
  public static StructuralSearchProfile getProfileByFileType(FileType fileType) {
    if (ourUseUniversalMatchingAlgorithm && ourUniversalMatchingProfile.canProcess(fileType)) {
      return ourUniversalMatchingProfile;
    }

    for (StructuralSearchProfile profile : StructuralSearchProfile.EP_NAME.getExtensions()) {
      if (profile.canProcess(fileType)) {
        return profile;
      }
    }
    for (StructuralSearchProfile profile : myRegisteredProfiles) {
      if (profile.canProcess(fileType)) {
        return profile;
      }
    }

    if (ourUniversalMatchingProfile.canProcess(fileType)) {
      return ourUniversalMatchingProfile;
    }
    return null;
  }

  @NotNull
  public static FileType[] getSuitableFileTypes() {
    Set<FileType> allFileTypes = new HashSet<FileType>();
    Collections.addAll(allFileTypes, FileTypeManager.getInstance().getRegisteredFileTypes());
    for (Language language : Language.getRegisteredLanguages()) {
      FileType fileType = language.getAssociatedFileType();
      if (fileType != null) {
        allFileTypes.add(fileType);
      }
    }

    List<FileType> result = new ArrayList<FileType>();
    for (FileType fileType : allFileTypes) {
      if (fileType != StdFileTypes.GUI_DESIGNER_FORM &&
          fileType != StdFileTypes.IDEA_MODULE &&
          fileType != StdFileTypes.IDEA_PROJECT &&
          fileType != StdFileTypes.IDEA_WORKSPACE &&
          fileType != FileTypes.ARCHIVE &&
          fileType != FileTypes.UNKNOWN &&
          fileType != FileTypes.PLAIN_TEXT &&
          !(fileType instanceof AbstractFileType) &&
          !fileType.isBinary() &&
          !fileType.isReadOnly()) {
        result.add(fileType);
      }
    }

    return result.toArray(new FileType[result.size()]);
  }

  public static PsiElement[] parsePattern(Project project,
                                          String context,
                                          String pattern,
                                          FileType fileType,
                                          Language language,
                                          String extension,
                                          boolean physical) {
    int offset = context.indexOf(PATTERN_PLACEHOLDER);

    final int patternLength = pattern.length();
    final String patternInContext = context.replace(PATTERN_PLACEHOLDER, pattern);

    final String ext = extension != null ? extension : fileType.getDefaultExtension();
    final String name = "__dummy." + ext;
    final PsiFileFactory factory = PsiFileFactory.getInstance(project);

    final PsiFile file = language == null
                         ? factory.createFileFromText(name, fileType, patternInContext, LocalTimeCounter.currentTime(), physical, true)
                         : factory.createFileFromText(name, language, patternInContext, physical, true);
    if (file == null) {
      return PsiElement.EMPTY_ARRAY;
    }

    final List<PsiElement> result = new ArrayList<PsiElement>();

    PsiElement element = file.findElementAt(offset);
    if (element == null) {
      return PsiElement.EMPTY_ARRAY;
    }

    PsiElement topElement = element;
    element = element.getParent();

    while (element != null) {
      if (element.getTextOffset() == offset && element.getTextLength() <= patternLength) {
        topElement = element;
      }
      element = element.getParent();
    }

    final int endOffset = offset + patternLength;
    result.add(topElement);
    topElement = topElement.getNextSibling();

    while (topElement != null && topElement.getTextRange().getEndOffset() <= endOffset) {
      result.add(topElement);
      topElement = topElement.getNextSibling();
    }

    return result.toArray(new PsiElement[result.size()]);
  }
}
