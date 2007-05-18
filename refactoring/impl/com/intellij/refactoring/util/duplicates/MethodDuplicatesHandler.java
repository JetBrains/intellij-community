/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.util.duplicates;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisUIOptions;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.VisibilityUtil;
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
  private static final Logger LOG = Logger.getInstance("#" + MethodDuplicatesHandler.class.getName());

  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file, DataContext dataContext) {
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
    AnalysisScope scope = new AnalysisScope(file);
    final Module module = ModuleUtil.findModuleForPsiElement(file);
    BaseAnalysisActionDialog dlg = new BaseAnalysisActionDialog(RefactoringBundle.message("replace.method.duplicates.scope.chooser.title", REFACTORING_NAME),
                                                                RefactoringBundle.message("replace.method.duplicates.scope.chooser.message"),
                                                                project, scope, module != null ? module.getName() : null, false);
    dlg.show();
    if (dlg.isOK()) {
      scope = dlg.getScope(AnalysisUIOptions.getInstance(project), scope, project, module);

      invokeOnScope(project, method, scope);
    }
  }

  public static void invokeOnScope(final Project project, final PsiMethod method, final AnalysisScope scope) {
    final int [] dupCount = new int[]{0};
    scope.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(final PsiReferenceExpression expression) {
      }

      public void visitFile(final PsiFile file) {
        final VirtualFile virtualFile = file.getVirtualFile();
        LOG.assertTrue(virtualFile != null);
        if (invokeOnElements(project, file, method)) {
          dupCount[0]++;
        }
      }
    });
    if (dupCount[0] == 0) {
      final String message = RefactoringBundle.message("idea.has.not.found.any.code.that.can.be.replaced.with.method.call",
                                                       ApplicationNamesInfo.getInstance().getProductName());
      Messages.showInfoMessage(project, message, REFACTORING_NAME);
    }
  }

  private static boolean invokeOnElements(final Project project, final PsiFile file, final PsiMethod method) {
    final List<Match> duplicates = hasDuplicates(file, method);
    if (duplicates.isEmpty()) return false;
    final VirtualFile virtualFile = file.getVirtualFile();
    LOG.assertTrue(virtualFile != null);
    final Editor editor = FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, virtualFile), false);
    LOG.assertTrue(editor != null);
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
    if (!dialog.isOK()) return true;
    WindowManager.getInstance().getStatusBar(project).setInfo(getStatusMessage(duplicatesNo));
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(new Runnable () {
          public void run() {
            DuplicatesImpl.invoke(project, editor, new MethodDuplicatesMatchProvider(method, duplicates));
          }
        });
      }
    }, REFACTORING_NAME, null);

    WindowManager.getInstance().getStatusBar(project).setInfo("");
    return true;
  }

  public static List<Match> hasDuplicates(final PsiFile file, final PsiMethod method) {
    final PsiCodeBlock body = method.getBody();
    LOG.assertTrue(body != null);
    final PsiStatement[] statements = body.getStatements();
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

    return duplicatesFinder.findDuplicates(file);
  }

  static String getStatusMessage(final int duplicatesNo) {
    return RefactoringBundle.message("method.duplicates.found.message", duplicatesNo);
  }

  private static void showErrorMessage(String message, Project project) {
    CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.METHOD_DUPLICATES, project);
  }

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
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
      match.changeSignature(myMethod);
      final PsiClass containingClass = myMethod.getContainingClass();
      if (isEssentialStaticContextAbsent(match)) {
        myMethod.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
      }
      final PsiElementFactory factory = myMethod.getManager().getElementFactory();
      final boolean needQualifier = match.getInstanceExpression() != null;
      final boolean needStaticQualifier = isExternal(match);
      final @NonNls String text = needQualifier || needStaticQualifier ?  "q." + myMethod.getName() + "()": myMethod.getName() + "()";
      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)factory.createExpressionFromText(text, null);
      methodCallExpression = (PsiMethodCallExpression)CodeStyleManager.getInstance(myMethod.getManager()).reformat(methodCallExpression);
      final PsiParameter[] parameters = myMethod.getParameterList().getParameters();
      for (final PsiParameter parameter : parameters) {
        methodCallExpression.getArgumentList().add(match.getParameterValue(parameter));
      }
      if (needQualifier || needStaticQualifier) {
        final PsiExpression qualifierExpression = methodCallExpression.getMethodExpression().getQualifierExpression();
        LOG.assertTrue(qualifierExpression != null);
        if (needQualifier) {
          qualifierExpression.replace(match.getInstanceExpression());
        } else {
          qualifierExpression.replace(factory.createReferenceExpression(containingClass));
        }
      }
      VisibilityUtil.escalateVisibility(myMethod, match.getMatchStart());
      match.replace(methodCallExpression, null);
    }

    private boolean isExternal(final Match match) {
      return !PsiTreeUtil.isAncestor(myMethod.getContainingClass(), match.getMatchStart(), false);
    }

    private boolean isEssentialStaticContextAbsent(final Match match) {
      if (!myMethod.hasModifierProperty(PsiModifier.STATIC)) {
        if (isExternal(match)) {
          return match.getInstanceExpression() == null;
        }
        if (RefactoringUtil.isInStaticContext(match.getMatchStart(), myMethod.getContainingClass())) return true;
      }
      return false;
    }

    public List<Match> getDuplicates() {
      return myDuplicates;
    }

    public boolean hasDuplicates() {
      return myDuplicates.isEmpty();
    }

    @NotNull
    public String getConfirmDuplicatePrompt(final Match match) {
      final PsiElement matchStart = match.getMatchStart();
      final String visibility = VisibilityUtil.getPossibleVisibility(myMethod, matchStart);
      final boolean shouldBeStatic = isEssentialStaticContextAbsent(match);
      final String signature = match.getChangedSignature(myMethod, myMethod.hasModifierProperty(PsiModifier.STATIC) || shouldBeStatic, visibility);
      if (signature != null) {
        return RefactoringBundle.message("replace.this.code.fragment.and.change.signature", signature);
      }
      final boolean needToEscalateVisibility = !PsiUtil.isAccessible(myMethod, matchStart, null);
      if (needToEscalateVisibility) {
        @NonNls final String visibilityPresentation = visibility == PsiModifier.PACKAGE_LOCAL ? "package local" : visibility;
        if (shouldBeStatic) {
          return RefactoringBundle.message("replace.this.code.fragment.and.make.method.static.visible", visibilityPresentation);
        }
        else {
          return RefactoringBundle.message("replace.this.code.fragment.and.make.method.visible", visibilityPresentation);
        }
      }
      if (shouldBeStatic) {
        return RefactoringBundle.message("replace.this.code.fragment.and.make.method.static");
      }
      return RefactoringBundle.message("replace.this.code.fragment");
    }
  }
}
