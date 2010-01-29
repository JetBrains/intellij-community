package com.jetbrains.python.refactoring.classes;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.Nullable;

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
    boolean hasChanges = false;
    for (String element : superClasses) {
      if (builder.length() > 1) builder.append(",");
      if (!alreadyHasSuperClass(superClass, element)) {
        builder.append(element);
        hasChanges = true;
      }
    }
    builder.append(")");
    if (!hasChanges) return;
    
    final PsiFile file = PsiFileFactory.getInstance(project).createFileFromText(superClass.getName() + "temp", PythonFileType.INSTANCE, builder.toString());
    final PsiElement expression = file.getFirstChild().getFirstChild();
    PsiElement colon = superClass.getFirstChild();
    while (colon != null && !colon.getText().equals(":")) {
      colon = colon.getNextSibling();
    }
    LOG.assertTrue(colon != null && expression != null);
    PyPsiUtils.addBeforeInParent(colon, expression);
  }

  private static boolean alreadyHasSuperClass(PyClass superClass, String className) {
    for (PyClass aClass : superClass.getSuperClasses()) {
      if (Comparing.strEqual(aClass.getName(), className)) {
        return true;
      }
    }
    return false;
  }

  public static void moveMethods(List<PyFunction> methods, PyClass superClass) {
    if (methods.size() == 0) return;
    final PyElement[] elements = methods.toArray(new PyElement[methods.size()]);
    addMethods(superClass, elements, true);
    PyPsiUtils.removeElements(elements);
  }

  public static void addMethods(final PyClass superClass, final PyElement[] elements, final boolean up) {
    if (elements.length == 0) return;
    final Project project = superClass.getProject();
    final String text = prepareClassText(superClass, elements, up, false, null);

    if (text == null) return;

    final PyClass newClass = PythonLanguage.getInstance().getElementGenerator().createFromText(project, PyClass.class, text);
    if (superClass.getMethods().length != 0) {
      final PyFunction previousLastMethod = superClass.getMethods()[0];
      final ASTNode node = newClass.getLastChild().getNode();
      for (ASTNode child : node.getChildren(null)) {
        PyPsiUtils.addBeforeInParent(previousLastMethod, child.getPsi());
      }
      PyPsiUtils.addBeforeInParent(previousLastMethod, newClass.getLastChild().getPrevSibling());
      PyPsiUtils.addBeforeInParent(previousLastMethod, newClass.getLastChild());      
    } else {
      PyPsiUtils.addToEnd(superClass, newClass.getLastChild());
    }
  }

  @Nullable
  public static String prepareClassText(PyClass superClass, PyElement[] elements, boolean up, boolean ignoreNoChanges, final String preparedClassName) {
    PsiElement sibling = elements[0].getPrevSibling();
    sibling = sibling == null ? elements[0].getParent().getPrevSibling() : sibling;
    final String white = sibling.getText();
    final StringBuilder builder = new StringBuilder("class ");
    if (preparedClassName != null) {
      builder.append(preparedClassName).append(":");
    } else {
      builder.append("Foo").append(":\n");
    }
    boolean hasChanges = false;
    for (PyElement element : elements) {
      final String name = element.getName();
      if (name != null && (up || superClass.findMethodByName(name, false) == null)) {
        builder.append(white).append(element.getText()).append("\n");
        hasChanges = true;
      }
    }
    return ignoreNoChanges || hasChanges ? builder.toString() : null;
  }
}
