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

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.NotNullPredicate;
import com.jetbrains.python.PyNames;
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

  private static final InitFirst INIT_FIRST = new InitFirst();

  private PyClassRefactoringUtil() {
  }


  /**
   * TODO: Doc
   * Copies class field declarations to some other place
   *
   * @param assignmentStatement list of class fields
   */
  @NotNull
  public static List<PyAssignmentStatement> copyFieldDeclarationToStatement(@NotNull final Collection<PyAssignmentStatement> assignmentStatement,
                                                                            @NotNull final PyStatementList superClassStatement) {
    List<PyAssignmentStatement> declations = new ArrayList<PyAssignmentStatement>(assignmentStatement.size());
    for (final PyAssignmentStatement expression : assignmentStatement) {
      PyAssignmentStatement newDeclaration = (PyAssignmentStatement)expression.copy();
      declations.add(newDeclaration);
      declations.add((PyAssignmentStatement)PyUtil.addElementToStatementList(newDeclaration, superClassStatement, true));
      PyPsiUtils.removeRedundantPass(superClassStatement);
    }
    return declations;
  }

  public static List<PyFunction> copyMethods(Collection<PyFunction> methods, PyClass superClass) {
    if (methods.size() == 0) return null;
    for (PsiElement e : methods) {
      rememberNamedReferences(e);
    }
    final PyFunction[] elements = methods.toArray(new PyFunction[methods.size()]);
    return addMethods(superClass, elements);
  }

  //TODO: Doct
  @NotNull
  public static List<PyFunction> addMethods(@NotNull PyClass destination, @NotNull PyFunction... methods) {
    List<PyFunction> methodsToAdd = new ArrayList<PyFunction>(Arrays.asList(methods));
    Collections.sort(methodsToAdd, INIT_FIRST);

    PyStatementList destStatementList = destination.getStatementList();
    List<PyFunction> newlyCreatedMethods = new ArrayList<PyFunction>(methods.length);

    for (PyFunction method : methodsToAdd) {

      if (destination.findMethodByName(method.getName(), false) != null) {
        continue; //TODO: Doc why
      }


      PyFunction newMethod = insertMethodInProperPlace(destStatementList, method);
      newlyCreatedMethods.add(newMethod);
      restoreNamedReferences(newMethod);
    }

    PyPsiUtils.removeRedundantPass(destStatementList);
    return newlyCreatedMethods;
  }

  //TODO: Doc algo
  @NotNull
  private static PyFunction insertMethodInProperPlace(
    @NotNull PyStatementList destStatementList,
    @NotNull PyFunction method) {
    boolean methodIsInit = PyUtil.isInit(method);
    if (!methodIsInit) {
      //Not init method could be inserted in the bottom
      return (PyFunction)destStatementList.add(method);
    }

    //We should find appropriate place for init
    for (PsiElement element : destStatementList.getChildren()) {
      boolean elementComment = element instanceof PyExpressionStatement;
      boolean elementClassField = element instanceof PyAssignmentStatement;

      if ((!(elementComment || elementClassField))) {
        return (PyFunction)destStatementList.addBefore(method, element);
      }
    }
    return (PyFunction)destStatementList.add(method);
  }


  public static <T extends PyElement & PyStatementListContainer> void insertPassIfNeeded(@NotNull T element) {
    final PyStatementList statements = element.getStatementList();
    if (statements.getStatements().length == 0) {
      statements.add(
        PyElementGenerator.getInstance(element.getProject())
          .createFromText(LanguageLevel.getDefault(), PyPassStatement.class, PyNames.PASS)
      );
    }
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
   *
   * @param methodName     name if new method (be sure to check {@link com.jetbrains.python.PyNames} for special methods)
   * @param pyClass        class to add method
   * @param modifier       if method static or class or simple instance method (null)>
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

    final PyFunction function = builder.buildFunction(pyClass.getProject(), LanguageLevel.getDefault());
    return addMethods(pyClass, function).get(0);
  }

  //TODO: Doc
  @NotNull
  public static void addSuperclasses(@NotNull final Project project,
                                     @NotNull final PyClass clazz,
                                     @NotNull final PyClass... superClasses) {

    final List<String> superClassNames = new ArrayList<String>();


    for (final PyClass superClass : Collections2.filter(Arrays.asList(superClasses), NotNullPredicate.INSTANCE)) {
      if (superClass.getName() != null) {
        superClassNames.add(superClass.getName());
        PyClassRefactoringUtil.insertImport(clazz, superClass);
      }
    }

    PyArgumentList superClassExpressionList = clazz.getSuperClassExpressionList();
    PyElementGenerator generator = PyElementGenerator.getInstance(project);

    if (superClassExpressionList != null) {
      for (String superClassName : superClassNames) {
        superClassExpressionList.addArgument(generator.createExpressionFromText(superClassName));
      }
    }
    //TODO: Doc why we do it manually
    else {
      String superClassText = String.format("(%s)", StringUtil.join(superClassNames, ","));
      clazz.addAfter(generator.createExpressionFromText(superClassText),
                     clazz.getNameNode().getPsi());
    }
  }

  private static class NameExtractor implements Function<PyElement, String> {
    @SuppressWarnings("NullableProblems") //We sure collection has no null
    @Nullable
    @Override
    public String apply(@NotNull final PyElement input) {
      return input.getName();
    }
  }

  private static class InitFirst implements Comparator<PyFunction> {
    @Override
    public int compare(PyFunction o1, PyFunction o2) {
      return (PyUtil.isInit(o1) ? 1 : 0) - (PyUtil.isInit(o2) ? 1 : 0);
    }
  }
}
