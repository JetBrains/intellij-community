/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.util.duplicates;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * @author dsl
 */
public class MethodDuplicatesHandler implements RefactoringActionHandler {
  public static final String REFACTORING_NAME = "Replace Method Code Duplicates";

  public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement element = file.findElementAt(offset);
    final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    if (method == null) {
      String message = "Cannot perform the refactoring.\n" +
                       "Locate caret inside a method.";
      showErrorMessage(message, project);
      return;
    }
    if (!file.isWritable()) {
      RefactoringMessageUtil.showReadOnlyElementRefactoringMessage(project, method);
      return;
    }
    if (method.isConstructor()) {
      String message = "Cannot perform refactoring.\n" +
                       "Replace With Method Call does not work for constructors";
      showErrorMessage(message, project);
    }
    final PsiCodeBlock body = method.getBody();
    if (body == null) {
      String message = "Cannot perform refactring.\n" +
                       "Method " + method.getName() + " does not have a body";
      showErrorMessage(message, project);
      return;
    }
    final PsiStatement[] statements = body.getStatements();
    if (statements.length == 0) {
      String message = "Cannot perform refactoring.\n" +
                       "Method " + method.getName() + " has an empty body";

      showErrorMessage(message, project);
      return;      
    }
    final DuplicatesFinder duplicatesFinder;
    final PsiElement[] pattern;
    if (statements.length != 1 || !(statements[0] instanceof PsiReturnStatement)) {
      pattern = statements;
    } else {
      final PsiExpression returnValue = ((PsiReturnStatement)statements[0]).getReturnValue();
      if (returnValue != null) {
        pattern = new PsiElement[]{returnValue};
      }
      else {
        pattern = statements;
      }
    }
    duplicatesFinder = new DuplicatesFinder(pattern, Arrays.asList(method.getParameterList().getParameters()),
                                            new ArrayList<PsiVariable>(), !method.hasModifierProperty(PsiModifier.STATIC));

    PsiElement scope = file;
    final List<Match> duplicates = duplicatesFinder.findDuplicates(scope);
    if (duplicates.isEmpty()) {
      final String message = "IDEA has not found any code that can be replaced with method call";
      Messages.showInfoMessage(project, message, REFACTORING_NAME);
      return;
    }
    final int duplicatesNo = duplicates.size();
    final ArrayList highlighters = new ArrayList();
    for (Iterator<Match> iterator = duplicates.iterator(); iterator.hasNext();) {
      final Match match = iterator.next();
      DuplicatesImpl.highlightMatch(project, editor, match, highlighters);
    }
    final MethodDuplicatesDialog dialog = new MethodDuplicatesDialog(project, method, duplicatesNo);
    dialog.show();
    for (Iterator iterator = highlighters.iterator(); iterator.hasNext();) {
      final RangeHighlighter rangeHighlighter = (RangeHighlighter)iterator.next();
      HighlightManager.getInstance(project).removeSegmentHighlighter(editor, rangeHighlighter);
    }
    if (!dialog.isOK()) return;
    WindowManager.getInstance().getStatusBar(project).setInfo(getStatusMessage(duplicatesNo));
    DuplicatesImpl.invoke(project, editor, REFACTORING_NAME, new MethodDuplicatesMatchProvider(method, duplicates));
    WindowManager.getInstance().getStatusBar(project).setInfo("");
  }

  static String getStatusMessage(final int duplicatesNo) {
    return duplicatesNo + " code " + (duplicatesNo == 1 ? "fragment" : "framents") + " found";
  }

  private void showErrorMessage(String message, Project project) {
    RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.METHOD_DUPLICATES, project);
  }

  public void invoke(Project project, PsiElement[] elements, DataContext dataContext) {
    throw new UnsupportedOperationException();
  }

  private static class MethodDuplicatesMatchProvider implements MatchProvider {
    private final PsiMethod myMethod;
    private final List<Match> myDuplicates;

    public MethodDuplicatesMatchProvider(PsiMethod method, List<Match> duplicates) {
      myMethod = method;
      myDuplicates = duplicates;
    }

    public void processMatch(Match match) throws IncorrectOperationException {
      final PsiElementFactory factory = myMethod.getManager().getElementFactory();
      final boolean needQualifier = match.getInstanceExpression() != null;
      final String text = needQualifier ?  "q." + myMethod.getName() + "()": myMethod.getName() + "()";
      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)factory.createExpressionFromText(text, null);
      methodCallExpression = (PsiMethodCallExpression)CodeStyleManager.getInstance(myMethod.getManager()).reformat(methodCallExpression);
      final PsiParameter[] parameters = myMethod.getParameterList().getParameters();
      for (int i = 0; i < parameters.length; i++) {
        final PsiParameter parameter = parameters[i];
        methodCallExpression.getArgumentList().add(match.getParameterValue(parameter));
      }
      if (needQualifier) {
        methodCallExpression.getMethodExpression().getQualifierExpression().replace(match.getInstanceExpression());
      }
      match.replace(methodCallExpression, null);
    }

    public List<Match> getDuplicates() {
      return myDuplicates;
    }

    public boolean hasDuplicates() {
      return myDuplicates.isEmpty();
    }
  }
}
