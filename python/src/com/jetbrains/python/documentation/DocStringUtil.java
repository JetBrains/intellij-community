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
package com.jetbrains.python.documentation;

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
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;

/**
 * User: catherine
 */
public class DocStringUtil {
  private DocStringUtil() {
  }

  @Nullable
  public static String getDocStringValue(@NotNull PyDocStringOwner owner) {
    return PyPsiUtils.strValue(owner.getDocStringExpression());
  }

  @Nullable
  public static StructuredDocString parse(@Nullable String text) {
    if (text == null) {
      return null;
    }
    if (isSphinxDocString(text)) {
      return parseDocStringContent(DocStringFormat.REST, text);
    }
    if (isGoogleDocString(text)) {
      return parseDocStringContent(DocStringFormat.GOOGLE, text);
    }
    if (isNumpyDocstring(text)) {
      return parseDocStringContent(DocStringFormat.NUMPY, text);
    }
    return parseDocStringContent(DocStringFormat.EPYTEXT, text);
  }


  @NotNull
  public static StructuredDocString parseDocString(@NotNull DocStringFormat format,
                                                   @NotNull PyStringLiteralExpression literalExpression) {
    return parseDocString(format, literalExpression.getStringNodes().get(0));
  }

  @NotNull
  public static StructuredDocString parseDocString(@NotNull DocStringFormat format, @NotNull ASTNode node) {
    //Preconditions.checkArgument(node.getElementType() == PyTokenTypes.DOCSTRING);
    return parseDocString(format, node.getText());
  }


  @NotNull
  public static StructuredDocString parseDocString(@NotNull DocStringFormat format, @NotNull String stringText) {
    return parseDocString(format, stripSuffixAndQuotes(stringText));
  }

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
        throw new UnsupportedOperationException("Not supported for plain docstrings. Use PyDocStringUtil#ensureNotPlainDocstringFormat");
    }
  }

  @NotNull
  private static Substring stripSuffixAndQuotes(@NotNull String text) {
    final TextRange contentRange = PyStringLiteralExpressionImpl.getNodeTextRange(text);
    return new Substring(text, contentRange.getStartOffset(), contentRange.getEndOffset());
  }

  public static boolean isSphinxDocString(@NotNull String text) {
    return text.contains(":param ") || text.contains(":rtype") || text.contains(":type");
  }

  public static boolean isEpydocDocString(@NotNull String text) {
    return text.contains("@param ") || text.contains("@rtype") || text.contains("@type");
  }

  public static boolean isGoogleDocString(@NotNull String text) {
    final Matcher matcher = GoogleCodeStyleDocString.SECTION_HEADER_RE.matcher(text);
    if (!matcher.find()) {
      return false;
    }
    @NonNls final String foundName = matcher.group(1).trim();
    return SectionBasedDocString.SECTION_NAMES.contains(foundName.toLowerCase());
  }

  public static boolean isNumpyDocstring(@NotNull String text) {
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

  public static StructuredDocString getStructuredDocString(PyDocStringOwner owner) {
    return parse(owner.getDocStringValue());
  }

  public static boolean isDocStringExpression(@Nullable PyExpression expression) {
    final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(expression, PyDocStringOwner.class);
    if (docStringOwner != null) {
      if (docStringOwner.getDocStringExpression() == expression) {
        return true;
      }
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
      PsiElement prevSibling = assignment.getPrevSibling();
      while (prevSibling != null && (prevSibling instanceof PsiWhiteSpace)) {
        prevSibling = prevSibling.getPrevSibling();
      }
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
    PsiElement prevElement = parent.getPrevSibling();
    while (prevElement instanceof PsiWhiteSpace || prevElement instanceof PsiComment) {
      prevElement = prevElement.getPrevSibling();
    }
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
    return ensureNotPlainDocstringFormatForFile(anchor.getContainingFile(), getModuleForElement(anchor));
  }

  @NotNull
  private static Module getModuleForElement(@NotNull PsiElement element) {
    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module == null) {
      module = ModuleManager.getInstance(element.getProject()).getModules()[0];
    }
    return module;
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

  @NotNull
  public static DocStringFormat getDocStringFormat(@NotNull PsiElement anchor) {
    final PyDocumentationSettings settings = PyDocumentationSettings.getInstance(getModuleForElement(anchor));
    return settings.getFormatForFile(anchor.getContainingFile());
  }
}
