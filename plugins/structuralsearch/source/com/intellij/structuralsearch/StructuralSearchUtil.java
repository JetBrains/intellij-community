package com.intellij.structuralsearch;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
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
    return null;
  }

  /*@Nullable
  public static StructuralSearchProfile getProfileByFileType(@NotNull FileType fileType) {
    for (StructuralSearchProfile profile : myRegisteredProfiles) {
      if (ArrayUtil.contains(fileType, profile.getFileTypes())) {
        return profile;
      }
    }
    return null;
  }*/

  public static List<StructuralSearchProfile> getAllProfiles() {
    return myRegisteredProfiles;
  }
}
