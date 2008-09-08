/**
 * created at Nov 21, 2001
 * @author Jeka
 */
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.ide.DataManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.lang.refactoring.InlineHandlers;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.inlineSuperClass.InlineSuperClassRefactoringHandler;
import com.intellij.refactoring.lang.jsp.inlineInclude.InlineIncludeFileHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class JavaInlineHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inline.InlineHandler");
  private static final String REFACTORING_NAME = RefactoringBundle.message("inline.title");

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    LOG.assertTrue(elements.length == 1);
    if (dataContext == null) {
      dataContext = DataManager.getInstance().getDataContext();
    }
    final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    if (elements[0] instanceof PsiMethod) {
      InlineMethodHandler.invoke(project, editor, (PsiMethod) elements[0]);
    } else if (elements[0] instanceof  PsiField) {
      InlineConstantFieldHandler.invoke(project, editor, (PsiField) elements[0]);
    } else if (elements[0] instanceof PsiLocalVariable) {
      InlineLocalHandler.invoke(project, editor, (PsiLocalVariable)elements[0], null);
    }
    else if (elements [0] instanceof PsiClass) {
      final Collection<PsiClass> inheritors = ClassInheritorsSearch.search((PsiClass)elements[0]).findAll();
      if (inheritors.size() == 0) {
        InlineToAnonymousClassHandler.invoke(project, editor, (PsiClass) elements[0]);
      } else {
        InlineSuperClassRefactoringHandler.invoke(project, editor, (PsiClass)elements[0], inheritors);
      }
    }
    else {
      LOG.error("Unknown element type to inline:" + elements[0]);
    }
  }

  public void invoke(@NotNull final Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    if (element != null) {
      final List<InlineHandler> handlers = InlineHandlers.getInlineHandlers(element.getLanguage());
      for (InlineHandler handler : handlers) {
        if (GenericInlineHandler.invoke(element, editor, handler)) {
          return;
        }
      }
    }

    JspFile jspFile;

    if (element instanceof PsiLocalVariable) {
      final PsiReference psiReference = TargetElementUtilBase.findReference(editor);
      final PsiReferenceExpression refExpr = psiReference instanceof PsiReferenceExpression ? ((PsiReferenceExpression)psiReference) : null;
      InlineLocalHandler.invoke(project, editor, (PsiLocalVariable) element, refExpr);
    } else if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;
      if (method.isConstructor() && !InlineMethodHandler.isChainingConstructor(method)) {
        InlineToAnonymousClassHandler.invoke(project, editor, method.getContainingClass());
      }
      else {
        InlineMethodHandler.invoke(project, editor, method);
      }
    } else if (element instanceof PsiField) {
      InlineConstantFieldHandler.invoke(project, editor, (PsiField) element);
    }
    else if (element instanceof PsiClass) {
      final Collection<PsiClass> inheritors = ClassInheritorsSearch.search((PsiClass)element).findAll();
      if (inheritors.size() == 0) {
        InlineToAnonymousClassHandler.invoke(project, editor, (PsiClass) element);
      } else {
        InlineSuperClassRefactoringHandler.invoke(project, editor, (PsiClass)element, inheritors);
      }
    }
    else if (element instanceof PsiParameter && element.getParent() instanceof PsiParameterList) {
      InlineParameterHandler.invoke(project, editor, (PsiParameter) element);
    }
    else if (PsiUtil.isInJspFile(file) && (jspFile = PsiUtil.getJspFile(file)) != null) {
      InlineIncludeFileHandler.invoke(project, editor, jspFile);
    }
    else if (element != null && element.getLanguage() instanceof JavaLanguage){
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.method.or.local.name"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, null);
    }
  }

}
