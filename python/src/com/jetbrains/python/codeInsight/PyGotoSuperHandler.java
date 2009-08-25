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
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 24, 2009
 * Time: 4:28:58 PM
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
        PyClass[] superClasses = PyUtil.getAllSuperClasses(pyClass);
        if (superClasses.length != 0) {
          if (superClasses.length == 1) {
            superClasses[0].navigate(true);
          }
          else {
            NavigationUtil.getPsiElementPopup(superClasses, CodeInsightBundle.message("goto.super.class.chooser.title"))
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
    final PyClass[] superClasses = PyUtil.getAllSuperClasses(pyClass);
    for (PyClass aClass: superClasses) {
      final PyFunction byName = aClass.findMethodByName(name);
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
