/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.documentation.docstrings;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.ui.Messages;
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
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DocStringUtil {
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
    final TextRange contentRange = PyStringLiteralExpressionImpl.getNodeTextRange(text);
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
           text.contains(":return:") || text.contains(":returns:") ||
           text.contains(":raise ") || text.contains(":raises ") || text.contains(":except ") || text.contains(":exception ") ||
           text.contains(":rtype") || text.contains(":type");
  }

  public static boolean isLikeEpydocDocString(@NotNull String text) {
    return text.contains("@param ") ||
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
        if (SectionBasedDocString.SECTION_NAMES.contains(lineBefore.trim().toLowerCase())) {
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

  /**
   * Checks that docstring format is set either via element module's {@link com.jetbrains.python.PyNames.DOCFORMAT} attribute or
   * in module settings. If none of them applies, show standard choose dialog, asking user to pick one and updates module settings
   * accordingly.
   *
   * @param anchor PSI element that will be used to locate containing file and project module
   * @return false if no structured docstring format was specified initially and user didn't select any, true otherwise
   */
  public static boolean ensureNotPlainDocstringFormat(@NotNull PsiElement anchor) {
    final Module module = getModuleForElement(anchor);
    if (module == null) {
      return false;
    }

    return ensureNotPlainDocstringFormatForFile(anchor.getContainingFile(), module);
  }

  // Might return {@code null} in some rare cases when PSI element doesn't have an associated module.
  // For instance, an empty IDEA project with a Python scratch file.
  @Nullable
  private static Module getModuleForElement(@NotNull PsiElement element) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module != null) {
      return module;
    }
    
    return ArrayUtil.getFirstElement(ModuleManager.getInstance(element.getProject()).getModules());
  }

  private static boolean ensureNotPlainDocstringFormatForFile(@NotNull PsiFile file, @NotNull Module module) {
    final PyDocumentationSettings settings = PyDocumentationSettings.getInstance(module);
    if (settings.isPlain(file)) {
      final List<String> values = DocStringFormat.ALL_NAMES_BUT_PLAIN;
      final int i =
        Messages.showChooseDialog("Docstring format:", "Select Docstring Type", ArrayUtil.toStringArray(values), values.get(0), null);
      if (i < 0) {
        return false;
      }
      settings.setFormat(DocStringFormat.fromNameOrPlain(values.get(i)));
    }
    return true;
  }
}
