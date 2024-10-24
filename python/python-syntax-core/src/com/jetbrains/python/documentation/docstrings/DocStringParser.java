package com.jetbrains.python.documentation.docstrings;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.psi.PyStringLiteralUtil;
import com.jetbrains.python.psi.StructuredDocString;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DocStringParser {
  /**
   * @param stringText docstring text with possible string prefix and quotes
   */
  @NotNull
  public static StructuredDocString parseDocString(@NotNull DocStringFormat format, @NotNull String stringText) {
    return parseDocString(format, stripPrefixAndQuotes(stringText));
  }

  @NotNull
  public static StructuredDocString parseDocString(@NotNull DocStringFormat format, @NotNull Substring content) {
    return switch (format) {
      case REST -> new SphinxDocString(content);
      case GOOGLE -> new GoogleCodeStyleDocString(content);
      case NUMPY -> new NumpyDocString(content);
      case PLAIN -> new PlainDocString(content);
    };
  }

  @NotNull
  private static Substring stripPrefixAndQuotes(@NotNull String text) {
    final TextRange contentRange = PyStringLiteralUtil.getContentRange(text);
    return new Substring(text, contentRange.getStartOffset(), contentRange.getEndOffset());
  }

  /**
   * @return docstring format inferred heuristically solely from its content. For more reliable result use anchored version
   * {@link #guessDocStringFormat(String, PsiElement)} of this method.
   * @see #guessDocStringFormat(String, PsiElement)
   */
  @NotNull
  public static DocStringFormat guessDocStringFormat(@NotNull String text) {
    if (isLikeSphinxDocString(text)) {
      return DocStringFormat.REST;
    }
    if (isLikeNumpyDocstring(text)) {
      return DocStringFormat.NUMPY;
    }
    if (isLikeGoogleDocString(text)) {
      return DocStringFormat.GOOGLE;
    }
    return DocStringFormat.PLAIN;
  }

  /**
   * @param text   docstring text <em>with both quotes and string prefix stripped</em>
   * @param anchor PSI element that will be used to retrieve docstring format from the containing file or the project module
   * @return docstring inferred heuristically and if unsuccessful fallback to configured format retrieved from anchor PSI element
   * @see #getConfiguredDocStringFormat(PsiElement)
   */
  @NotNull
  public static DocStringFormat guessDocStringFormat(@NotNull String text, @Nullable PsiElement anchor) {
    final DocStringFormat guessed = guessDocStringFormat(text);
    return guessed == DocStringFormat.PLAIN && anchor != null ? getConfiguredDocStringFormatOrPlain(anchor) : guessed;
  }

  /**
   * @param anchor PSI element that will be used to retrieve docstring format from the containing file or the project module
   * @return docstring format configured for file or module containing given anchor PSI element
   * @see PyDocumentationSettings#getFormatForFile(PsiFile)
   */
  @Nullable
  public static DocStringFormat getConfiguredDocStringFormat(@NotNull PsiElement anchor) {
    final Module module = getModuleForElement(anchor);
    if (module == null) {
      return null;
    }

    final PyDocumentationSettings settings = PyDocumentationSettings.getInstance(module);
    return settings.getFormatForFile(anchor.getContainingFile());
  }

  @NotNull
  public static DocStringFormat getConfiguredDocStringFormatOrPlain(@NotNull PsiElement anchor) {
    return ObjectUtils.chooseNotNull(getConfiguredDocStringFormat(anchor), DocStringFormat.PLAIN);
  }

  public static boolean isLikeSphinxDocString(@NotNull String text) {
    return text.contains(":param ") ||
           text.contains(":key ") || text.contains(":keyword ") ||
           text.contains(":return:") || text.contains(":returns:") ||
           text.contains(":raise ") || text.contains(":raises ") || text.contains(":except ") || text.contains(":exception ") ||
           text.contains(":rtype") || text.contains(":type") ||
           text.contains(":var") || text.contains(":ivar") || text.contains(":cvar");
  }

  public static boolean isLikeGoogleDocString(@NotNull String text) {
    for (@NonNls String title : StringUtil.findMatches(text, GoogleCodeStyleDocString.SECTION_HEADER, 1)) {
      if (SectionBasedDocString.isValidSectionTitle(title)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isLikeNumpyDocstring(@NotNull String text) {
    final String[] lines = StringUtil.splitByLines(text, false);
    for (int i = 0; i < lines.length; i++) {
      final String line = lines[i];
      if (NumpyDocString.SECTION_HEADER.matcher(line).matches() && i > 0) {
        @NonNls final String lineBefore = lines[i - 1];
        if (SectionBasedDocString.SECTION_NAMES.contains(StringUtil.toLowerCase(lineBefore.trim()))) {
          return true;
        }
      }
    }
    return false;
  }

  // Might return {@code null} in some rare cases when PSI element doesn't have an associated module.
  // For instance, an empty IDEA project with a Python scratch file.
  @Nullable
  public static Module getModuleForElement(@NotNull PsiElement element) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(element.getContainingFile());
    if (module != null) {
      return module;
    }

    return ArrayUtil.getFirstElement(ModuleManager.getInstance(element.getProject()).getModules());
  }
}
