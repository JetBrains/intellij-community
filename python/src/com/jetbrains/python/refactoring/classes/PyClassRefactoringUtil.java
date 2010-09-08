package com.jetbrains.python.refactoring.classes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.util.containers.*;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.actions.AddImportHelper;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.HashSet;

/**
 * @author Dennis.Ushakov
 */
public class PyClassRefactoringUtil {
  private static final Logger LOG = Logger.getInstance(PyClassRefactoringUtil.class.getName());

  private PyClassRefactoringUtil() {}

  public static void moveSuperclasses(PyClass clazz, Set<String> superClasses, PyClass superClass) {
    if (superClasses.size() == 0) return;
    final Project project = clazz.getProject();
    final List<PyExpression> toAdd = removeAndGetSuperClasses(clazz, superClasses);
    addSuperclasses(project, superClass, toAdd, superClasses);
  }

  public static void addSuperclasses(Project project, PyClass superClass,
                                     @Nullable Collection<PyExpression> superClassesAsPsi,
                                     Collection<String> superClassesAsStrings) {
    if (superClassesAsStrings.size() == 0) return;
    PyArgumentList argList = superClass.getSuperClassExpressionList();
    if (argList != null) {
      if (superClassesAsPsi != null) {
        for (PyExpression element : superClassesAsPsi) {
          argList.addArgument(element);
        }
      }
      else {
        for (String s : superClassesAsStrings) {
          final PyExpression expr = PyElementGenerator.getInstance(project).createExpressionFromText(s);
          argList.addArgument(expr);
        }
      }
    } else {
      addSuperclasses(project, superClass, superClassesAsStrings);
    }
  }

  public static List<PyExpression> removeAndGetSuperClasses(PyClass clazz, Set<String> superClasses) {
    if (superClasses.size() == 0) return Collections.emptyList();
    final List<PyExpression> toAdd = new ArrayList<PyExpression>();
    final PyExpression[] elements = clazz.getSuperClassExpressions();
    for (PyExpression element : elements) {
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
    removeMethodsWithComments(elements);
  }

  private static void removeMethodsWithComments(PyElement[] elements) {
    for (PyElement element : elements) {
      final Set<PsiElement> comments = PyUtil.getComments(element);
      if (comments.size() > 0) {
        PyPsiUtils.removeElements(comments.toArray(new PsiElement[comments.size()]));
      }
    }
    PyPsiUtils.removeElements(elements);
  }

  public static void insertPassIfNeeded(PyClass clazz) {
    final PyStatementList statements = clazz.getStatementList();
    if (statements.getStatements().length == 0) {
      statements.add(PyElementGenerator.getInstance(clazz.getProject()).createFromText(LanguageLevel.getDefault(), PyPassStatement.class, "pass"));
    }
  }

  public static void addMethods(final PyClass superClass, final PyElement[] elements, final boolean up) {
    if (elements.length == 0) return;
    final Project project = superClass.getProject();
    final String text = prepareClassText(superClass, elements, up, false, null);

    if (text == null) return;

    final PyClass newClass = PyElementGenerator.getInstance(project).createFromText(LanguageLevel.getDefault(), PyClass.class, text);
    final PyStatementList statements = superClass.getStatementList();
    final PyStatementList newStatements = newClass.getStatementList();
    if (statements.getStatements().length != 0) {
      for (PyElement newStatement : newStatements.getStatements()) {
        if (newStatement instanceof PyExpressionStatement && newStatement.getFirstChild() instanceof PyStringLiteralExpression) continue;
        final PsiElement anchor = statements.add(newStatement);
        final Set<PsiElement> comments = PyUtil.getComments(newStatement);
        for (PsiElement comment : comments) {
          statements.addBefore(comment, anchor);
        }
      }
    } else {
      statements.replace(newStatements);
    }
  }

  @Nullable
  public static String prepareClassText(PyClass superClass, PyElement[] elements, boolean up, boolean ignoreNoChanges, final String preparedClassName) {
    PsiElement sibling = superClass.getPrevSibling();
    final String white = sibling != null ? "\n" + sibling.getText() + "    ": "\n    ";
    final StringBuilder builder = new StringBuilder("class ");
    if (preparedClassName != null) {
      builder.append(preparedClassName).append(":");
    } else {
      builder.append("Foo").append(":");
    }
    boolean hasChanges = false;
    for (PyElement element : elements) {
      final String name = element.getName();
      if (name != null && (up || superClass.findMethodByName(name, false) == null)) {
        final Set<PsiElement> comments = PyUtil.getComments(element);
        for (PsiElement comment : comments) {
          builder.append(white).append(comment.getText());
        }
        builder.append(white).append(element.getText()).append("\n");
        hasChanges = true;
      }
    }
    if (ignoreNoChanges && !hasChanges) {
      builder.append(white).append("pass");
    }
    return ignoreNoChanges || hasChanges ? builder.toString() : null;
  }

  public static void insertImport(PyClass target, PyClass newClass) {
    if (PyBuiltinCache.getInstance(newClass).hasInBuiltins(newClass)) {
      return;
    }
    final VirtualFile vFile = newClass.getContainingFile().getVirtualFile();
    assert vFile != null;
    final PsiFile file = target.getContainingFile();
    if (!PyCodeInsightSettings.getInstance().PREFER_FROM_IMPORT) {
      final String name = newClass.getQualifiedName();
      AddImportHelper.addImportStatement(file, name, null);
    } else {
      AddImportHelper.addImportFrom(file, ResolveImportUtil.findShortestImportableName(target, vFile), newClass.getName());
    }
  }

  public static Set<PyClass> rememberClassReferences(final List<PyFunction> methods) {
    final HashSet<PyClass> result = new HashSet<PyClass>();
    for (PyFunction method : methods) {
      method.acceptChildren(new PyRecursiveElementVisitor() {
        @Override
        public void visitPyReferenceExpression(PyReferenceExpression node) {
          super.visitPyReferenceExpression(node);
          final PsiPolyVariantReference ref = node.getReference();
          final PsiElement target = ref.resolve();
          if (target instanceof PyClass) {
            result.add((PyClass)target);
          }
        }
      });
    }
    return result;
  }

  public static void restoreImports(final PyClass target, final Set<PyClass> rememberedSet) {
    for (PyClass clazz : rememberedSet) {
      insertImport(target, clazz);
    }
  }
}
