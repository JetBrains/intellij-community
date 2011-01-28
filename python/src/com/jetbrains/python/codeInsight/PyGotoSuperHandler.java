package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Alexey.Ivanov
 */
public class PyGotoSuperHandler implements CodeInsightActionHandler {

  public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    if (element == null) {
      return;
    }
    PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (function != null) {
      final Collection<PyFunction> superFunctions = getAllSuperMethodsByName(function);
      if (!superFunctions.isEmpty()) {
        PyFunction[] superFunctionsArray = superFunctions.toArray(new PyFunction[superFunctions.size()]);
        if (superFunctionsArray.length == 1) {
          superFunctionsArray[0].navigate(true);
        }
        else {
          NavigationUtil.getPsiElementPopup(superFunctionsArray, CodeInsightBundle.message("goto.super.method.chooser.title"))
            .showInBestPositionFor(editor);
        }
      }
    }
    else {
      PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class);
      if (pyClass != null) {
        List<PyClass> superClasses = PyUtil.getAllSuperClasses(pyClass);
        if (superClasses.size() != 0) {
          if (superClasses.size() == 1) {
            superClasses.get(0).navigate(true);
          }
          else {
            NavigationUtil.getPsiElementPopup(superClasses.toArray(new PyClass[superClasses.size()]), CodeInsightBundle.message("goto.super.class.chooser.title"))
              .showInBestPositionFor(editor);
          }
        }
      }
    }
  }

  private static Collection<PyFunction> getAllSuperMethodsByName(@NotNull final PyFunction method) {
    final PyClass pyClass = method.getContainingClass();
    final String name = method.getName();
    if (pyClass == null || name == null) {
      return Collections.emptyList();
    }
    final List<PyFunction> result = new ArrayList<PyFunction>();
    for (PyClass aClass: pyClass.iterateAncestorClasses()) {
      final PyFunction byName = aClass.findMethodByName(name, false);
      if (byName != null) {
        result.add(byName);
      }
    }
    return result;
  }

  public boolean startInWriteAction() {
    return false;
  }
}
