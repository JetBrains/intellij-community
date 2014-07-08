/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * User: catherine
 */
public class DocStringUtil {
  private static final String PARAMETERS = "Parameters";
  private static final Pattern NUMPY_PARAMETERS_SECTION = Pattern.compile(PARAMETERS + "[ \\t]*\\n[ \\t]*[-]{" + PARAMETERS.length() + "}");
  private static final String RETURNS = "Returns";
  private static final Pattern NUMPY_RETURNS_SECTION = Pattern.compile(RETURNS + "[ \\t]*\\n[ \\t]*[-]{" + RETURNS.length() + "}");
  private static final String SEE_ALSO = "See Also";
  private static final Pattern NUMPY_SEE_ALSO_SECTION = Pattern.compile(SEE_ALSO + "[ \\t]*\\n[ \\t]*[-]{" + SEE_ALSO.length() + "}");
  private static final String EXAMPLES = "Examples";
  private static final Pattern NUMPY_EXAMPLES_SECTION = Pattern.compile(EXAMPLES + "[ \\t]*\\n[ \\t]*[-]{" + EXAMPLES.length() + "}");

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
      return new SphinxDocString(text);
    }
    else if (isEpydocDocString(text)) {
      return new EpydocString(text);
    }
    return null;
  }

  public static boolean isSphinxDocString(@NotNull String text) {
    return text.contains(":param ") || text.contains(":rtype") || text.contains(":type");
  }

  public static boolean isEpydocDocString(@NotNull String text) {
    return text.contains("@param ") || text.contains("@rtype") || text.contains("@type");
  }

  public static boolean isNumpyDocString(@NotNull String text) {
    return text.contains("ndarray") ||
           NUMPY_PARAMETERS_SECTION.matcher(text).find() ||
           NUMPY_RETURNS_SECTION.matcher(text).find() ||
           NUMPY_SEE_ALSO_SECTION.matcher(text).find() ||
           NUMPY_EXAMPLES_SECTION.matcher(text).find();
  }

  /**
   * Looks for a doc string under given parent.
   * @param parent where to look. For classes and functions, this would be PyStatementList, for modules, PyFile.
   * @return the defining expression, or null.
   */
  @Nullable
  public static PyStringLiteralExpression findDocStringExpression(@Nullable PyElement parent) {
    if (parent != null) {
      PsiElement seeker = PyUtil.getFirstNonCommentAfter(parent.getFirstChild());
      if (seeker instanceof PyExpressionStatement) seeker = PyUtil.getFirstNonCommentAfter(seeker.getFirstChild());
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
}
