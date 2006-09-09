package com.intellij.refactoring.safeDelete;

import com.intellij.ide.util.DeleteUtil;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;

import java.util.Arrays;
import java.util.Set;

/**
 * @author dsl
 */
public class SafeDeleteHandler implements RefactoringActionHandler {
  public static final String REFACTORING_NAME = RefactoringBundle.message("safe.delete.title");

  public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = (PsiElement) dataContext.getData(DataConstants.PSI_ELEMENT);
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    if (element == null || !SafeDeleteProcessor.validElement(element)) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("is.not.supported.in.the.current.context", REFACTORING_NAME));
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.SAFE_DELETE, project);
      return;
    }
    invoke(project, new PsiElement[]{element}, dataContext);
  }

  public void invoke(final Project project, PsiElement[] elements, DataContext dataContext) {
    invoke(project, elements, true);
  }

  public static void invoke(final Project project, PsiElement[] elements, boolean checkSuperMethods) {
    for (PsiElement element : elements) {
      if (!SafeDeleteProcessor.validElement(element)) {
        return;
      }
    }
    final PsiElement[] temptoDelete = DeleteUtil.filterElements(elements);
    Set<PsiElement> elementsSet = new HashSet<PsiElement>(Arrays.asList(temptoDelete));
    Set<PsiElement> fullElementsSet = new HashSet<PsiElement>();

    if (checkSuperMethods) {
      for (PsiElement element : temptoDelete) {
        if (element instanceof PsiMethod) {
          final PsiMethod[] methods =
            SuperMethodWarningUtil.checkSuperMethods((PsiMethod)element, RefactoringBundle.message("to.delete.with.usage.search"), elementsSet);
          if (methods.length == 0) return;
          fullElementsSet.addAll(Arrays.asList(methods));
        } else
        if (element instanceof PsiParameter && ((PsiParameter) element).getDeclarationScope() instanceof PsiMethod) {
          PsiMethod method = (PsiMethod) ((PsiParameter) element).getDeclarationScope();
          final Set<PsiParameter> parametersToDelete = new HashSet<PsiParameter>();
          parametersToDelete.add((PsiParameter) element);
          final int parameterIndex = method.getParameterList().getParameterIndex((PsiParameter) element);
          SuperMethodsSearch.search(method, null, true, false).forEach(new Processor<MethodSignatureBackedByPsiMethod>() {
            public boolean process(MethodSignatureBackedByPsiMethod signature) {
              parametersToDelete.add(signature.getMethod().getParameterList().getParameters()[parameterIndex]);
              return true;
            }
          });

          OverridingMethodsSearch.search(method).forEach(new Processor<PsiMethod>() {
            public boolean process(PsiMethod overrider) {
              parametersToDelete.add(overrider.getParameterList().getParameters()[parameterIndex]);
              return true;
            }
          });
          if (parametersToDelete.size() > 1) {
            String message = RefactoringBundle.message("0.is.a.part.of.method.hierarchy.do.you.want.to.delete.multiple.parameters", UsageViewUtil.getLongName(method));
            if (Messages.showYesNoDialog(project, message, REFACTORING_NAME,
                Messages.getQuestionIcon()) != DialogWrapper.OK_EXIT_CODE) return;
          }
          fullElementsSet.addAll(parametersToDelete);
        } else {
          fullElementsSet.add(element);
        }
      }
    } else {
      fullElementsSet.addAll(Arrays.asList(temptoDelete));
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, fullElementsSet)) return;

    final PsiElement[] elementsToDelete = fullElementsSet.toArray(new PsiElement[fullElementsSet.size()]);
    SafeDeleteDialog dialog = new SafeDeleteDialog(project, elementsToDelete, new SafeDeleteDialog.Callback() {
      public void run(final SafeDeleteDialog dialog) {
        SafeDeleteProcessor.createInstance(project, new Runnable() {
          public void run() {
            dialog.close(SafeDeleteDialog.CANCEL_EXIT_CODE);
          }
        }, elementsToDelete, dialog.isSearchInComments(), dialog.isSearchForTextOccurences(), true).run();
      }

    });

    dialog.show();
  }
}
