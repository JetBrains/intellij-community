package com.intellij.structuralsearch;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiElement;
import com.intellij.tokenindex.LanguageTokenizer;
import com.intellij.tokenindex.Tokenizer;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class StructuralSearchUtil {
  private static final List<StructuralSearchProfile> myRegisteredProfiles  = new ArrayList<StructuralSearchProfile>();
  public static final FileType DEFAULT_FILE_TYPE = StdFileTypes.JAVA;

  static {
    Collections.addAll(myRegisteredProfiles, new JavaStructuralSearchProfile(), new XmlStructuralSearchProfile());
  }

  private StructuralSearchUtil() {
  }

  @Nullable
  public static StructuralSearchProfile getProfileByPsiElement(@NotNull PsiElement element) {
    return getProfileByLanguage(element.getLanguage());
  }

  @Nullable
  public static StructuralSearchProfile getProfileByLanguage(@NotNull Language language) {
    for (StructuralSearchProfile profile : myRegisteredProfiles) {
      if (profile.isMyLanguage(language)) {
        return profile;
      }
    }
    for (StructuralSearchProfile profile : StructuralSearchProfile.EP_NAME.getExtensions()) {
      if (profile.isMyLanguage(language)) {
        return profile;
      }
    }
    return null;
  }

  public static List<StructuralSearchProfile> getAllProfiles() {
    List<StructuralSearchProfile> profiles = new ArrayList<StructuralSearchProfile>();
    profiles.addAll(myRegisteredProfiles);
    Collections.addAll(profiles, StructuralSearchProfile.EP_NAME.getExtensions());
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
    for (StructuralSearchProfile profile : myRegisteredProfiles) {
      if (ArrayUtil.contains(fileType, profile.getFileTypes())) {
        return profile;
      }
    }
    for (StructuralSearchProfile profile : StructuralSearchProfile.EP_NAME.getExtensions()) {
      if (ArrayUtil.contains(fileType, profile.getFileTypes())) {
        return profile;
      }
    }
    return null;
  }
}
