package com.jetbrains.python.refactoring.classes.pullUp;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.refactoring.RefactoringBundle;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.refactoring.classes.PyMemberInfo;

import java.util.*;

/**
 * @author Dennis.Ushakov
 */
public class PyPullUpHelper {
  private static final Logger LOG = Logger.getInstance(PyPullUpHelper.class.getName());
  private PyPullUpHelper() {}

  public static PyElement pullUp(final PyClass clazz, final Collection<PyMemberInfo> selectedMemberInfos, final PyClass superClass) {
    final Set<String> superClasses = new HashSet<String>();
    final List<PyFunction> methods = new ArrayList<PyFunction>();
    for (PyMemberInfo member : selectedMemberInfos) {
      final PyElement element = member.getMember();
      if (element instanceof PyFunction) methods.add((PyFunction)element);
      else if (element instanceof PyClass) superClasses.add(element.getName());
      else LOG.error("unmatched member class " + element.getClass());
    }
    CommandProcessor.getInstance().executeCommand(clazz.getProject(), new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            // move methods
            moveElements(methods, clazz, superClass);

            // move superclasses declarations
            moveSuperclasses(clazz, superClasses, superClass);
          }
        });
      }
    }, RefactoringBundle.message("pull.members.up.title"), null);

    return superClass;
  }

  private static void moveSuperclasses(PyClass clazz, Set<String> superClasses, PyClass superClass) {
    if (superClasses.size() == 0) return;
    final Project project = clazz.getProject();
    final List<PsiElement> toAdd = new ArrayList<PsiElement>();
    PsiElement[] elements = clazz.getSuperClassExpressions();
    for (PsiElement element : elements) {
      if (superClasses.contains(element.getText())) {
        toAdd.add(element);
        PyUtil.removeListNode(element);
      }
    }
    elements = superClass.getSuperClassExpressions();

    if (elements.length > 0) {
      PsiElement parent = elements[elements.length - 1].getParent();
      for (PsiElement element : toAdd) {
        PyUtil.addListNode(parent, element, parent.getLastChild().getNode(), false, true);
      }
    } else {
      addSuperclasses(project, superClass, superClasses);
    }
  }

  private static void addSuperclasses(Project project, PyClass superClass, Collection<String> superClasses) {
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
    PyPsiUtils.addBeforeInParent(colon, expression);
  }

  private static void moveElements(List<PyFunction> methods, PyClass clazz, PyClass superClass) {
    if (methods.size() == 0) return;
    final Project project = clazz.getProject();
    final PsiElement[] elements = methods.toArray(new PsiElement[methods.size()]);
    PyPsiUtils.removeElements(elements);
    final StringBuilder builder = new StringBuilder();
    for (PsiElement element : elements) {
      builder.append(element.getText()).append("\n");
    }
    final PsiFile file = PsiFileFactory.getInstance(project).createFileFromText(clazz.getName() + "temp", PythonFileType.INSTANCE, builder.toString());

    if (superClass.getMethods().length != 0) {
      PyPsiUtils.addBeforeInParent(superClass.getMethods()[0], file.getChildren());
    } else {
      PyPsiUtils.addToEnd(superClass, file.getChildren());
    }
  }
}
