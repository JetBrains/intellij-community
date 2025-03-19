// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.wrapreturnvalue;

import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRareRefactoringsBundle;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

class WrapReturnValueHandler implements RefactoringActionHandler, ContextAwareActionHandler {
  @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext){
        final ScrollingModel scrollingModel = editor.getScrollingModel();
        scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE);
        PsiMethod selectedMethod = getSelectedMethod(editor, file);
        if(selectedMethod == null){
          CommonRefactoringUtil.showErrorHint(project, editor, RefactorJBundle.message("cannot.perform.the.refactoring") + RefactorJBundle.message(
              "the.caret.should.be.positioned.within.a.method.declaration.to.be.refactored"), getRefactoringNameText(), this.getHelpID());
          return;
        }
        invoke(project, selectedMethod, editor);
    }

    @Override
    public boolean isAvailableForQuickList(@NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext dataContext) {
        return getSelectedMethod(editor, file) != null;
    }

    private static PsiMethod getSelectedMethod(Editor editor, PsiFile file) {
        final int caret = editor.getCaretModel().getOffset();
        final PsiElement elementAt = file.findElementAt(caret);
        return MethodUtils.getJavaMethodFromHeader(elementAt);
    }

    protected String getRefactoringName(){
        return getRefactoringNameText();
    }

    protected String getHelpID(){
        return HelpID.WrapReturnValue;
    }

    @Override
    public void invoke(@NotNull Project project,
                       PsiElement @NotNull [] elements,
                       DataContext dataContext){
        if(elements.length != 1){
            return;
        }
        PsiMethod method =
                PsiTreeUtil.getParentOfType(elements[0], PsiMethod.class, false);
        if(method == null){
            return;
        }
      Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
      invoke(project, method, editor);
    }

  private void invoke(final Project project, PsiMethod method, Editor editor) {
    if(method.isConstructor()){
      CommonRefactoringUtil.showErrorHint(project, editor, RefactorJBundle.message("cannot.perform.the.refactoring") + JavaRareRefactoringsBundle.message("constructor.returns.can.not.be.wrapped"),
                                          getRefactoringNameText(), this.getHelpID());
      return;
    }
    final PsiType returnType = method.getReturnType();
    if(PsiTypes.voidType().equals(returnType)){
      CommonRefactoringUtil.showErrorHint(project, editor, RefactorJBundle.message("cannot.perform.the.refactoring") + JavaRareRefactoringsBundle.message("method.selected.returns.void"),
                                          getRefactoringNameText(), this.getHelpID());
      return;
    }
    method = SuperMethodWarningUtil.checkSuperMethod(method);
    if (method == null) return;

    if(method instanceof PsiCompiledElement){
      CommonRefactoringUtil.showErrorHint(project, editor, RefactorJBundle.message("cannot.perform.the.refactoring") + RefactorJBundle.message(
        "the.selected.method.cannot.be.wrapped.because.it.is.defined.in.a.non.project.class"), getRefactoringNameText(), this.getHelpID());
      return;
    }

    new WrapReturnValueDialog(method).show();


  }

  public static @NlsContexts.DialogTitle String getRefactoringNameText() {
    return JavaRareRefactoringsBundle.message("wrap.return.value");
  }
}
