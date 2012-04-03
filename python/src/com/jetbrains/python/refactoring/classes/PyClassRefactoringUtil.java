package com.jetbrains.python.refactoring.classes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.documentation.DocStringTypeReference;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.*;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dennis.Ushakov
 */
public class PyClassRefactoringUtil {
  private static final Logger LOG = Logger.getInstance(PyClassRefactoringUtil.class.getName());
  private static final Key<PsiNamedElement> ENCODED_IMPORT = Key.create("PyEncodedImport");
  private static final Key<String> ENCODED_IMPORT_AS = Key.create("PyEncodedImportAs");

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

  public static void moveMethods(Collection<PyFunction> methods, PyClass superClass) {
    if (methods.size() == 0) return;
    for (PsiElement e : methods) {
      rememberNamedReferences(e);
    }
    final PyElement[] elements = methods.toArray(new PyElement[methods.size()]);
    addMethods(superClass, elements, true);
    removeMethodsWithComments(elements);
  }

  private static void removeMethodsWithComments(PyElement[] elements) {
    for (PyElement element : elements) {
      final Set<PsiElement> comments = PyUtil.getComments(element);
      if (comments.size() > 0) {
        PyPsiUtils.removeElements(PsiUtilBase.toPsiElementArray(comments));
      }
    }
    PyPsiUtils.removeElements(elements);
  }

  public static void insertPassIfNeeded(PyClass clazz) {
    final PyStatementList statements = clazz.getStatementList();
    if (statements.getStatements().length == 0) {
      statements.add(PyElementGenerator.getInstance(clazz.getProject()).createFromText(LanguageLevel.getDefault(), PyPassStatement.class, PyNames.PASS));
    }
  }

  public static void addMethods(final PyClass superClass, final PyElement[] elements, final boolean up) {
    if (elements.length == 0) return;
    final PyStatementList statements = superClass.getStatementList();
    for (PyElement newStatement : elements) {
      if (up && newStatement instanceof PyFunction) {
        final String name = newStatement.getName();
        if (name != null && superClass.findMethodByName(name, false) != null) {
          continue;
        }
      }
      if (newStatement instanceof PyExpressionStatement && newStatement.getFirstChild() instanceof PyStringLiteralExpression) continue;
      final PsiElement anchor = statements.add(newStatement);
      restoreNamedReferences(anchor);
      final Set<PsiElement> comments = PyUtil.getComments(newStatement);
      for (PsiElement comment : comments) {
        statements.addBefore(comment, anchor);
      }
    }
    PyPsiUtils.removeRedundantPass(statements);
  }

  public static void restoreNamedReferences(@NotNull PsiElement element) {
    restoreNamedReferences(element, null);
  }

