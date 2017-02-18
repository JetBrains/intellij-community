/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.google.common.collect.Collections2;
import com.intellij.lang.ASTNode;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.NotNullPredicate;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.codeInsight.imports.PyImportOptimizer;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyImportedModule;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dennis.Ushakov
 */
public final class PyClassRefactoringUtil {
  private static final Logger LOG = Logger.getInstance(PyClassRefactoringUtil.class.getName());
  private static final Key<PsiNamedElement> ENCODED_IMPORT = Key.create("PyEncodedImport");
  private static final Key<Boolean> ENCODED_USE_FROM_IMPORT = Key.create("PyEncodedUseFromImport");
  private static final Key<String> ENCODED_IMPORT_AS = Key.create("PyEncodedImportAs");


  private PyClassRefactoringUtil() {
  }


  /**
   * Copies class field declarations to some other place
   *
   * @param assignmentStatements list of class fields
   *                             @param dequalifyIfDeclaredInClass If not null method will check if field declared in this class.
   *                                                               If declared -- qualifier will be removed.
   *                                                               For example: MyClass.Foo will become Foo it this param is MyClass.
   * @return new (copied) fields
   */
  @NotNull
  public static List<PyAssignmentStatement> copyFieldDeclarationToStatement(@NotNull final Collection<PyAssignmentStatement> assignmentStatements,
                                                                            @NotNull final PyStatementList superClassStatement,
                                                                            @Nullable final PyClass dequalifyIfDeclaredInClass) {
    final List<PyAssignmentStatement> declarations = new ArrayList<>(assignmentStatements.size());
    Collections.sort(declarations, PyDependenciesComparator.INSTANCE);


    for (final PyAssignmentStatement pyAssignmentStatement : assignmentStatements) {
      final PyElement value = pyAssignmentStatement.getAssignedValue();
      final PyAssignmentStatement newDeclaration = (PyAssignmentStatement)pyAssignmentStatement.copy();

      if (value instanceof PyReferenceExpression && dequalifyIfDeclaredInClass != null) {
        final String newValue = getNewValueToAssign((PyReferenceExpression)value, dequalifyIfDeclaredInClass);

        setNewAssigneeValue(newDeclaration, newValue);

      }

      declarations.add(PyUtil.addElementToStatementList(newDeclaration, superClassStatement));
      PyPsiUtils.removeRedundantPass(superClassStatement);
    }
    return declarations;
  }

  /**
   * Sets new value to assignment statement.
   * @param assignmentStatement statement to change
   * @param newValue new value
   */
  private static void setNewAssigneeValue(@NotNull final PyAssignmentStatement assignmentStatement, @NotNull final String newValue) {
    final PyExpression oldValue = assignmentStatement.getAssignedValue();
    final PyExpression newExpression =
      PyElementGenerator.getInstance(assignmentStatement.getProject()).createExpressionFromText(LanguageLevel.forElement(assignmentStatement), newValue);
    if (oldValue != null) {
      oldValue.replace(newExpression);
    } else {
      assignmentStatement.add(newExpression);
    }
  }

  /**
   * Checks if current value declared in provided class and removes class qualifier if true
   * @param currentValue current value
   * @param dequalifyIfDeclaredInClass  class to check
   * @return value as string
   */
  @NotNull
  private static String getNewValueToAssign(@NotNull final PyReferenceExpression currentValue, @NotNull final PyClass dequalifyIfDeclaredInClass) {
    final PyExpression qualifier = currentValue.getQualifier();
    if ((qualifier instanceof PyReferenceExpression) &&
        ((PyReferenceExpression)qualifier).getReference().isReferenceTo(dequalifyIfDeclaredInClass)) {
      final String name = currentValue.getName();
      return ((name != null) ? name : currentValue.getText());
    }
    return currentValue.getText();
  }

  @NotNull
  public static List<PyFunction> copyMethods(Collection<PyFunction> methods, PyClass superClass, boolean skipIfExist ) {
    if (methods.isEmpty()) {
      return Collections.emptyList();
    }
    for (final PsiElement e : methods) {
      rememberNamedReferences(e);
    }
    final PyFunction[] elements = methods.toArray(new PyFunction[methods.size()]);
    return addMethods(superClass, skipIfExist, elements);
  }

