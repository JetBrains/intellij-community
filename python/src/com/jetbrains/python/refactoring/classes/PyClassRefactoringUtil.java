package com.jetbrains.python.refactoring.classes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyPsiUtils;

import java.util.*;

/**
 * @author Dennis.Ushakov
 */
public class PyClassRefactoringUtil {
  private static final Logger LOG = Logger.getInstance(PyClassRefactoringUtil.class.getName());

  private PyClassRefactoringUtil() {}

  public static void moveSuperclasses(PyClass clazz, Set<String> superClasses, PyClass superClass) {
    if (superClasses.size() == 0) return;
    final Project project = clazz.getProject();
    final List<PsiElement> toAdd = removeAndGetSuperClasses(clazz, superClasses);
    addSuperclasses(project, superClass, toAdd, superClasses);
  }

  public static void addSuperclasses(Project project, PyClass superClass, Collection<PsiElement> toAdd, Collection<String> superClasses) {
    if (superClasses.size() == 0) return;
    PsiElement[] elements = superClass.getSuperClassExpressions();
    if (elements.length > 0) {
      PsiElement parent = elements[elements.length - 1].getParent();
      for (PsiElement element : toAdd) {
        PyUtil.addListNode(parent, element, parent.getLastChild().getNode(), false, true);
      }
    } else {
      addSuperclasses(project, superClass, superClasses);
    }
  }

  public static List<PsiElement> removeAndGetSuperClasses(PyClass clazz, Set<String> superClasses) {
    if (superClasses.size() == 0) return Collections.emptyList();
    final List<PsiElement> toAdd = new ArrayList<PsiElement>();
    final PsiElement[] elements = clazz.getSuperClassExpressions();
    for (PsiElement element : elements) {
      if (superClasses.contains(element.getText())) {
        toAdd.add(element);
        PyUtil.removeListNode(element);
      }
    }
    return toAdd;
  }

  public static void addSuperclasses(Project project, PyClass superClass, Collection<String> superClasses) {
    if (superClasses.size() == 0) return;
    final StringBuilder builder = new StringBuilder("(");
    for (String element : superClasses) {
      if (builder.length() > 1) builder.append(",");
      builder.append(element);
    }
    builder.append(")");
    final PsiFile file = PsiFileFactory.getInstance(project).createFileFromText(superClass.getName() + "temp", PythonFileType.INSTANCE, builder.toString());
    final PsiElement expression = file.getFirstChild().getFirstChild();
    PsiElement colon = superClass.getFirstChild();
    while (colon != null && !colon.getText().equals(":")) {
      colon = colon.getNextSibling();
    }
    LOG.assertTrue(colon != null && expression != null);
    PyPsiUtils.addBeforeInParent(colon, expression);
  }

  public static void moveMethods(List<PyFunction> methods, PyClass superClass) {
    if (methods.size() == 0) return;
    final PsiElement[] elements = methods.toArray(new PsiElement[methods.size()]);
    PyPsiUtils.removeElements(elements);
    addMethods(superClass, elements);
  }

  public static void addMethods(PyClass superClass, PsiElement[] elements) {
    if (elements.length == 0) return;
    final Project project = superClass.getProject();
    final StringBuilder builder = new StringBuilder();
    for (PsiElement element : elements) {
      builder.append(element.getText()).append("\n");
    }
    final PsiFile file = PsiFileFactory.getInstance(project).createFileFromText(superClass.getName() + "temp", PythonFileType.INSTANCE, builder.toString());

    if (superClass.getMethods().length != 0) {
      PyPsiUtils.addBeforeInParent(superClass.getMethods()[0], file.getChildren());
    } else {
      PyPsiUtils.addToEnd(superClass, file.getChildren());
    }
  }
}
