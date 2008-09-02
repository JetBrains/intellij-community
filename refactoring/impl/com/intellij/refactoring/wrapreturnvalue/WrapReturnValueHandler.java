package com.intellij.refactoring.wrapreturnvalue;

import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;

class WrapReturnValueHandler implements RefactoringActionHandler {
    public static final String REFACTORING_NAME = RefactorJBundle.message("wrap.return.value");

    public void invoke(Project project,
                       Editor editor,
                       PsiFile file,
                       DataContext dataContext){
        final ScrollingModel scrollingModel = editor.getScrollingModel();
        scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE);
        final PsiElement element = (PsiElement) dataContext.getData(DataConstants.PSI_ELEMENT);
        PsiMethod selectedMethod = null;
        if(element instanceof PsiMethod){
            selectedMethod = (PsiMethod) element;
        } else{
            final CaretModel caretModel = editor.getCaretModel();
            final int position = caretModel.getOffset();
            PsiElement selectedElement = file.findElementAt(position);
            while(selectedElement != null){
                if(selectedElement instanceof PsiMethod){
                    selectedMethod = (PsiMethod) selectedElement;
                    break;
                }
                selectedElement = selectedElement.getParent();
            }
        }
        if(selectedMethod == null){
          CommonRefactoringUtil.showErrorMessage(null, RefactorJBundle.message("cannot.perform.the.refactoring") +
                        RefactorJBundle
                                .message("the.caret.should.be.positioned.at.the.name.of.the.method.to.be.refactored"), this.getHelpID(),
                                                 project);
          return;
        }
      invoke(project, selectedMethod);
    }

    protected String getRefactoringName(){
        return REFACTORING_NAME;
    }

    protected String getHelpID(){
        return HelpID.WrapReturnValue;
    }

    public void invoke(Project project,
                       PsiElement[] elements,
                       DataContext dataContext){
        if(elements.length != 1){
            return;
        }
        PsiMethod method =
                PsiTreeUtil.getParentOfType(elements[0], PsiMethod.class, false);
        if(method == null){
            return;
        }
      invoke(project, method);
    }

  private void invoke(final Project project, PsiMethod method) {
    if(method.isConstructor()){
          CommonRefactoringUtil.showErrorMessage(null, RefactorJBundle.message("cannot.perform.the.refactoring") +
                    RefactorJBundle.message("constructor.returns.can.not.be.wrapped"), this.getHelpID(), project);
      return;
    }
    final PsiType returnType = method.getReturnType();
    if(PsiType.VOID.equals(returnType)){
          CommonRefactoringUtil.showErrorMessage(null, RefactorJBundle.message("cannot.perform.the.refactoring") +
                    RefactorJBundle.message("method.selected.returns.void"), this.getHelpID(), project);
      return;
    }
    method = SuperMethodWarningUtil.checkSuperMethod(method, RefactoringBundle.message("to.refactor"));
    if (method == null) return;

    if(method instanceof PsiCompiledElement){
      CommonRefactoringUtil.showErrorMessage(null, RefactorJBundle.message("cannot.perform.the.refactoring") +
                    RefactorJBundle.message(
                            "the.selected.method.cannot.be.wrapped.because.it.is.defined.in.a.non.project.class"), this.getHelpID(),
                                             project);
      return;
    }

    new WrapReturnValueDialog(method).show();


  }



}
