/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.util.duplicates;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author dsl
 */
public class MethodDuplicatesHandler implements RefactoringActionHandler {
  public static final String REFACTORING_NAME = RefactoringBundle.message("replace.method.code.duplicates.title");

  public void invoke(final Project project, final Editor editor, PsiFile file, DataContext dataContext) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement element = file.findElementAt(offset);
    final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    if (method == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("locate.caret.inside.a.method"));
      showErrorMessage(message, project);
      return;
    }
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return;
    if (method.isConstructor()) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("replace.with.method.call.does.not.work.for.constructors"));
      showErrorMessage(message, project);
    }
    final PsiCodeBlock body = method.getBody();
    if (body == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("method.does.not.have.a.body", method.getName()));
      showErrorMessage(message, project);
      return;
    }
    final PsiStatement[] statements = body.getStatements();
    if (statements.length == 0) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("method.has.an.empty.body", method.getName()));

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
                                            new ArrayList<PsiVariable>());

    final List<Match> duplicates = duplicatesFinder.findDuplicates(file);
    if (duplicates.isEmpty()) {
      final String message =
        RefactoringBundle.message("idea.has.not.found.any.code.that.can.be.replaced.with.method.call", ApplicationNamesInfo.getInstance().getProductName());
      Messages.showInfoMessage(project, message, REFACTORING_NAME);
      return;
    }
    final int duplicatesNo = duplicates.size();
    final ArrayList<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    for (final Match match : duplicates) {
      DuplicatesImpl.highlightMatch(project, editor, match, highlighters);
    }
    final MethodDuplicatesDialog dialog = new MethodDuplicatesDialog(project, method, duplicatesNo);
    dialog.show();
    for (final RangeHighlighter rangeHighlighter : highlighters) {
      HighlightManager.getInstance(project).removeSegmentHighlighter(editor, rangeHighlighter);
    }
    if (!dialog.isOK()) return;
    WindowManager.getInstance().getStatusBar(project).setInfo(getStatusMessage(duplicatesNo));
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        DuplicatesImpl.invoke(project, editor, new MethodDuplicatesMatchProvider(method, duplicates));
      }
    }, REFACTORING_NAME, null);

    WindowManager.getInstance().getStatusBar(project).setInfo("");
  }

  static String getStatusMessage(final int duplicatesNo) {
    return RefactoringBundle.message("method.duplicates.found.message", duplicatesNo);
  }

  private static void showErrorMessage(String message, Project project) {
    CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.METHOD_DUPLICATES, project);
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
      if (RefactoringUtil.isInStaticContext(match.getMatchStart())) {
        myMethod.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
      }
      final PsiElementFactory factory = myMethod.getManager().getElementFactory();
      final boolean needQualifier = match.getInstanceExpression() != null;
      final @NonNls String text = needQualifier ?  "q." + myMethod.getName() + "()": myMethod.getName() + "()";
      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)factory.createExpressionFromText(text, null);
      methodCallExpression = (PsiMethodCallExpression)CodeStyleManager.getInstance(myMethod.getManager()).reformat(methodCallExpression);
      final PsiParameter[] parameters = myMethod.getParameterList().getParameters();
      for (final PsiParameter parameter : parameters) {
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

    @NotNull
    public String getConfirmDuplicatePrompt(Match match) {
      if (RefactoringUtil.isInStaticContext(match.getMatchStart()) && !myMethod.hasModifierProperty(PsiModifier.STATIC)) {
      return RefactoringBundle.message("replace.this.code.fragment.and.make.method.static");
    }
    return RefactoringBundle.message("replace.this.code.fragment");
    }
  }
}