  /**
   * Adds methods to class.
   *
   * @param destination where to add methods
   * @param methods     methods
   * @param skipIfExist do not add anything if method already exists
   * @return newly added methods or existing one (if skipIfExists is true and method already exists)
   */
  @NotNull
  public static List<PyFunction> addMethods(@NotNull final PyClass destination, final boolean skipIfExist, @NotNull final PyFunction... methods) {

    final PyStatementList destStatementList = destination.getStatementList();
    final List<PyFunction> result = new ArrayList<>(methods.length);

    for (final PyFunction method : methods) {

      final PyFunction existingMethod = destination.findMethodByName(method.getName(), false, null);
      if ((existingMethod != null) && skipIfExist) {
        result.add(existingMethod);
        continue; //We skip adding if class already has this method.
      }


      final PyFunction newMethod = insertMethodInProperPlace(destStatementList, method);
      result.add(newMethod);
      restoreNamedReferences(newMethod);
    }

    PyPsiUtils.removeRedundantPass(destStatementList);
    return result;
  }

  /**
   * Adds init methods before all other methods (but after class vars and docs).
   * Adds all other methods to the bottom
   *
   * @param destStatementList where to add methods
   * @param method            method to add
   * @return newlty added method
   */
  @NotNull
  private static PyFunction insertMethodInProperPlace(
    @NotNull final PyStatementList destStatementList,
    @NotNull final PyFunction method) {
    boolean methodIsInit = PyUtil.isInit(method);
    if (!methodIsInit) {
      //Not init method could be inserted in the bottom
      return (PyFunction)destStatementList.add(method);
    }

    //We should find appropriate place for init
    for (final PsiElement element : destStatementList.getChildren()) {
      final boolean elementComment = element instanceof PyExpressionStatement;
      final boolean elementClassField = element instanceof PyAssignmentStatement;

      if (!(elementComment || elementClassField)) {
        return (PyFunction)destStatementList.addBefore(method, element);
      }
    }
    return (PyFunction)destStatementList.add(method);
  }


  /**
   * Restores references saved by {@link #rememberNamedReferences(com.intellij.psi.PsiElement, String...)}.
   *
   * @param element newly created element to restore references
   * @see #rememberNamedReferences(com.intellij.psi.PsiElement, String...)
   */
  public static void restoreNamedReferences(@NotNull final PsiElement element) {
    restoreNamedReferences(element, null);
  }

  public static void restoreNamedReferences(@NotNull final PsiElement newElement, @Nullable final PsiElement oldElement) {
    restoreNamedReferences(newElement, oldElement, PsiElement.EMPTY_ARRAY);
  }

