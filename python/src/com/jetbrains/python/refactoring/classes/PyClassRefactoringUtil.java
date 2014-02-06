/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.refactoring.classes;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.psi.impl.PyImportedModule;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.psi.PyFunction.Modifier.CLASSMETHOD;
import static com.jetbrains.python.psi.PyFunction.Modifier.STATICMETHOD;

/**
 * @author Dennis.Ushakov
 */
public class PyClassRefactoringUtil {
  private static final Logger LOG = Logger.getInstance(PyClassRefactoringUtil.class.getName());
  private static final Key<PsiNamedElement> ENCODED_IMPORT = Key.create("PyEncodedImport");
  private static final Key<Boolean> ENCODED_USE_FROM_IMPORT = Key.create("PyEncodedUseFromImport");
  private static final Key<String> ENCODED_IMPORT_AS = Key.create("PyEncodedImportAs");

  private PyClassRefactoringUtil() {
  }

  public static void moveSuperclasses(PyClass clazz, Set<String> superClasses, PyClass superClass) {
    if (superClasses.size() == 0) return;
    final Project project = clazz.getProject();
    final List<PyExpression> toAdd = removeAndGetSuperClasses(clazz, superClasses);
    addSuperclasses(project, superClass, toAdd, superClasses);
  }

  public static void addSuperclasses(Project project,
                                     PyClass superClass,
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
          argList.addArgument(PyElementGenerator.getInstance(project).createExpressionFromText(s));
        }
      }
    }
    else {
      addSuperclasses(project, superClass, superClassesAsStrings);
    }
  }

  /**
   * Removes super classes by name and returns list of removed
   *
   * @param clazz                class to find super classes to remove
   * @param superClassesToRemove list of super class names
   * @return list of removed classes
   */
  @NotNull
  public static List<PyExpression> removeAndGetSuperClasses(@NotNull PyClass clazz, @NotNull Set<String> superClassesToRemove) {
    if (superClassesToRemove.isEmpty()) {
      return Collections.emptyList();
    }
    final List<PyExpression> result = new ArrayList<PyExpression>();
    for (final PyExpression superClassExpression : clazz.getSuperClassExpressions()) {
      if (superClassesToRemove.contains(superClassExpression.getText())) {
        result.add(superClassExpression);
        superClassExpression.delete();
      }
    }
    return result;
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

    final PsiFile file =
      PsiFileFactory.getInstance(project).createFileFromText(superClass.getName() + "temp", PythonFileType.INSTANCE, builder.toString());
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

  /**
   * Moves class field declarations to some other place
   * @param expressions list of class fields
   * @param superClass where to move them
   */
  public static void moveFieldDeclarationToStatement(@NotNull final Collection<PyTargetExpression> expressions,
                                                     @NotNull final PyStatementList superClassStatement) {
    for (final PyTargetExpression expression : expressions) {
      final PyAssignmentStatement expAssignmentStatement = PsiTreeUtil.getParentOfType(expression, PyAssignmentStatement.class);
      assert expAssignmentStatement != null: "Target expression has no assignment statement";
      PyUtil.addElementToStatementList(expAssignmentStatement.copy(), superClassStatement, true);
      expAssignmentStatement.delete();
      PyPsiUtils.removeRedundantPass(superClassStatement);
    }

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
        PyPsiUtils.removeElements(PsiUtilCore.toPsiElementArray(comments));
      }
    }
    PyPsiUtils.removeElements(elements);
  }

  public static <T extends PyElement & PyStatementListContainer>void insertPassIfNeeded(@NotNull T element) {
    final PyStatementList statements = element.getStatementList();
    if (statements.getStatements().length == 0) {
      statements.add(
        PyElementGenerator.getInstance(element.getProject()).createFromText(LanguageLevel.getDefault(), PyPassStatement.class, PyNames.PASS));
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
          if (ref.isReferenceTo(oldElement)) {
            ref.bindToElement(newElement);
          }
        }
      }
    });
  }

  private static void restoreReference(final PyReferenceExpression node) {
    PsiNamedElement target = node.getCopyableUserData(ENCODED_IMPORT);
    final String asName = node.getCopyableUserData(ENCODED_IMPORT_AS);
    final Boolean useFromImport = node.getCopyableUserData(ENCODED_USE_FROM_IMPORT);
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
    if (PsiTreeUtil.isAncestor(node.getContainingFile(), target, false)) return;
    if (target instanceof PyFile) {
      insertImport(node, target, asName, useFromImport != null ? useFromImport : true);
    }
    else {
      insertImport(node, target, asName, true);
    }
    node.putCopyableUserData(ENCODED_IMPORT, null);
    node.putCopyableUserData(ENCODED_IMPORT_AS, null);
    node.putCopyableUserData(ENCODED_USE_FROM_IMPORT, null);
  }

  public static void insertImport(PsiElement anchor, Collection<PsiNamedElement> elements) {
    for (PsiNamedElement newClass : elements) {
      insertImport(anchor, newClass);
    }
  }

  public static boolean isValidQualifiedName(QualifiedName name) {
    if (name == null) {
      return false;
    }
    final Collection<String> components = name.getComponents();
    if (components.isEmpty()) {
      return false;
    }
    for (String s : components) {
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
    final QualifiedName qname = QualifiedNameFinder.findCanonicalImportPath(element, anchor);
    if (qname == null || !isValidQualifiedName(qname)) {
      return false;
    }
    final QualifiedName containingQName;
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

  public static void rememberNamedReferences(@NotNull final PsiElement element) {
    element.acceptChildren(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyReferenceExpression(PyReferenceExpression node) {
        super.visitPyReferenceExpression(node);
        if (PsiTreeUtil.getParentOfType(node, PyImportStatementBase.class) != null) {
          return;
        }
        final NameDefiner importElement = getImportElement(node);
        if (importElement != null && PsiTreeUtil.isAncestor(element, importElement, false)) {
          return;
        }
        rememberReference(node, element);
      }
    });
  }

  private static void rememberReference(@NotNull PyReferenceExpression node, @NotNull PsiElement element) {
    // We will remember reference in deepest node (except for references to PyImportedModules, as we need references to modules, not to
    // their packages)
    final PyExpression qualifier = node.getQualifier();
    if (qualifier != null && !(resolveExpression(qualifier) instanceof PyImportedModule)) {
      return;
    }
    final PsiElement target = resolveExpression(node);
    if (target instanceof PsiNamedElement && !PsiTreeUtil.isAncestor(element, target, false)) {
      final NameDefiner importElement = getImportElement(node);
      if (!PyUtil.inSameFile(element, target) && importElement == null && !(target instanceof PsiFileSystemItem)) {
        return;
      }
      node.putCopyableUserData(ENCODED_IMPORT, (PsiNamedElement)target);
      if (importElement instanceof PyImportElement) {
        node.putCopyableUserData(ENCODED_IMPORT_AS, ((PyImportElement)importElement).getAsName());
      }
      node.putCopyableUserData(ENCODED_USE_FROM_IMPORT, qualifier == null);
    }
  }

  @Nullable
  private static NameDefiner getImportElement(PyReferenceExpression expr) {
    for (ResolveResult result : expr.getReference().multiResolve(false)) {
      final PsiElement e = result.getElement();
      if (e instanceof PyImportElement) {
        return (PyImportElement)e;
      }
      if (e instanceof PyStarImportElement) {
        return (PyStarImportElement)e;
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

  public static void updateImportOfElement(@NotNull PyImportStatementBase importStatement, @NotNull PsiNamedElement element) {
    final String name = getOriginalName(element);
    if (name != null) {
      PyImportElement importElement = null;
      for (PyImportElement e : importStatement.getImportElements()) {
        if (name.equals(getOriginalName(e))) {
          importElement = e;
        }
      }
      if (importElement != null) {
        final PsiFile file = importStatement.getContainingFile();
        final PsiFile newFile = element.getContainingFile();
        boolean deleteImportElement = false;
        if (newFile == file) {
          deleteImportElement = true;
        }
        else if (insertImport(importStatement, element, importElement.getAsName(), true)) {
          deleteImportElement = true;
        }
        if (deleteImportElement) {
          if (importStatement.getImportElements().length == 1) {
            final boolean isInjected =
              InjectedLanguageManager.getInstance(importElement.getProject()).isInjectedFragment(importElement.getContainingFile());
            if (!isInjected) {
              importStatement.delete();
            }
            else {
              deleteImportStatementFromInjected(importStatement);
            }
          }
          else {
            importElement.delete();
          }
        }
      }
    }
  }

  private static void deleteImportStatementFromInjected(@NotNull final PyImportStatementBase importStatement) {
    final PsiElement sibling = importStatement.getPrevSibling();
    importStatement.delete();
    if (sibling instanceof PsiWhiteSpace) sibling.delete();
  }

  @Nullable
  public static String getOriginalName(@NotNull PsiNamedElement element) {
    if (element instanceof PyFile) {
      VirtualFile virtualFile = PsiUtilBase.asVirtualFile(PyUtil.turnInitIntoDir(element));
      if (virtualFile != null) {
        return virtualFile.getNameWithoutExtension();
      }
      return null;
    }
    return element.getName();
  }

  @Nullable
  private static String getOriginalName(PyImportElement element) {
    final QualifiedName qname = element.getImportedQName();
    if (qname != null && qname.getComponentCount() > 0) {
      return qname.getComponents().get(0);
    }
    return null;
  }

  /**
   * Creates class method
   * @param methodName name if new method (be sure to check {@link com.jetbrains.python.PyNames} for special methods)
   * @param pyClass class to add method
   * @param modifier if method static or class or simple instance method (null)>
   * @param parameterNames method parameters
   * @return newly created method
   */
  @NotNull
  public static PyFunction createMethod(@NotNull final String methodName,
                                        @NotNull final PyClass pyClass,
                                        @Nullable final PyFunction.Modifier modifier,
                                        @NotNull final String... parameterNames) {
    final PyFunctionBuilder builder = new PyFunctionBuilder(methodName);


    //TODO: Take names from codestyle?
    if (modifier == null) {
      builder.parameter(PyNames.CANONICAL_SELF);
    }
    else if (modifier == CLASSMETHOD) {
      builder.parameter(PyNames.CANONICAL_CLS);
      builder.decorate(PyNames.CLASSMETHOD);
    }
    else if (modifier == STATICMETHOD) {
      builder.decorate(PyNames.STATICMETHOD);
    }

    for (final String parameterName : parameterNames) {
      builder.parameter(parameterName);
    }

    final PyFunction function = builder.addFunction(pyClass.getStatementList(), LanguageLevel.getDefault());
    addMethods(pyClass, new PyElement[]{function}, true);
    return function;
  }
}
