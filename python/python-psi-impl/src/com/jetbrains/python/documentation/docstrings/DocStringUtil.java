// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation.docstrings;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DocStringUtil {
  private DocStringUtil() {
  }

  @Nullable
  public static String getDocStringValue(@NotNull PyDocStringOwner owner) {
    return PyPsiUtils.strValue(owner.getDocStringExpression());
  }

  /**
   * Attempts to detect docstring format from given text and parses it into corresponding structured docstring.
   * It's recommended to use more reliable {@link #parse(String, PsiElement)} that fallbacks to format specified in settings.
   *
   * @param text docstring text <em>with both quotes and string prefix stripped</em>
   * @return structured docstring for one of supported formats or instance of {@link PlainDocString} if none was recognized.
   * @see #parse(String, PsiElement)
   */
  @NotNull
  public static StructuredDocString parse(@NotNull String text) {
    return parse(text, null);
  }

  /**
   * Attempts to detects docstring format first from given text, next from settings and parses text into corresponding structured docstring.
   *
   * @param text   docstring text <em>with both quotes and string prefix stripped</em>
   * @param anchor PSI element that will be used to retrieve docstring format from the containing file or the project module
   * @return structured docstring for one of supported formats or instance of {@link PlainDocString} if none was recognized.
   * @see DocStringFormat#ALL_NAMES_BUT_PLAIN
   * @see #guessDocStringFormat(String, PsiElement)
   */
  @NotNull
  public static StructuredDocString parse(@NotNull String text, @Nullable PsiElement anchor) {
    final DocStringFormat format = guessDocStringFormat(text, anchor);
    return parseDocStringContent(format, text);
  }

  /**
   * Attempts to detects docstring format first from the text of given string node, next from settings using given expression as an anchor
   * and parses text into corresponding structured docstring.
   *
   * @param stringLiteral supposedly result of {@link PyDocStringOwner#getDocStringExpression()}
   * @return structured docstring for one of supported formats or instance of {@link PlainDocString} if none was recognized.
   */
  @NotNull
  public static StructuredDocString parseDocString(@NotNull PyStringLiteralExpression stringLiteral) {
    return parseDocString(guessDocStringFormat(stringLiteral.getStringValue(), stringLiteral), stringLiteral);
  }

  @NotNull
  public static StructuredDocString parseDocString(@NotNull DocStringFormat format, @NotNull PyStringLiteralExpression stringLiteral) {
    return parseDocString(format, stringLiteral.getStringNodes().get(0));
  }

  @NotNull
  public static StructuredDocString parseDocString(@NotNull DocStringFormat format, @NotNull ASTNode node) {
    //Preconditions.checkArgument(node.getElementType() == PyTokenTypes.DOCSTRING);
    return parseDocString(format, node.getText());
  }

  /**
   * @param stringText docstring text with possible string prefix and quotes
   */
  @NotNull
  public static StructuredDocString parseDocString(@NotNull DocStringFormat format, @NotNull String stringText) {
    return parseDocString(format, stripPrefixAndQuotes(stringText));
  }

  /**
   * @param stringContent docstring text without string prefix and quotes, but not escaped, otherwise ranges of {@link Substring} returned
   *                      from {@link StructuredDocString} may be invalid
   */
  @NotNull
  public static StructuredDocString parseDocStringContent(@NotNull DocStringFormat format, @NotNull String stringContent) {
    return parseDocString(format, new Substring(stringContent));
  }

  @NotNull
  public static StructuredDocString parseDocString(@NotNull DocStringFormat format, @NotNull Substring content) {
    switch (format) {
      case REST:
        return new SphinxDocString(content);
      case EPYTEXT:
        return new EpydocString(content);
      case GOOGLE:
        return new GoogleCodeStyleDocString(content);
      case NUMPY:
        return new NumpyDocString(content);
      default:
        return new PlainDocString(content);
    }
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
    if (isLikeEpydocDocString(text)) {
      return DocStringFormat.EPYTEXT;
    }
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
           text.contains(":key ") ||  text.contains(":keyword ") ||
           text.contains(":return:") || text.contains(":returns:") ||
           text.contains(":raise ") || text.contains(":raises ") || text.contains(":except ") || text.contains(":exception ") ||
           text.contains(":rtype") || text.contains(":type");
  }

  public static boolean isLikeEpydocDocString(@NotNull String text) {
    return text.contains("@param ") ||
           text.contains("@kwarg ") || text.contains("@keyword ") || text.contains("@kwparam ") ||
           text.contains("@raise ") || text.contains("@raises ") || text.contains("@except ") || text.contains("@exception ") ||
           text.contains("@return:") ||
           text.contains("@rtype") || text.contains("@type");
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

  /**
   * Looks for a doc string under given parent.
   *
   * @param parent where to look. For classes and functions, this would be PyStatementList, for modules, PyFile.
   * @return the defining expression, or null.
   */
  @Nullable
  public static PyStringLiteralExpression findDocStringExpression(@Nullable PyElement parent) {
    if (parent != null) {
      PsiElement seeker = PyPsiUtils.getNextNonCommentSibling(parent.getFirstChild(), false);
      if (seeker instanceof PyExpressionStatement) seeker = PyPsiUtils.getNextNonCommentSibling(seeker.getFirstChild(), false);
      if (seeker instanceof PyStringLiteralExpression) return (PyStringLiteralExpression)seeker;
    }
    return null;
  }

  @Nullable
  public static StructuredDocString getStructuredDocString(@NotNull PyDocStringOwner owner) {
    final String value = owner.getDocStringValue();
    return value == null ? null : parse(value, owner);
  }

  /**
   * Returns containing docstring expression of class definition, function definition or module.
   * Useful to test whether particular PSI element is or belongs to such docstring.
   */
  @Nullable
  public static PyStringLiteralExpression getParentDefinitionDocString(@NotNull PsiElement element) {
    final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(element, PyDocStringOwner.class);
    if (docStringOwner != null) {
      final PyStringLiteralExpression docString = docStringOwner.getDocStringExpression();
      if (PsiTreeUtil.isAncestor(docString, element, false)) {
        return docString;
      }
    }
    return null;
  }

  public static boolean isDocStringExpression(@NotNull PyExpression expression) {
    if (getParentDefinitionDocString(expression) == expression) {
      return true;
    }
    if (expression instanceof PyStringLiteralExpression) {
      return isVariableDocString((PyStringLiteralExpression)expression);
    }
    return false;
  }

  @Nullable
  public static String getAttributeDocComment(@NotNull PyTargetExpression attr) {
    if (attr.getParent() instanceof PyAssignmentStatement) {
      final PyAssignmentStatement assignment = (PyAssignmentStatement)attr.getParent();
      final PsiElement prevSibling = PyPsiUtils.getPrevNonWhitespaceSibling(assignment);
      if (prevSibling instanceof PsiComment && prevSibling.getText().startsWith("#:")) {
        return prevSibling.getText().substring(2);
      }
    }
    return null;
  }

  public static boolean isVariableDocString(@NotNull PyStringLiteralExpression expr) {
    final PsiElement parent = expr.getParent();
    if (!(parent instanceof PyExpressionStatement)) {
      return false;
    }
    final PsiElement prevElement = PyPsiUtils.getPrevNonCommentSibling(parent, true);
    if (prevElement instanceof PyAssignmentStatement) {
      if (expr.getText().contains("type:")) return true;

      final PyAssignmentStatement assignmentStatement = (PyAssignmentStatement)prevElement;
      final ScopeOwner scope = PsiTreeUtil.getParentOfType(prevElement, ScopeOwner.class);
      if (scope instanceof PyClass || scope instanceof PyFile) {
        return true;
      }
      if (scope instanceof PyFunction) {
        for (PyExpression target : assignmentStatement.getTargets()) {
          if (PyUtil.isInstanceAttribute(target)) {
            return true;
          }
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
