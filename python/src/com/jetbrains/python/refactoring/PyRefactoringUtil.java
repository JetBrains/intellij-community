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
package com.jetbrains.python.refactoring;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.findUsages.PyFindUsagesHandlerFactory;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 20, 2009
 * Time: 7:07:02 PM
 */
public class PyRefactoringUtil {
  private PyRefactoringUtil() {
  }

  @NotNull
  public static List<PsiElement> getOccurrences(@NotNull final PsiElement pattern, @Nullable final PsiElement context) {
    if (context == null) {
      return Collections.emptyList();
    }
    final List<PsiElement> occurrences = new ArrayList<>();
    final PyElementVisitor visitor = new PyElementVisitor() {
      public void visitElement(@NotNull final PsiElement element) {
        if (element instanceof PyParameter) {
          return;
        }
        if (PsiEquivalenceUtil.areElementsEquivalent(element, pattern)) {
          occurrences.add(element);
          return;
        }
        if (element instanceof PyStringLiteralExpression) {
          final Pair<PsiElement, TextRange> selection = pattern.getUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE);
          if (selection != null) {
            final String substring = selection.getSecond().substring(pattern.getText());
            final PyStringLiteralExpression expr = (PyStringLiteralExpression)element;
            final String text = element.getText();
            if (text != null && expr.getStringNodes().size() == 1) {
              final int start = text.indexOf(substring);
              if (start >= 0) {
                element.putUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE, Pair.create(element, TextRange.from(start, substring.length())));
                occurrences.add(element);
                return;
              }
            }
          }
        }
        element.acceptChildren(this);
      }
    };
    context.acceptChildren(visitor);
    return occurrences;
  }

  @Nullable
  public static PyExpression getSelectedExpression(@NotNull final Project project,
                                                   @NotNull PsiFile file,
                                                   @NotNull final PsiElement element1,
                                                   @NotNull final PsiElement element2) {
    PsiElement parent = PsiTreeUtil.findCommonParent(element1, element2);
    if (parent != null && !(parent instanceof PyElement)) {
      parent = PsiTreeUtil.getParentOfType(parent, PyElement.class);
    }
    if (parent == null) {
      return null;
    }
    // If it is PyIfPart for example, parent if statement, we should deny
    if (!(parent instanceof PyExpression)){
      return null;
    }
    // We cannot extract anything within import statements
    if (PsiTreeUtil.getParentOfType(parent, PyImportStatement.class, PyFromImportStatement.class) != null){
      return null;
    }
    if ((element1 == PsiTreeUtil.getDeepestFirst(parent)) && (element2 == PsiTreeUtil.getDeepestLast(parent))) {
      return (PyExpression) parent;
    }

    // Check if selection breaks AST node in binary expression
    if (parent instanceof PyBinaryExpression) {
      final String selection = file.getText().substring(element1.getTextOffset(), element2.getTextOffset() + element2.getTextLength());
      final PyElementGenerator generator = PyElementGenerator.getInstance(project);
      final LanguageLevel langLevel = LanguageLevel.forElement(element1);
      final PyExpression expression = generator.createFromText(langLevel, PyAssignmentStatement.class, "z=" + selection).getAssignedValue();
      if (!(expression instanceof PyBinaryExpression) || PsiUtilCore.hasErrorElementChild(expression)) {
        return null;
      }
      final String parentText = parent.getText();
      final int startOffset = element1.getTextOffset() - parent.getTextOffset() - 1;
      if (startOffset < 0) {
        return null;
      }
      final int endOffset = element2.getTextOffset() + element2.getTextLength() - parent.getTextOffset();

      final String prefix = parentText.substring(0, startOffset);
      final String suffix = parentText.substring(endOffset, parentText.length());
      final TextRange textRange = TextRange.from(startOffset, endOffset - startOffset);
      final PsiElement fakeExpression = generator.createFromText(langLevel, parent.getClass(), prefix + "python" + suffix);
      if (PsiUtilCore.hasErrorElementChild(fakeExpression)) {
        return null;
      }

      expression.putUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE, Pair.create(parent, textRange));
      return expression;
    }
    return null;
  }

  @NotNull
  public static Collection<String> collectUsedNames(@Nullable final PsiElement scope) {
    if (!(scope instanceof PyClass) && !(scope instanceof PyFile) && !(scope instanceof PyFunction)) {
      return Collections.emptyList();
    }
    final Set<String> variables = new HashSet<String>() {
      @Override
      public boolean add(String s) {
        return s != null && super.add(s);
      }
    };
    scope.acceptChildren(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyTargetExpression(@NotNull final PyTargetExpression node) {
        variables.add(node.getName());
      }

      @Override
      public void visitPyNamedParameter(@NotNull final PyNamedParameter node) {
        variables.add(node.getName());
      }

      @Override
      public void visitPyReferenceExpression(PyReferenceExpression node) {
        if (!node.isQualified()) {
          variables.add(node.getReferencedName());
        }
        else {
          super.visitPyReferenceExpression(node);
        }
      }

      @Override
      public void visitPyFunction(@NotNull final PyFunction node) {
        variables.add(node.getName());
      }

      @Override
      public void visitPyClass(@NotNull final PyClass node) {
        variables.add(node.getName());
      }
    });
    return variables;
  }

  @Nullable
  public static PsiElement findExpressionInRange(@NotNull final PsiFile file, int startOffset, int endOffset) {
    PsiElement element1 = file.findElementAt(startOffset);
    PsiElement element2 = file.findElementAt(endOffset - 1);
    if (element1 instanceof PsiWhiteSpace) {
      startOffset = element1.getTextRange().getEndOffset();
      element1 = file.findElementAt(startOffset);
    }
    if (element2 instanceof PsiWhiteSpace) {
      endOffset = element2.getTextRange().getStartOffset();
      element2 = file.findElementAt(endOffset - 1);
    }
    if (element1 == null || element2 == null) {
      return null;
    }
    return getSelectedExpression(file.getProject(), file, element1, element2);
  }

  @NotNull
  public static PsiElement[] findStatementsInRange(@NotNull final PsiFile file, int startOffset, int endOffset) {
    PsiElement element1 = file.findElementAt(startOffset);
    PsiElement element2 = file.findElementAt(endOffset - 1);
    if (element1 instanceof PsiWhiteSpace) {
      startOffset = element1.getTextRange().getEndOffset();
      element1 = file.findElementAt(startOffset);
    }
    if (element2 instanceof PsiWhiteSpace) {
      endOffset = element2.getTextRange().getStartOffset();
      element2 = file.findElementAt(endOffset - 1);
    }
    if (element1 == null || element2 == null) {
      return PsiElement.EMPTY_ARRAY;
    }

    PsiElement parent = PsiTreeUtil.findCommonParent(element1, element2);
    if (parent == null) {
      return PsiElement.EMPTY_ARRAY;
    }

    while (true) {
      if (parent instanceof PyStatement) {
        parent = parent.getParent();
        break;
      }
      if (parent instanceof PyStatementList) {
        break;
      }
      if (parent == null || parent instanceof PsiFile) {
        return PsiElement.EMPTY_ARRAY;
      }
      parent = parent.getParent();
    }

    if (!parent.equals(element1)) {
      while (!parent.equals(element1.getParent())) {
        element1 = element1.getParent();
      }
    }
    if (startOffset != element1.getTextRange().getStartOffset()) {
      return PsiElement.EMPTY_ARRAY;
    }

    if (!parent.equals(element2)) {
      while (!parent.equals(element2.getParent())) {
        element2 = element2.getParent();
      }
    }
    if (endOffset != element2.getTextRange().getEndOffset()) {
      return PsiElement.EMPTY_ARRAY;
    }

    if (element1 instanceof PyFunction || element1 instanceof PyClass) {
      return PsiElement.EMPTY_ARRAY;
    }
    if (element2 instanceof PyFunction || element2 instanceof PyClass) {
      return PsiElement.EMPTY_ARRAY;
    }

    PsiElement[] children = parent.getChildren();
    ArrayList<PsiElement> array = new ArrayList<>();
    boolean flag = false;
    for (PsiElement child : children) {
      if (child.equals(element1)) {
        flag = true;
      }
      if (flag && !(child instanceof PsiWhiteSpace)) {
        array.add(child);
      }
      if (child.equals(element2)) {
        break;
      }
    }

    for (PsiElement element : array) {
      if (!(element instanceof PyStatement || element instanceof PsiWhiteSpace || element instanceof PsiComment)) {
        return PsiElement.EMPTY_ARRAY;
      }
    }
    return PsiUtilCore.toPsiElementArray(array);
  }

  public static boolean areConflictingMethods(PyFunction pyFunction, PyFunction pyFunction1) {
    final PyParameter[] firstParams = pyFunction.getParameterList().getParameters();
    final PyParameter[] secondParams = pyFunction1.getParameterList().getParameters();
    final String firstName = pyFunction.getName();
    final String secondName = pyFunction1.getName();

    return Comparing.strEqual(firstName, secondName) && firstParams.length == secondParams.length;
  }

  @NotNull
  public static List<UsageInfo> findUsages(@NotNull PsiNamedElement element, boolean forHighlightUsages) {
    final List<UsageInfo> usages = new ArrayList<>();
    final FindUsagesHandler handler = new PyFindUsagesHandlerFactory().createFindUsagesHandler(element, forHighlightUsages);
    assert handler != null;
    final List<PsiElement> elementsToProcess = new ArrayList<>();
    Collections.addAll(elementsToProcess, handler.getPrimaryElements());
    Collections.addAll(elementsToProcess, handler.getSecondaryElements());
    for (PsiElement e : elementsToProcess) {
      handler.processElementUsages(e, usageInfo -> {
        if (!usageInfo.isNonCodeUsage) {
          usages.add(usageInfo);
        }
        return true;
      }, FindUsagesHandler.createFindUsagesOptions(element.getProject(), null));
    }
    return usages;
  }
}
