package com.intellij.refactoring.removemiddleman;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.RefactorJHelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.psi.DelegationUtils;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.sixrr.rpp.RefactorJConfig;

class RemoveMiddlemanHandler implements RefactoringActionHandler {
    private static final String REFACTORING_NAME = RefactorJBundle.message("remove.middleman");

    protected String getRefactoringName(){
        return REFACTORING_NAME;
    }

    protected String getHelpID(){
        return RefactorJHelpID.RemoveMiddleman;
    }

    public void invoke(Project project,
                       Editor editor,
                       PsiFile file,
                       DataContext dataContext){
        final ScrollingModel scrollingModel = editor.getScrollingModel();
        scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE);
        final PsiElement element = (PsiElement) dataContext.getData(DataConstants.PSI_ELEMENT);
        if(!(element instanceof PsiField)){
          CommonRefactoringUtil.showErrorMessage(null, RefactorJBundle.message("cannot.perform.the.refactoring") +
                        RefactorJBundle.message("the.caret.should.be.positioned.at.the.name.of.the.field.to.be.refactored"), this.getHelpID(),
                                                 project);
          return;
        }
        final PsiField field = (PsiField) element;
        invoke(field);
    }

    public void invoke(Project project,
                       PsiElement[] elements,
                       DataContext dataContext){
        if(elements.length != 1){
            return;
        }
        if(elements[0] instanceof PsiField){
            invoke((PsiField) elements[0]);
        }
    }

    private void invoke(final PsiField field){
        final Project project = field.getProject();
        if(!DelegationUtils.fieldUsedAsDelegate(field)){
            final String message = RefactorJBundle.message("cannot.perform.the.refactoring") +
                    RefactorJBundle.message("field.selected.is.not.used.as.a.delegate");
          CommonRefactoringUtil.showErrorMessage(null, message, this.getHelpID(), project);
          return;
        }
        final RefactorJConfig config = RefactorJConfig.getInstance();
        final RemoveMiddlemanDialog dialog = new RemoveMiddlemanDialog(field, config.REMOVE_MIDDLEMAN_DELETE_METHODS);

        dialog.show();
        if(!dialog.isOK()){
            return;
        }
        final boolean previewUsages = dialog.isPreviewUsages();
        final boolean removeMethods = dialog.removeMethods();
        config.REMOVE_MIDDLEMAN_DELETE_METHODS = removeMethods;
        /* todo perform(project, new Runnable(){
            public void run(){
                final RemoveMiddlemanProcessor processor =
                        new RemoveMiddlemanProcessor(field, removeMethods, previewUsages);
                processor.run();
            }
        });*/
    }
}