  public static void restoreNamedReferences(@NotNull final PsiElement newElement, @Nullable final PsiElement oldElement) {
    newElement.acceptChildren(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyReferenceExpression(PyReferenceExpression node) {
        super.visitPyReferenceExpression(node);
        restoreReference(node);
      }

      @Override
      public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
        super.visitPyStringLiteralExpression(node);
        for (PsiReference ref : node.getReferences()) {
          if (ref instanceof DocStringTypeReference && ref.isReferenceTo(oldElement)) {
            ref.bindToElement(newElement);
          }
        }
      }
    });
  }

  private static void restoreReference(final PyReferenceExpression node) {
    PsiNamedElement target = node.getCopyableUserData(ENCODED_IMPORT);
    String asName = node.getCopyableUserData(ENCODED_IMPORT_AS);
    if (target instanceof PsiDirectory) {
      target = (PsiNamedElement)PyUtil.turnDirIntoInit(target);
    }
    if (target instanceof PyFunction) {
      final PyFunction f = (PyFunction)target;
      final PyClass c = f.getContainingClass();
      if (c != null && c.findInitOrNew(false) == f) {
        target = c;
      }
    }
    if (target == null) return;
    if (PyBuiltinCache.getInstance(target).hasInBuiltins(target)) return;
    if (PsiTreeUtil.isAncestor(node.getContainingFile(), target, false)) return;
    if (target instanceof PyFile) {
      insertImport(node, target, asName, false);
    }
    else {
      insertImport(node, target, asName);
    }
    node.putCopyableUserData(ENCODED_IMPORT, null);
    node.putCopyableUserData(ENCODED_IMPORT_AS, null);
  }

  public static void insertImport(PsiElement anchor, Collection<PsiNamedElement> elements) {
    for (PsiNamedElement newClass : elements) {
      insertImport(anchor, newClass);
    }
  }

  public static boolean isValidQualifiedName(PyQualifiedName name) {
    if (name == null) {
      return false;
    }
    final Collection<String> components = name.getComponents();
    if (components.isEmpty()) {
      return false;
    }
    for (String s: components) {
      if (!PyNames.isIdentifier(s) || PyNames.isReserved(s)) {
        return false;
      }
    }
    return true;
  }

  public static boolean insertImport(PsiElement anchor, PsiNamedElement element) {
    return insertImport(anchor, element, null);
  }

  public static boolean insertImport(PsiElement anchor, PsiNamedElement element, @Nullable String asName) {
    return insertImport(anchor, element, asName, PyCodeInsightSettings.getInstance().PREFER_FROM_IMPORT);
  }

  public static boolean insertImport(PsiElement anchor, PsiNamedElement element, @Nullable String asName, boolean preferFromImport) {
    if (PyBuiltinCache.getInstance(element).hasInBuiltins(element)) return false;
    final PsiFile newFile = element.getContainingFile();
    final PsiFile file = anchor.getContainingFile();
    if (newFile == file) return false;
    final PyQualifiedName qname = ResolveImportUtil.findCanonicalImportPath(element, anchor);
    if (qname == null || !isValidQualifiedName(qname)) {
      return false;
    }
    final PyQualifiedName containingQName;
    final String importedName;
    if (element instanceof PyFile) {
      containingQName = qname.removeLastComponent();
      importedName = qname.getLastComponent();
    }
    else {
      containingQName = qname;
      importedName = getOriginalName(element);
    }
    final AddImportHelper.ImportPriority priority = AddImportHelper.getImportPriority(anchor, newFile);
    if (preferFromImport && !containingQName.getComponents().isEmpty()) {
      return AddImportHelper.addImportFrom(file, null, containingQName.toString(), importedName, asName, priority);
    }
    else {
      return AddImportHelper.addImportStatement(file, containingQName.append(importedName).toString(), asName, priority);
    }
  }

  public static void rememberNamedReferences(final PsiElement element) {
    element.acceptChildren(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyReferenceExpression(PyReferenceExpression node) {
        super.visitPyReferenceExpression(node);
        if (PsiTreeUtil.getParentOfType(node, PyImportStatementBase.class) != null) {
          return;
        }
        final PyImportElement importElement = getImportElement(node);
        if (importElement != null && PsiTreeUtil.isAncestor(element, importElement, false)) {
          return;
        }
        rememberReference(node, element);
      }
    });
  }

  private static void rememberReference(PyReferenceExpression node, PsiElement element) {
    // We will remember reference in deepest node (except for references to PyImportedModules, as we need references to modules, not to
    // their packages)
    final PyExpression qualifier = node.getQualifier();
    if (qualifier != null && !(resolveExpression(qualifier) instanceof PyImportedModule)) {
      return;
    }
    final PsiElement target = resolveExpression(node);
    if (target instanceof PsiNamedElement && !PsiTreeUtil.isAncestor(element, target, false)) {
      node.putCopyableUserData(ENCODED_IMPORT, (PsiNamedElement)target);
      final PyImportElement importElement = getImportElement(node);
      if (importElement != null) {
        node.putCopyableUserData(ENCODED_IMPORT_AS, importElement.getAsName());
      }
    }
  }

  @Nullable
  private static PyImportElement getImportElement(PyReferenceExpression expr) {
    for (ResolveResult result : expr.getReference().multiResolve(false)) {
      final PsiElement e = result.getElement();
      if (e instanceof PyImportElement) {
        return (PyImportElement)e;
      }
    }
    return null;
  }

  @Nullable
  private static PsiElement resolveExpression(@NotNull PyExpression expr) {
    if (expr instanceof PyReferenceExpression) {
      return ((PyReferenceExpression)expr).getReference().resolve();
    }
    return null;
  }

  public static void updateImportOfElement(PyImportStatementBase importStatement, PsiNamedElement element) {
    final String name = getOriginalName(element);
    if (name != null) {
      PyImportElement importElement = null;
      for (PyImportElement e: importStatement.getImportElements()) {
        if (name.equals(getOriginalName(e))) {
          importElement = e;
        }
      }
      if (importElement != null) {
        if (insertImport(importStatement, element, importElement.getAsName())) {
          if (importStatement.getImportElements().length == 1) {
            importStatement.delete();
          }
          else {
            importElement.delete();
          }
        }
      }
    }
  }

  @Nullable
  public static String getOriginalName(PsiNamedElement element) {
    if (element instanceof PyFile) {
      final PsiElement e = PyUtil.turnInitIntoDir(element);
      if (e instanceof PsiFileSystemItem) {
        final VirtualFile virtualFile = ((PsiFileSystemItem)e).getVirtualFile();
        if (virtualFile != null) {
          return virtualFile.getNameWithoutExtension();
        }
      }
      return null;
    }
    return element.getName();
  }

  @Nullable
  private static String getOriginalName(PyImportElement element) {
    final PyQualifiedName qname = element.getImportedQName();
    if (qname != null && qname.getComponentCount() > 0) {
      return qname.getComponents().get(0);
    }
    return null;
  }
}
