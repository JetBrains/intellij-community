package com.jetbrains.python.refactoring.rename;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.util.Processor;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.search.PyOverridingMethodsSearch;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class RenamePyFunctionProcessor extends RenamePyElementProcessor {
  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    return element instanceof PyFunction;
  }

  @Override
  public boolean forcesShowPreview() {
    return true;
  }

  @Override
  public boolean isToSearchInComments(PsiElement element) {
    return PyCodeInsightSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FUNCTION;
  }

  @Override
  public void setToSearchInComments(PsiElement element, boolean enabled) {
    PyCodeInsightSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FUNCTION = enabled;
  }

  @Override
  public boolean isToSearchForTextOccurrences(PsiElement element) {
    return PyCodeInsightSettings.getInstance().RENAME_SEARCH_NON_CODE_FOR_FUNCTION;
  }

  @Override
  public void setToSearchForTextOccurrences(PsiElement element, boolean enabled) {
    PyCodeInsightSettings.getInstance().RENAME_SEARCH_NON_CODE_FOR_FUNCTION = enabled;
  }

  @Override
  public PsiElement substituteElementToRename(PsiElement element, Editor editor) {
    PyFunction function = (PyFunction) element;
    final PyClass containingClass = function.getContainingClass();
    if (containingClass == null) {
      return function;
    }
    if (PyNames.INIT.equals(function.getName())) {
      return containingClass; 
    }
    final List<PsiElement> superMethods = new ArrayList<PsiElement>(PySuperMethodsSearch.search(function, true).findAll());
    if (superMethods.size() > 0) {
      // TODO this is not exactly right for multiple inheritance
      final PyFunction deepestSuperMethod = (PyFunction) superMethods.get(superMethods.size()-1);
      String message = "Method " + function.getName() + " of class " + containingClass.getQualifiedName() + "\noverrides method of class "
                       + deepestSuperMethod.getContainingClass().getQualifiedName() + ".\nDo you want to rename the base method?";
      int rc = Messages.showYesNoCancelDialog(element.getProject(), message, "Rename", Messages.getQuestionIcon());
      if (rc == 0) {
        return deepestSuperMethod;
      }
      if (rc == 1) {
        return function;
      }
      return null;
    }
    return function;
  }

  @Override
  public void prepareRenaming(PsiElement element, final String newName, final Map<PsiElement, String> allRenames) {
    PyFunction function = (PyFunction) element;
    PyOverridingMethodsSearch.search(function, true).forEach(new Processor<PyFunction>() {
      @Override
      public boolean process(PyFunction pyFunction) {
        allRenames.put(pyFunction, newName);
        return true;
      }
    });
  }
}
