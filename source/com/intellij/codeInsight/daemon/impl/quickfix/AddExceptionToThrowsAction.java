package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.*;

/**
 * @author mike
 */
public class AddExceptionToThrowsAction extends BaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.AddExceptionToThrowsAction");
  private final PsiElement myWrongElement;

  public AddExceptionToThrowsAction(PsiElement wrongElement) {
    myWrongElement = wrongElement;
  }

  public boolean startInWriteAction() {
    return false;
  }

  public void invoke(final Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final PsiMethod targetMethod = PsiTreeUtil.getParentOfType(myWrongElement, PsiMethod.class);
    PsiElement element = findElement(myWrongElement, targetMethod);
    LOG.assertTrue(element != null);

    final PsiClassType[] unhandledExceptions = filterInProjectExceptions(ExceptionUtil.getUnhandledExceptions(element), targetMethod);

    boolean processSuperMethods = false;
    final PsiMethod[] superMethods = getSuperMethods(targetMethod);

    boolean hasSuperMethodsWithoutExceptions = hasSuperMethodsWithoutExceptions(superMethods, unhandledExceptions);

    if (hasSuperMethodsWithoutExceptions && superMethods.length > 0) {
      int result = Messages.showYesNoCancelDialog("Method '" + targetMethod.getName() + "' is inherited.\n" +
         "Do you want to add exceptions to method signatures in the whole method hierarchy?", "Method Is Inherited", Messages.getQuestionIcon());

      if (result == 0) processSuperMethods = true;
      else if (result == 1) processSuperMethods = false;
      else return;
    }

    final boolean processSuperMethods1 = processSuperMethods;
    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          if (!CodeInsightUtil.prepareFileForWrite(targetMethod.getContainingFile())) return;
          if (processSuperMethods1) {
            for (int i = 0; i < superMethods.length; i++) {
              PsiMethod superMethod = superMethods[i];
              if (!CodeInsightUtil.prepareFileForWrite(superMethod.getContainingFile())) return;
            }
          }

          try {
            processMethod(targetMethod, unhandledExceptions, project);

            if (processSuperMethods1) {
              for (int i = 0; i < superMethods.length; i++) {
                PsiMethod superMethod = superMethods[i];
                processMethod(superMethod, unhandledExceptions, project);
              }
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    );
  }

  private PsiMethod[] getSuperMethods(PsiMethod targetMethod) {
    List<PsiMethod> result = new ArrayList<PsiMethod>();
    _collectSuperMethods(targetMethod, result);
    return result.toArray(new PsiMethod[result.size()]);
  }

  private void _collectSuperMethods(PsiMethod method, List<PsiMethod> result) {
    PsiMethod[] superMethods = PsiSuperMethodUtil.findSuperMethods(method);
    for (int i = 0; i < superMethods.length; i++) {
      PsiMethod superMethod = superMethods[i];
      result.add(superMethod);
      _collectSuperMethods(superMethod, result);
    }
  }

  private static boolean hasSuperMethodsWithoutExceptions(final PsiMethod[] superMethods, PsiClassType[] unhandledExceptions) {
    for (int i = 0; i < superMethods.length; i++) {
      PsiMethod superMethod = superMethods[i];
      final PsiClassType[] referencedTypes = superMethod.getThrowsList().getReferencedTypes();

      Set<PsiClassType> exceptions = new HashSet<PsiClassType>(Arrays.asList(unhandledExceptions));
      for (int j = 0; j < referencedTypes.length; j++) {
        PsiClassType referencedType = referencedTypes[j];
        for (int k = 0; k < unhandledExceptions.length; k++) {
          PsiClassType exception = unhandledExceptions[k];
          if (referencedType.isAssignableFrom(exception)) exceptions.remove(exception);
        }
      }

      if (!exceptions.isEmpty()) return true;
    }

    return false;
  }

  private static void processMethod(PsiMethod targetMethod, final PsiClassType[] unhandledExceptions, Project project) throws IncorrectOperationException {
    for (int i = 0; i < unhandledExceptions.length; i++) {
      PsiClassType unhandledException = unhandledExceptions[i];
      PsiClass exceptionClass = unhandledException.resolve();
      if (exceptionClass != null) {
        PsiUtil.addException(targetMethod, exceptionClass);
      }
    }

    CodeStyleManager.getInstance(project).reformat(targetMethod.getThrowsList());
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PsiJavaFile)) return false;
    if (myWrongElement == null || !myWrongElement.isValid()) return false;

    PsiMethod method = PsiTreeUtil.getParentOfType(myWrongElement, PsiMethod.class);
    if (method == null || !method.getThrowsList().isPhysical()) return false;
    PsiElement element = findElement(myWrongElement, method);
    if (element == null) return false;

    setText("Add Exception(s) to Method Signature");
    return true;
  }

  public String getFamilyName() {
    return "Add Exception to Method Signature";
  }

  private PsiElement findElement(PsiElement element, PsiMethod topElement) {
    if (element == null) return null;
    PsiClassType[] unhandledExceptions = ExceptionUtil.getUnhandledExceptions(element);
    unhandledExceptions = filterInProjectExceptions(unhandledExceptions, topElement);
    if (unhandledExceptions.length > 0) {
      return element;
    }
    return findElement(element.getParent(), topElement);
  }

  private PsiClassType[] filterInProjectExceptions(PsiClassType[] unhandledExceptions, PsiMethod targetMethod) {
    if (targetMethod == null) return PsiClassType.EMPTY_ARRAY;

    Set<PsiClassType> result = new HashSet<PsiClassType>();

    if (!targetMethod.getManager().isInProject(targetMethod)) {
      final PsiClassType[] referencedTypes = targetMethod.getThrowsList().getReferencedTypes();
      for (int i = 0; i < referencedTypes.length; i++) {
        PsiClassType referencedType = referencedTypes[i];
        PsiClass psiClass = referencedType.resolve();
        if (psiClass == null) continue;
        for (int j = 0; j < unhandledExceptions.length; j++) {
          PsiClassType exception = unhandledExceptions[j];
          if (referencedType.isAssignableFrom(exception)) result.add(exception);
        }
      }
    }
    else {
      final PsiMethod[] superMethods = PsiSuperMethodUtil.findSuperMethods(targetMethod);
      for (int i = 0; i < superMethods.length; i++) {
        PsiMethod superMethod = superMethods[i];
        PsiClassType[] classTypes = filterInProjectExceptions(unhandledExceptions, superMethod);
        result.addAll(Arrays.asList(classTypes));
      }

      if (superMethods.length == 0) {
        result.addAll(Arrays.asList(unhandledExceptions));
      }
    }

    return result.toArray(new PsiClassType[result.size()]);
  }
}