  public static void restoreNamedReferences(@NotNull final PsiElement newElement,
                                            @Nullable final PsiElement oldElement,
                                            @NotNull final PsiElement[] otherMovedElements) {
    newElement.acceptChildren(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyReferenceExpression(PyReferenceExpression node) {
        super.visitPyReferenceExpression(node);
        restoreReference(node, otherMovedElements);
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


  private static void restoreReference(@NotNull PyReferenceExpression node, @NotNull PsiElement[] otherMovedElements) {
    try {
      PsiNamedElement target = node.getCopyableUserData(ENCODED_IMPORT);
      final String asName = node.getCopyableUserData(ENCODED_IMPORT_AS);
      final Boolean useFromImport = node.getCopyableUserData(ENCODED_USE_FROM_IMPORT);
      if (target instanceof PsiDirectory) {
        target = (PsiNamedElement)PyUtil.getPackageElement((PsiDirectory)target, node);
      }
      if (target instanceof PyFunction) {
        final PyFunction f = (PyFunction)target;
        final PyClass c = f.getContainingClass();
        if (c != null && c.findInitOrNew(false, null) == f) {
          target = c;
        }
      }
      if (target == null) return;
      if (PsiTreeUtil.isAncestor(node.getContainingFile(), target, false)) return;
      if (ArrayUtil.contains(target, otherMovedElements)) return;
      if (target instanceof PyFile || target instanceof PsiDirectory) {
        insertImport(node, target, asName, useFromImport != null ? useFromImport : true);
      }
      else {
        insertImport(node, target, asName, true);
      }
    }
    finally {
      node.putCopyableUserData(ENCODED_IMPORT, null);
      node.putCopyableUserData(ENCODED_IMPORT_AS, null);
      node.putCopyableUserData(ENCODED_USE_FROM_IMPORT, null);
    }
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

  public static boolean insertImport(@NotNull PsiElement anchor, @NotNull PsiNamedElement element) {
    return insertImport(anchor, element, null);
  }

  public static boolean insertImport(@NotNull PsiElement anchor, @NotNull PsiNamedElement element, @Nullable String asName) {
    return insertImport(anchor, element, asName, PyCodeInsightSettings.getInstance().PREFER_FROM_IMPORT);
  }

  public static boolean insertImport(@NotNull PsiElement anchor,
                                     @NotNull PsiNamedElement element,
                                     @Nullable String asName,
                                     boolean preferFromImport) {
    if (PyBuiltinCache.getInstance(element).isBuiltin(element)) return false;
    final PsiFileSystemItem elementSource = element instanceof PsiDirectory? (PsiFileSystemItem)element : element.getContainingFile();
    final PsiFile file = anchor.getContainingFile();
    if (elementSource == file) return false;
    final QualifiedName qname = QualifiedNameFinder.findCanonicalImportPath(element, anchor);
    if (qname == null || !isValidQualifiedName(qname)) {
      return false;
    }
    final QualifiedName containingQName;
    final String importedName;
    final boolean importingModuleOrPackage = element instanceof PyFile || element instanceof PsiDirectory;
    if (importingModuleOrPackage) {
      containingQName = qname.removeLastComponent();
      importedName = qname.getLastComponent();
    }
    else {
      containingQName = qname;
      importedName = getOriginalName(element);
    }
    final AddImportHelper.ImportPriority priority = AddImportHelper.getImportPriority(anchor, elementSource);
    if (preferFromImport && !containingQName.getComponents().isEmpty() || !importingModuleOrPackage) {
      return AddImportHelper.addOrUpdateFromImportStatement(file, containingQName.toString(), importedName, asName, priority, anchor);
    }
    else {
      return AddImportHelper.addImportStatement(file, containingQName.append(importedName).toString(), asName, priority, anchor);
    }
  }

  /**
   * Searches for references inside some element (like {@link com.jetbrains.python.psi.PyAssignmentStatement}, {@link com.jetbrains.python.psi.PyFunction} etc
   * and stored them.
   * After that you can add element to some new parent. Newly created element then should be processed via {@link #restoreNamedReferences(com.intellij.psi.PsiElement)}
   * and all references would be restored.
   *
   * @param element     element to store references for
   * @param namesToSkip if reference inside of element has one of this names, it will not be saved.
   */
  public static void rememberNamedReferences(@NotNull final PsiElement element, @NotNull final String... namesToSkip) {
    element.acceptChildren(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyReferenceExpression(PyReferenceExpression node) {
        super.visitPyReferenceExpression(node);
        if (PsiTreeUtil.getParentOfType(node, PyImportStatementBase.class) != null) {
          return;
        }
        final PyImportedNameDefiner importElement = getImportElement(node);
        if (importElement != null && PsiTreeUtil.isAncestor(element, importElement, false)) {
          return;
        }
        if (!ArrayUtil.contains(node.getText(), namesToSkip)) { //Do not remember name if it should be skipped
          rememberReference(node, element);
        }
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
    final List<PsiElement> allResolveResults = multiResolveExpression(node);
    PsiElement target = ContainerUtil.getFirstItem(allResolveResults);
    if (target instanceof PsiNamedElement && !PsiTreeUtil.isAncestor(element, target, false)) {
      final PyImportedNameDefiner importElement = getImportElement(node);
      if (!PyUtil.inSameFile(element, target) && importElement == null && !(target instanceof PsiFileSystemItem)) {
        return;
      }
      if (target instanceof PyTargetExpression && PyNames.ALL.equals(((PyTargetExpression)target).getName())) {
        for (PsiElement result : allResolveResults) {
          if (result instanceof PyImportElement) {
            final QualifiedName importedQName = ((PyImportElement)result).getImportedQName();
            if (importedQName != null) {
              target = new DynamicNamedElement(target.getContainingFile(), importedQName.toString());
              break;
            }
          }
        }
      }
      node.putCopyableUserData(ENCODED_IMPORT, (PsiNamedElement)target);
      if (importElement instanceof PyImportElement) {
        node.putCopyableUserData(ENCODED_IMPORT_AS, ((PyImportElement)importElement).getAsName());
      }
      node.putCopyableUserData(ENCODED_USE_FROM_IMPORT, qualifier == null);
    }
  }

  @Nullable
  private static PyImportedNameDefiner getImportElement(PyReferenceExpression expr) {
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

  @NotNull
  private static List<PsiElement> multiResolveExpression(@NotNull PyReferenceExpression expr) {
    return ContainerUtil.mapNotNull(expr.getReference().multiResolve(false), result -> result.getElement());
  }

  /**
   * Updates the import statement if the given PSI element <em>has the same name</em> as one of the import elements of that statement.
   * It means that you should be careful it you actually want to update the source part of a "from import" statement, because in cases
   * like {@code from foo import foo} this method may do not what you expect.
   *
   * @param importStatement parent import statement that contains reference to given element
   * @param element         PSI element reference to which should be updated
   * @return                whether import statement was actually updated
   */
  public static boolean updateUnqualifiedImportOfElement(@NotNull PyImportStatementBase importStatement, @NotNull PsiNamedElement element) {
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
        if (newFile == file || insertImport(importStatement, element, importElement.getAsName(), true)) {
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
          return true;
        }
      }
    }
    return false;
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
   * Adds super classes to certain class.
   *
   * @param project      project where refactoring takes place
   * @param clazz        destination
   * @param superClasses classes to add
   */
  public static void addSuperclasses(@NotNull final Project project,
                                     @NotNull final PyClass clazz,
                                     @NotNull final PyClass... superClasses) {

    final Collection<String> superClassNames = new ArrayList<>();


    for (final PyClass superClass : Collections2.filter(Arrays.asList(superClasses), NotNullPredicate.INSTANCE)) {
      if (superClass.getName() != null) {
        superClassNames.add(superClass.getName());
        insertImport(clazz, superClass);
      }
    }

    addSuperClassExpressions(project, clazz, superClassNames, null);
  }


  /**
   * Adds expressions to superclass list
   *
   * @param project          project
   * @param clazz            class to add expressions to superclass list
   * @param paramExpressions param expressions. Like "object" or "MySuperClass". Will not add any param exp. if null.
   * @param keywordArguments keyword args like "metaclass=ABCMeta". key-value pairs.  Will not add any keyword arg. if null.
   */
  public static void addSuperClassExpressions(@NotNull final Project project,
                                              @NotNull final PyClass clazz,
                                              @Nullable final Collection<String> paramExpressions,
                                              @Nullable final Collection<Pair<String, String>> keywordArguments) {
    final PyElementGenerator generator = PyElementGenerator.getInstance(project);
    final LanguageLevel languageLevel = LanguageLevel.forElement(clazz);

    PyArgumentList superClassExpressionList = clazz.getSuperClassExpressionList();
    boolean addExpression = false;
    if (superClassExpressionList == null) {
      superClassExpressionList = generator.createFromText(languageLevel, PyClass.class, "class foo():pass").getSuperClassExpressionList();
      assert superClassExpressionList != null : "expression not created";
      addExpression = true;
    }


    generator.createFromText(LanguageLevel.PYTHON34, PyClass.class, "class foo(object, metaclass=Foo): pass").getSuperClassExpressionList();
    if (paramExpressions != null) {
      for (final String paramExpression : paramExpressions) {
        superClassExpressionList.addArgument(generator.createParameter(paramExpression));
      }
    }

    if (keywordArguments != null) {
      for (final Pair<String, String> keywordArgument : keywordArguments) {
        superClassExpressionList.addArgument(generator.createKeywordArgument(languageLevel, keywordArgument.first, keywordArgument.second));
      }
    }

    // If class has no expression list, then we need to add it manually.
    if (addExpression) {
      final ASTNode classNameNode = clazz.getNameNode(); // For nameless classes we simply add expression list directly to them
      final PsiElement elementToAddAfter = (classNameNode == null) ? clazz.getFirstChild() : classNameNode.getPsi();
      clazz.addAfter(superClassExpressionList, elementToAddAfter);
    }
  }

  /**
   * Optimizes imports resorting them and removing unneeded
   *
   * @param file file to optimize imports
   */
  public static void optimizeImports(@NotNull final PsiFile file) {
    new PyImportOptimizer().processFile(file).run();
  }

  /**
   * Adds class attributeName (field) if it does not exist. like __metaclass__ = ABCMeta. Or CLASS_FIELD = 42.
   *
   * @param aClass        where to add
   * @param attributeName attribute's name. Like __metaclass__ or CLASS_FIELD
   * @param value         it's value. Like ABCMeta or 42.
   * @return newly inserted attribute
   */
  @Nullable
  public static PsiElement addClassAttributeIfNotExist(
    @NotNull final PyClass aClass,
    @NotNull final String attributeName,
    @NotNull final String value) {
    if (aClass.findClassAttribute(attributeName, false, null) != null) {
      return null; //Do not add any if exist already
    }
    final PyElementGenerator generator = PyElementGenerator.getInstance(aClass.getProject());
    final String text = String.format("%s = %s", attributeName, value);
    final LanguageLevel level = LanguageLevel.forElement(aClass);

    final PyAssignmentStatement assignmentStatement = generator.createFromText(level, PyAssignmentStatement.class, text);
    //TODO: Add metaclass to the top. Add others between last attributeName and first method
    return PyUtil.addElementToStatementList(assignmentStatement, aClass.getStatementList(), true);
  }

  private static class DynamicNamedElement extends LightElement implements PsiNamedElement {
    private final PsiFile myFile;
    private final String myName;

    public DynamicNamedElement(@NotNull PsiFile file, @NotNull String name) {
      super(file.getManager(), file.getLanguage());
      myName = name;
      myFile = file;
    }

    @Override
    public String toString() {
      return "DynamicNamedElement(file='" + getContainingFile().getName() + "', name='" + getName() +"')";
    }

    @Override
    public PsiFile getContainingFile() {
      return myFile;
    }

    @Override
    public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
      return null;
    }

    @Override
    public String getName() {
      return myName;
    }
  }
}
