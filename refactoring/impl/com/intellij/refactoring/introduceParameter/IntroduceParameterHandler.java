/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 06.05.2002
 * Time: 13:36:30
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.introduceParameter;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemPreferencePolicy;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.*;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.ui.NameSuggestionsGenerator;
import com.intellij.refactoring.ui.TypeSelectorManager;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


public class IntroduceParameterHandler extends IntroduceHandlerBase implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.introduceParameter.IntroduceParameterHandler");
  private static final String REFACTORING_NAME = RefactoringBundle.message("introduce.parameter.title");
  private Project myProject;

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    ElementToWorkOn elementToWorkOn = ElementToWorkOn.getElementToWorkOn(editor, file, REFACTORING_NAME, HelpID.INTRODUCE_PARAMETER, project);
    if(elementToWorkOn == null) return;

    final PsiExpression expr = elementToWorkOn.getExpression();
    final PsiLocalVariable localVar = elementToWorkOn.getLocalVariable();
    final boolean isInvokedOnDeclaration = elementToWorkOn.isInvokedOnDeclaration();

    if (invoke(editor, project, expr, localVar, isInvokedOnDeclaration)) {
      editor.getSelectionModel().removeSelection();
    }
  }

  protected boolean invokeImpl(Project project, PsiExpression tempExpr, Editor editor) {
    return invoke(editor, project, tempExpr, null, false);
  }

  protected boolean invokeImpl(Project project, PsiLocalVariable localVariable, Editor editor) {
    return invoke(editor, project, null, localVariable, true);
  }

  private boolean invoke(Editor editor, Project project, final PsiExpression expr,
                         PsiLocalVariable localVar, boolean invokedOnDeclaration) {
    LOG.assertTrue(!PsiDocumentManager.getInstance(project).hasUncommitedDocuments());
    PsiMethod method;
    if (expr != null) {
      method = Util.getContainingMethod(expr);
    }
    else {
      method = Util.getContainingMethod(localVar);
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("expression:" + expr);
    }

    myProject = project;
    if (expr == null && localVar == null) {
      String message =  RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("selected.block.should.represent.an.expression"));
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INTRODUCE_PARAMETER, myProject);
      return false;
    }


    if (method == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("is.not.supported.in.the.current.context", REFACTORING_NAME));
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INTRODUCE_PARAMETER, myProject);
      return false;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, method)) return false;

    final PsiType typeByExpression = !invokedOnDeclaration ? RefactoringUtil.getTypeByExpressionWithExpectedType(expr) : null;
    if (!invokedOnDeclaration && typeByExpression == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("type.of.the.selected.expression.cannot.be.determined"));
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INTRODUCE_PARAMETER, myProject);
      return false;
    }

    if (!invokedOnDeclaration && typeByExpression == PsiType.VOID) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("selected.expression.has.void.type"));
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INTRODUCE_PARAMETER, project);
      return false;
    }

    final List<PsiMethod> validEnclosingMethods = getEnclosingMethods(method);
    if (validEnclosingMethods.size() > 1 && !ApplicationManager.getApplication().isUnitTestMode()) {
      final EnclosingMethodSelectionDialog dialog = new EnclosingMethodSelectionDialog(project, validEnclosingMethods);
      dialog.show();
      if (!dialog.isOK()) return false;
      method = dialog.getSelectedMethod();
    }
    else if (validEnclosingMethods.size() == 1) {
      method = validEnclosingMethods.get(0);
    }

    final PsiMethod methodToSearchFor = SuperMethodWarningUtil.checkSuperMethod(method, RefactoringBundle.message("to.refactor"));
    if (methodToSearchFor == null) return false;
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, methodToSearchFor)) return false;

    TIntArrayList parametersToRemove = expr == null ? new TIntArrayList() : Util.findParametersToRemove(method, expr);

    PsiExpression[] occurences;
    if (expr != null) {
      occurences = CodeInsightUtil.findExpressionOccurrences(method, expr);
    }
    else { // local variable
      occurences = CodeInsightUtil.findReferenceExpressions(method, localVar);
    }
    if (editor != null) {
      RefactoringUtil.highlightAllOccurences(myProject, occurences, editor);
    }

    List<UsageInfo> localVars = new ArrayList<UsageInfo>();
    List<UsageInfo> classMemberRefs = new ArrayList<UsageInfo>();
    List<UsageInfo> params = new ArrayList<UsageInfo>();


    if (expr != null) {
      Util.analyzeExpression(expr, localVars, classMemberRefs, params);
    }

    if (expr instanceof PsiReferenceExpression) {
      PsiElement resolved = ((PsiReferenceExpression) expr).resolve();
      if (resolved instanceof PsiLocalVariable) {
        localVar = (PsiLocalVariable) resolved;
      }
    }


    if (ApplicationManager.getApplication().isUnitTestMode()) {
      @NonNls String parameterName = "anObject";
      boolean replaceAllOccurences = true;
      boolean isDeleteLocalVariable = true;
      new IntroduceParameterProcessor(myProject, method, methodToSearchFor, expr, expr, localVar, isDeleteLocalVariable, parameterName,
                                      replaceAllOccurences, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, null,
                                      parametersToRemove).run();
    }
    else {
      final String propName = localVar != null ? CodeStyleManager.getInstance(myProject).variableNameToPropertyName(localVar.getName(), VariableKind.LOCAL_VARIABLE) : null;
      final PsiType initializerType = IntroduceParameterProcessor.getInitializerType(null, expr, localVar);

      TypeSelectorManager typeSelectorManager = expr != null
                                                ? new TypeSelectorManagerImpl(project, initializerType, expr, occurences)
                                                : new TypeSelectorManagerImpl(project, initializerType, occurences);

      NameSuggestionsGenerator nameSuggestionsGenerator = new NameSuggestionsGenerator() {
        public SuggestedNameInfo getSuggestedNameInfo(PsiType type) {
          return CodeStyleManager.getInstance(myProject).suggestVariableName(VariableKind.PARAMETER, propName, expr, type);
        }

        public Pair<LookupItemPreferencePolicy, Set<LookupItem>> completeVariableName(String prefix, PsiType type) {
          LinkedHashSet<LookupItem> set = new LinkedHashSet<LookupItem>();
          LookupItemPreferencePolicy policy =
            CompletionUtil.completeVariableNameForRefactoring(myProject, set, prefix, type, VariableKind.PARAMETER);
          return new Pair<LookupItemPreferencePolicy, Set<LookupItem>>(policy, set);
        }
      };
      new IntroduceParameterDialog(myProject, classMemberRefs, occurences.length, localVar, expr, nameSuggestionsGenerator,
                                   typeSelectorManager, methodToSearchFor, method, parametersToRemove).show();
    }
    return true;
  }


  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    // Never called
    /* do nothing */
  }

  private static List<PsiMethod> getEnclosingMethods(PsiMethod nearest) {
    List<PsiMethod> enclosingMethods = new ArrayList<PsiMethod>();
    enclosingMethods.add(nearest);
    PsiMethod method = nearest;
    while(true) {
      method = PsiTreeUtil.getParentOfType(method, PsiMethod.class, true);
      if (method == null) break;
      enclosingMethods.add(method);
    }
    if (enclosingMethods.size() > 1) {
      List<PsiMethod> methodsNotImplementingLibraryInterfaces = new ArrayList<PsiMethod>();
      for(PsiMethod enclosing: enclosingMethods) {
        PsiMethod[] superMethods = enclosing.findDeepestSuperMethods();
        boolean libraryInterfaceMethod = false;
        for(PsiMethod superMethod: superMethods) {
          libraryInterfaceMethod |= isLibraryInterfaceMethod(superMethod);
        }
        if (!libraryInterfaceMethod) {
          methodsNotImplementingLibraryInterfaces.add(enclosing);
        }
      }
      if (methodsNotImplementingLibraryInterfaces.size() > 0) {
        return methodsNotImplementingLibraryInterfaces;
      }
    }
    return enclosingMethods;
  }

  private static boolean isLibraryInterfaceMethod(final PsiMethod method) {
    return method.getModifierList().hasModifierProperty(PsiModifier.ABSTRACT) && !method.getManager().isInProject(method);
  }
}
