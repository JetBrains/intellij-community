package com.intellij.structuralsearch;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.MatchUtils;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.tokenindex.LanguageTokenizer;
import com.intellij.tokenindex.Tokenizer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class StructuralSearchUtil {
  private static LanguageFileType ourDefaultFileType = null;

  public static boolean ourUseUniversalMatchingAlgorithm = false;
  private static StructuralSearchProfile[] ourNewStyleProfiles;
  private static List<Configuration> ourPredefinedConfigurations = null;

  private StructuralSearchUtil() {}

  @Nullable
  public static StructuralSearchProfile getProfileByPsiElement(@NotNull PsiElement element) {
    return getProfileByLanguage(element.getLanguage());
  }

  @Contract("null -> false")
  public static boolean isIdentifier(PsiElement element) {
    final StructuralSearchProfile profile = getProfileByPsiElement(element);
    return profile != null && profile.isIdentifier(element);
  }

  private static StructuralSearchProfile[] getNewStyleProfiles() {
    if (ourNewStyleProfiles == null) {
      final List<StructuralSearchProfile> list = new ArrayList<StructuralSearchProfile>();

      for (StructuralSearchProfile profile : StructuralSearchProfile.EP_NAME.getExtensions()) {
        if (profile instanceof StructuralSearchProfileBase) {
          list.add(profile);
        }
      }
      list.add(new XmlStructuralSearchProfile());
      ourNewStyleProfiles = list.toArray(new StructuralSearchProfile[list.size()]);
    }
    return ourNewStyleProfiles;
  }

  private static StructuralSearchProfile[] getProfiles() {
    return ourUseUniversalMatchingAlgorithm
           ? getNewStyleProfiles()
           : StructuralSearchProfile.EP_NAME.getExtensions();
  }

  public static FileType getDefaultFileType() {
    if (ourDefaultFileType == null) {
      for (StructuralSearchProfile profile : getProfiles()) {
        ourDefaultFileType = profile.getDefaultFileType(ourDefaultFileType);
      }
      if (ourDefaultFileType == null) {
        ourDefaultFileType = StdFileTypes.XML;
      }
    }
    assert isValidFileType(ourDefaultFileType) : "file type not valid for structural search: " + ourDefaultFileType.getName();
    return ourDefaultFileType;
  }

  @Nullable
  public static StructuralSearchProfile getProfileByLanguage(@NotNull Language language) {

    for (StructuralSearchProfile profile : getProfiles()) {
      if (profile.isMyLanguage(language)) {
        return profile;
      }
    }
    return null;
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

    for (StructuralSearchProfile profile : getProfiles()) {
      if (profile.canProcess(fileType)) {
        return profile;
      }
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
      if (isValidFileType(fileType)) {
        result.add(fileType);
      }
    }

    return result.toArray(new FileType[result.size()]);
  }

  private static boolean isValidFileType(FileType fileType) {
    return fileType != StdFileTypes.GUI_DESIGNER_FORM &&
        fileType != StdFileTypes.IDEA_MODULE &&
        fileType != StdFileTypes.IDEA_PROJECT &&
        fileType != StdFileTypes.IDEA_WORKSPACE &&
        fileType != FileTypes.ARCHIVE &&
        fileType != FileTypes.UNKNOWN &&
        fileType != FileTypes.PLAIN_TEXT &&
        !(fileType instanceof AbstractFileType) &&
        !fileType.isBinary() &&
        !fileType.isReadOnly();
  }

  public static String shieldSpecialChars(String word) {
    final StringBuilder buf = new StringBuilder(word.length());

    for (int i = 0; i < word.length(); ++i) {
      if (MatchUtils.SPECIAL_CHARS.indexOf(word.charAt(i)) != -1) {
        buf.append("\\");
      }
      buf.append(word.charAt(i));
    }

    return buf.toString();
  }

  public static List<Configuration> getPredefinedTemplates() {
    if (ourPredefinedConfigurations == null) {
      final List<Configuration> result = new ArrayList<Configuration>();
      for (StructuralSearchProfile profile : getProfiles()) {
        Collections.addAll(result, profile.getPredefinedTemplates());
      }
      Collections.sort(result);
      ourPredefinedConfigurations = Collections.unmodifiableList(result);
    }
    return ourPredefinedConfigurations;
  }

  public static boolean isDocCommentOwner(PsiElement match) {
    final StructuralSearchProfile profile = getProfileByPsiElement(match);
    return profile != null && profile.isDocCommentOwner(match);
  }
}
