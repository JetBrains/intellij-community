// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.classes;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.codeInsight.imports.PyImportOptimizer;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyImportedModule;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.refactoring.PyPsiRefactoringUtil;
import com.jetbrains.python.refactoring.classes.extractSuperclass.PyExtractSuperclassHelper;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Dennis.Ushakov
 */
public final class PyClassRefactoringUtil {
  private static final Logger LOG = Logger.getInstance(PyClassRefactoringUtil.class.getName());
  private static final Key<PsiNamedElement> ENCODED_IMPORT = Key.create("PyEncodedImport");
  private static final Key<Boolean> ENCODED_USE_FROM_IMPORT = Key.create("PyEncodedUseFromImport");
  private static final Key<String> ENCODED_IMPORT_AS = Key.create("PyEncodedImportAs");
  private static final Key<List<PyReferenceExpression>> INJECTION_REFERENCES = Key.create("PyInjectionReferences");
  private static final Key<Set<FutureFeature>> ENCODED_FROM_FUTURE_IMPORTS = Key.create("PyFromFutureImports");


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
  public static List<PyAssignmentStatement> copyFieldDeclarationToStatement(@NotNull final Collection<? extends PyAssignmentStatement> assignmentStatements,
                                                                            @NotNull final PyStatementList superClassStatement,
                                                                            @Nullable final PyClass dequalifyIfDeclaredInClass) {
    final List<PyAssignmentStatement> declarations = new ArrayList<>(assignmentStatements.size());

    for (final PyAssignmentStatement pyAssignmentStatement : assignmentStatements) {
      final PyElement value = pyAssignmentStatement.getAssignedValue();
      final PyAssignmentStatement newDeclaration = (PyAssignmentStatement)pyAssignmentStatement.copy();

      if (value instanceof PyReferenceExpression && dequalifyIfDeclaredInClass != null) {
        final String newValue = getNewValueToAssign((PyReferenceExpression)value, dequalifyIfDeclaredInClass);

        setNewAssigneeValue(newDeclaration, newValue);

      }

      declarations.add(PyPsiRefactoringUtil.addElementToStatementList(newDeclaration, superClassStatement));
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

  /**
   * Adds methods to class.
   *
   * @param destination where to add methods
   * @param methods     methods
   * @param skipIfExist do not add anything if method already exists
   * @return newly added methods or existing one (if skipIfExists is true and method already exists)
   */
  @NotNull
  public static List<PyFunction> addMethods(@NotNull final PyClass destination, final boolean skipIfExist, final PyFunction @NotNull ... methods) {

    final PyStatementList destStatementList = destination.getStatementList();
    final List<PyFunction> result = new ArrayList<>(methods.length);

    for (final PyFunction method : methods) {

      final PyFunction existingMethod = destination.findMethodByName(method.getName(), false, null);
      if ((existingMethod != null) && skipIfExist) {
        result.add(existingMethod);
        continue; //We skip adding if class already has this method.
      }

      result.add(insertMethodInProperPlace(destStatementList, method));
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
    boolean methodIsInit = PyNames.INIT.equals(method.getName());
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
   * Restores references saved by {@link #rememberNamedReferences(PsiElement, String...)}.
   *
   * @param element newly created element to restore references
   * @see #rememberNamedReferences(PsiElement, String...)
   */
  public static void restoreNamedReferences(@NotNull final PsiElement element) {
    restoreNamedReferences(element, null);
  }

  public static void restoreNamedReferences(@NotNull final PsiElement newElement, @Nullable final PsiElement oldElement) {
    restoreNamedReferences(newElement, oldElement, PsiElement.EMPTY_ARRAY);
  }

  public static void restoreNamedReferences(@NotNull final PsiElement newElement,
                                            @Nullable final PsiElement oldElement,
                                            final PsiElement @NotNull [] otherMovedElements) {
    Set<FutureFeature> fromFutureImports = newElement.getCopyableUserData(ENCODED_FROM_FUTURE_IMPORTS);
    newElement.putCopyableUserData(ENCODED_FROM_FUTURE_IMPORTS, null);
    PsiFile destFile = newElement.getContainingFile();
    if (fromFutureImports != null & destFile != null) {
      for (FutureFeature futureFeature: fromFutureImports) {
        AddImportHelper.addOrUpdateFromImportStatement(destFile, PyNames.FUTURE_MODULE, futureFeature.toString(), null,
                                                       AddImportHelper.ImportPriority.FUTURE, null);
      }
    }

    newElement.acceptChildren(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyReferenceExpression(@NotNull PyReferenceExpression node) {
        super.visitPyReferenceExpression(node);
        restoreReference(node, node, otherMovedElements);
      }

      @Override
      public void visitPyStringLiteralExpression(@NotNull PyStringLiteralExpression node) {
        super.visitPyStringLiteralExpression(node);
        if (oldElement != null) {
          for (PsiReference ref : node.getReferences()) {
            if (ref.isReferenceTo(oldElement)) {
              ref.bindToElement(newElement);
            }
          }
        }
        restoreReference(node, node, otherMovedElements);
      }

      @Override
      public void visitComment(@NotNull PsiComment comment) {
        super.visitComment(comment);
        if (comment instanceof PsiLanguageInjectionHost) {
          restoreReference(comment, comment, otherMovedElements);
        }
      }
    });
  }


  public static void restoreReference(@NotNull PsiElement sourceNode,
                                      @NotNull PsiElement targetNode,
                                      PsiElement @NotNull [] otherMovedElements) {
    try {
      if (sourceNode instanceof PyReferenceExpression) {
        doRestoreReference((PyReferenceExpression)sourceNode, targetNode, otherMovedElements);
      }
      else if (sourceNode instanceof PsiLanguageInjectionHost) {
        var injectionReferences = sourceNode.getCopyableUserData(INJECTION_REFERENCES);
        if (injectionReferences != null) {
          injectionReferences.forEach(expression -> doRestoreReference(expression, targetNode, otherMovedElements));
        }
      }
    }
    finally {
      sourceNode.putCopyableUserData(INJECTION_REFERENCES, null);
    }
  }

  private static void doRestoreReference(@NotNull PyReferenceExpression sourceNode,
                                         @NotNull PsiElement targetNode,
                                         PsiElement @NotNull [] otherMovedElements) {
    try {
      PsiNamedElement target = sourceNode.getCopyableUserData(ENCODED_IMPORT);
      final String asName = sourceNode.getCopyableUserData(ENCODED_IMPORT_AS);
      final Boolean useFromImport = sourceNode.getCopyableUserData(ENCODED_USE_FROM_IMPORT);
      if (target == null) return;
      if (target instanceof PsiDirectory) {
        target = (PsiNamedElement)PyUtil.getPackageElement((PsiDirectory)target, sourceNode);
      }
      if (target instanceof PyFunction) {
        final PyFunction f = (PyFunction)target;
        final PyClass c = f.getContainingClass();
        if (c != null && c.multiFindInitOrNew(false, null).contains(f)) {
          target = c;
        }
      }
      if (PsiTreeUtil.isAncestor(targetNode.getContainingFile(), target, false)) return;
      if (ArrayUtil.contains(target, otherMovedElements)) return;
      if (target instanceof PyFile || target instanceof PsiDirectory) {
        PyPsiRefactoringUtil.insertImport(targetNode, target, asName, useFromImport != null ? useFromImport : true);
      }
      else {
        PyPsiRefactoringUtil.insertImport(targetNode, target, asName, true);
      }
    }
    finally {
      sourceNode.putCopyableUserData(ENCODED_IMPORT, null);
      sourceNode.putCopyableUserData(ENCODED_IMPORT_AS, null);
      sourceNode.putCopyableUserData(ENCODED_USE_FROM_IMPORT, null);
    }
  }

  /**
   * Searches for references inside some element (like {@link PyAssignmentStatement}, {@link PyFunction} etc
   * and stored them.
   * After that you can add element to some new parent. Newly created element then should be processed via {@link #restoreNamedReferences(PsiElement)}
   * and all references would be restored.
   *
   * @param element     element to store references for
   * @param namesToSkip if reference inside of element has one of this names, it will not be saved.
   */
  public static void rememberNamedReferences(@NotNull final PsiElement element, final String @NotNull ... namesToSkip) {
    PsiFile containingFile = element.getContainingFile();
    if (containingFile instanceof PyFile) {
      Set<FutureFeature> fromFutureImports = collectFromFutureImports((PyFile)containingFile);
      element.putCopyableUserData(ENCODED_FROM_FUTURE_IMPORTS, fromFutureImports);
    }

    element.accept(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyReferenceExpression(@NotNull PyReferenceExpression node) {
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

      @Override
      public void visitComment(@NotNull PsiComment comment) {
        super.visitComment(comment);
        if (comment instanceof PsiLanguageInjectionHost) {
          rememberInjectionReferences((PsiLanguageInjectionHost)comment, element, namesToSkip);
        }
      }

      @Override
      public void visitPyStringLiteralExpression(@NotNull PyStringLiteralExpression expression) {
        super.visitPyStringLiteralExpression(expression);
        rememberInjectionReferences(expression, element, namesToSkip);
      }
    });
  }

  private static void rememberInjectionReferences(@NotNull PsiLanguageInjectionHost host,
                                                  @NotNull PsiElement element,
                                                  String @NotNull ... namesToSkip) {
    final List<Pair<PsiElement, TextRange>> files = InjectedLanguageManager.getInstance(host.getProject()).getInjectedPsiFiles(host);
    if (files == null) return;
    for (Pair<PsiElement, TextRange> pair : files) {
      pair.getFirst().accept(
        new PyRecursiveElementVisitor() {
          @Override
          public void visitPyReferenceExpression(@NotNull PyReferenceExpression expression) {
            super.visitPyReferenceExpression(expression);
            if (!ArrayUtil.contains(expression.getText(), namesToSkip)) {
              rememberReference(expression, element);
              rememberReferenceInInjectionHost(expression, host);
            }
          }
        });
    }
  }

  private static void rememberReferenceInInjectionHost(@NotNull PyReferenceExpression expression,
                                                       @NotNull PsiLanguageInjectionHost host) {
    var encodedImports = host.getCopyableUserData(INJECTION_REFERENCES);
    var rememberedReferences = encodedImports == null ? new ArrayList<PyReferenceExpression>() : encodedImports;
    rememberedReferences.add(expression);
    host.putCopyableUserData(INJECTION_REFERENCES, rememberedReferences);
  }

  private static @NotNull Set<FutureFeature> collectFromFutureImports(@NotNull PyFile file) {
    EnumSet<FutureFeature> result = EnumSet.noneOf(FutureFeature.class);
    for (FutureFeature feature: FutureFeature.values()) {
      if (file.hasImportFromFuture(feature)) {
        result.add(feature);
      }
    }
    return result;
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
   * Forces the use of 'import as' when restoring references (i.e. if there are name clashes)
   * @param node with encoded import
   * @param asName new alias for import
   */
  public static void forceAsName(@NotNull PyReferenceExpression node, @NotNull String asName) {
    if (node.getCopyableUserData(ENCODED_IMPORT) == null) {
      LOG.warn("As name is forced on the referenceExpression, that has no encoded import. Forcing it will likely be ignored.");
    }
    node.putCopyableUserData(ENCODED_IMPORT_AS, asName);
  }

  public static void transferEncodedImports(@NotNull PyReferenceExpression source, @NotNull PyReferenceExpression target) {
    target.putCopyableUserData(ENCODED_IMPORT, source.getCopyableUserData(ENCODED_IMPORT));
    target.putCopyableUserData(ENCODED_IMPORT_AS, source.getCopyableUserData(ENCODED_IMPORT_AS));
    target.putCopyableUserData(ENCODED_USE_FROM_IMPORT, source.getCopyableUserData(ENCODED_USE_FROM_IMPORT));

    source.putCopyableUserData(ENCODED_IMPORT, null);
    source.putCopyableUserData(ENCODED_IMPORT_AS, null);
    source.putCopyableUserData(ENCODED_USE_FROM_IMPORT, null);
  }

  public static boolean hasEncodedTarget(@NotNull PyReferenceExpression node) {
    return node.getCopyableUserData(ENCODED_IMPORT) != null;
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
    final String name = PyPsiRefactoringUtil.getOriginalName(element);
    if (name != null) {
      PyImportElement importElement = null;
      for (PyImportElement e : importStatement.getImportElements()) {
        if (name.equals(PyPsiRefactoringUtil.getOriginalName(e))) {
          importElement = e;
        }
      }
      if (importElement != null) {
        final PsiFile file = importStatement.getContainingFile();
        final PsiFile newFile = element.getContainingFile();
        if (newFile == file || PyPsiRefactoringUtil.insertImport(importStatement, element, importElement.getAsName(), true)) {
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


  /**
   * Optimizes imports resorting them and removing unneeded
   *
   * @param file file to optimize imports
   */
  public static void optimizeImports(@NotNull final PsiFile file) {
    PyImportOptimizer.onlyRemoveUnused().processFile(file).run();
  }

  @NotNull
  public static PyFile getOrCreateFile(String path, Project project) {
    final VirtualFile vfile = LocalFileSystem.getInstance().findFileByIoFile(new File(path));
    final PsiFile psi;
    if (vfile == null) {
      final File file = new File(path);
      try {
        final VirtualFile baseDir = project.getBaseDir();
        final FileTemplateManager fileTemplateManager = FileTemplateManager.getInstance(project);
        final FileTemplate template = fileTemplateManager.getInternalTemplate("Python Script");
        final Properties properties = fileTemplateManager.getDefaultProperties();
        properties.setProperty("NAME", FileUtilRt.getNameWithoutExtension(file.getName()));
        final String content = template.getText(properties);
        psi = PyExtractSuperclassHelper.placeFile(project,
                                                  StringUtil.notNullize(
                                                    file.getParent(),
                                                    baseDir != null ? baseDir
                                                      .getPath() : "."
                                                  ),
                                                  file.getName(),
                                                  content
        );
      }
      catch (IOException e) {
        throw new IncorrectOperationException(String.format("Cannot create file '%s'", path), (Throwable)e);
      }
    }
    else {
      psi = PsiManager.getInstance(project).findFile(vfile);
    }
    if (!(psi instanceof PyFile)) {
      throw new IncorrectOperationException(PyPsiBundle.message(
        "refactoring.move.module.members.error.cannot.place.elements.into.nonpython.file"));
    }
    return (PyFile)psi;
  }

  private static final class DynamicNamedElement extends LightElement implements PsiNamedElement {
    private final PsiFile myFile;
    private final String myName;

    private DynamicNamedElement(@NotNull PsiFile file, @NotNull String name) {
      super(file.getManager(), file.getLanguage());
      myName = name;
      myFile = file;
    }

    @Override
    public String toString() {
      return "DynamicNamedElement(file='" + getContainingFile().getName() + "', name='" + getName() + "')";
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
