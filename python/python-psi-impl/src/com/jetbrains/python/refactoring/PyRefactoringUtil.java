// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.introduce.IntroduceValidator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;

public final class PyRefactoringUtil {
  private PyRefactoringUtil() {
  }

  @NotNull
  public static List<PsiElement> getOccurrences(@NotNull final PsiElement pattern, @Nullable final PsiElement context) {
    if (context == null) {
      return Collections.emptyList();
    }
    final List<PsiElement> occurrences = new ArrayList<>();
    final PyElementVisitor visitor = new PyElementVisitor() {
      @Override
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
      final String suffix = parentText.substring(endOffset);
      final TextRange textRange = TextRange.from(startOffset, endOffset - startOffset);
      final PsiElement fakeExpression = generator.createExpressionFromText(langLevel, prefix + "python" + suffix);
      if (PsiUtilCore.hasErrorElementChild(fakeExpression)) {
        return null;
      }

      expression.putUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE, Pair.create(parent, textRange));
      return expression;
    }
    return null;
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

  public static PsiElement @NotNull [] findStatementsInRange(@NotNull final PsiFile file, int startOffset, int endOffset) {
    ArrayList<PsiElement> array = new ArrayList<>();

    PsiElement element1 = file.findElementAt(startOffset);
    PsiElement element2 = file.findElementAt(endOffset - 1);
    PsiElement endComment = null;

    boolean startsWithWhitespace = false;
    boolean endsWithWhitespace = false;
    if (element1 instanceof PsiWhiteSpace) {
      startOffset = element1.getTextRange().getEndOffset();
      element1 = file.findElementAt(startOffset);
      startsWithWhitespace = true;
    }
    if (element2 instanceof PsiWhiteSpace) {
      element2 = PsiTreeUtil.skipWhitespacesBackward(element2);
      endsWithWhitespace = true;
    }
    while (element2 instanceof PsiComment) {
      endComment = element2;
      element2 = PsiTreeUtil.skipWhitespacesAndCommentsBackward(element2);
      endsWithWhitespace = true;
    }

    while (element1 instanceof PsiComment) {
      array.add(element1);
      element1 = PsiTreeUtil.skipWhitespacesForward(element1);
      startsWithWhitespace = true;
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
    if (startOffset != element1.getTextRange().getStartOffset() && !startsWithWhitespace) {
      return PsiElement.EMPTY_ARRAY;
    }

    if (!parent.equals(element2)) {
      while (!parent.equals(element2.getParent())) {
        element2 = element2.getParent();
      }
    }
    if (endOffset != element2.getTextRange().getEndOffset() && !endsWithWhitespace) {
      return PsiElement.EMPTY_ARRAY;
    }

    if (element1 instanceof PyFunction || element1 instanceof PyClass) {
      return PsiElement.EMPTY_ARRAY;
    }
    if (element2 instanceof PyFunction || element2 instanceof PyClass) {
      return PsiElement.EMPTY_ARRAY;
    }

    PsiElement[] children = parent.getChildren();

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

    while (endComment instanceof PsiComment) {
      array.add(endComment);
      endComment = PsiTreeUtil.skipWhitespacesForward(endComment);
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

  /**
   * Selects the shortest unique name inside the scope of scopeAnchor generated using {@link NameSuggesterUtil#generateNamesByType(String)}.
   * If none of those names is suitable, unique names is made by appending number suffix.
   *
   * @param typeName    initial type name for generator
   * @param scopeAnchor PSI element used to determine correct scope
   * @return unique name in the scope of scopeAnchor
   */
  @NotNull
  public static String selectUniqueNameFromType(@NotNull String typeName, @NotNull PsiElement scopeAnchor) {
    return selectUniqueName(typeName, true, scopeAnchor, PyRefactoringUtil::isValidNewName);
  }

  /**
   * Selects the shortest unique name inside the scope of scopeAnchor generated using {@link NameSuggesterUtil#generateNames(String)}.
   * If none of those names is suitable, unique names is made by appending number suffix.
   *
   * @param templateName initial template name for generator
   * @param scopeAnchor  PSI element used to determine correct scope
   * @return unique name in the scope of scopeAnchor
   */
  @NotNull
  public static String selectUniqueName(@NotNull String templateName, @NotNull PsiElement scopeAnchor) {
    return selectUniqueName(templateName, false, scopeAnchor, PyRefactoringUtil::isValidNewName);
  }

  @NotNull
  public static String selectUniqueName(@NotNull String templateName, @NotNull PsiElement scopeAnchor, @NotNull BiPredicate<String, PsiElement> isValid) {
    return selectUniqueName(templateName, false, scopeAnchor, isValid);
  }

  @NotNull
  private static String selectUniqueName(@NotNull String templateName, boolean templateIsType, @NotNull PsiElement scopeAnchor, @NotNull BiPredicate<String, PsiElement> isValid) {
    final Collection<String> suggestions;
    if (templateIsType) {
      suggestions = NameSuggesterUtil.generateNamesByType(templateName);
    }
    else {
      suggestions = NameSuggesterUtil.generateNames(templateName);
    }
    for (String name : suggestions) {
      if (isValid.test(name, scopeAnchor)) {
        return name;
      }
    }

    final String shortestName = ContainerUtil.getFirstItem(suggestions);
    return appendNumberUntilValid(shortestName, scopeAnchor, isValid);
  }

  /**
   * Appends increasing numbers starting from 1 to the name until it becomes unique within the scope of the scopeAnchor.
   *
   * @param name        initial name
   * @param scopeAnchor PSI element used to determine correct scope
   * @param predicate used to test if suggested name is valid
   * @return unique name in the scope probably with number suffix appended
   */
  @NotNull
  public static String appendNumberUntilValid(@NotNull String name, @NotNull PsiElement scopeAnchor, @NotNull BiPredicate<String, PsiElement> predicate) {
    int counter = 1;
    String candidate = name;
    while (!predicate.test(candidate, scopeAnchor)) {
      candidate = name + counter;
      counter++;
    }
    return candidate;
  }

  public static boolean isValidNewName(@NotNull String name, @NotNull PsiElement scopeAnchor) {
    return !(IntroduceValidator.isDefinedInScope(name, scopeAnchor) || PyNames.isReserved(name));
  }
}
