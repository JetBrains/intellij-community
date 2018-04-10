// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.imports;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.documentation.doctest.PyDocstringFile;
import com.jetbrains.python.formatter.PyBlock;
import com.jetbrains.python.formatter.PyCodeStyleSettings;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.pyi.PyiFile;
import com.jetbrains.python.pyi.PyiUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static com.jetbrains.python.psi.PyUtil.as;
import static com.jetbrains.python.psi.PyUtil.sure;

/**
 * Does the actual job of adding an import statement into a file.
 * User: dcheryasov
 */
public class AddImportHelper {
  private static final Logger LOG = Logger.getInstance(AddImportHelper.class);

  // normal imports go first, then "from" imports
  private static final Comparator<PyImportStatementBase> IMPORT_TYPE_COMPARATOR = (import1, import2) -> {
    final int firstIsFromImport = import1 instanceof PyFromImportStatement ? 1 : 0;
    final int secondIsFromImport = import2 instanceof PyFromImportStatement ? 1 : 0;
    return firstIsFromImport - secondIsFromImport;
  };

  private static final Comparator<PyImportStatementBase> IMPORT_NAMES_COMPARATOR =
    (import1, import2) -> ContainerUtil.compareLexicographically(getSortNames(import1), getSortNames(import2));

  @NotNull
  private static List<String> getSortNames(@NotNull PyImportStatementBase importStatement) {
    final List<String> result = new ArrayList<>();
    final PyFromImportStatement fromImport = as(importStatement, PyFromImportStatement.class);
    if (fromImport != null) {
      // because of that relative imports go to the end of an import block
      result.add(StringUtil.repeatSymbol('.', fromImport.getRelativeLevel()));
      final QualifiedName source = fromImport.getImportSourceQName();
      result.add(Objects.toString(source, ""));
      if (fromImport.isStarImport()) {
        result.add("*");
      }
    }
    else {
      // fake relative level
      result.add("");
    }

    for (PyImportElement importElement : importStatement.getImportElements()) {
      final QualifiedName qualifiedName = importElement.getImportedQName();
      result.add(Objects.toString(qualifiedName, ""));
      result.add(StringUtil.notNullize(importElement.getAsName()));
    }
    return result;
  }

  /**
   * Creates and return comparator for import statements that compares them according to the rules specified in the code style settings.
   * It's intended to be used for imports that have the same import priority in order to sort them within the corresponding group.
   *
   * @see ImportPriority
   */
  @NotNull
  public static Comparator<PyImportStatementBase> getSameGroupImportsComparator(@NotNull Project project) {
    final PyCodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project).getCustomSettings(PyCodeStyleSettings.class);
    if (settings.OPTIMIZE_IMPORTS_SORT_BY_TYPE_FIRST) {
      return IMPORT_TYPE_COMPARATOR.thenComparing(IMPORT_NAMES_COMPARATOR);
    }
    else {
      return IMPORT_NAMES_COMPARATOR.thenComparing(IMPORT_TYPE_COMPARATOR);
    }
  }

  public enum ImportPriority {
    FUTURE,
    BUILTIN,
    THIRD_PARTY,
    PROJECT
  }

  static class ImportPriorityChoice {
    private final ImportPriority myPriority;
    private final String myDescription;

    public ImportPriorityChoice(@NotNull ImportPriority priority, @NotNull String description) {
      myPriority = priority;
      myDescription = description;
    }

    @NotNull
    public ImportPriority getPriority() {
      return myPriority;
    }

    @NotNull
    public String getDescription() {
      return myDescription;
    }
  }

  private static final ImportPriority UNRESOLVED_SYMBOL_PRIORITY = ImportPriority.THIRD_PARTY;

  private AddImportHelper() {
  }

  public static void addLocalImportStatement(@NotNull PsiElement element, @NotNull String name) {
    final PyElementGenerator generator = PyElementGenerator.getInstance(element.getProject());
    final LanguageLevel languageLevel = LanguageLevel.forElement(element);

    final PsiElement anchor = getLocalInsertPosition(element);
    final PsiElement parentElement = sure(anchor).getParent();
    if (parentElement != null) {
      parentElement.addBefore(generator.createImportStatement(languageLevel, name, null), anchor);
    }
  }

  public static void addLocalFromImportStatement(@NotNull PsiElement element, @NotNull String qualifier, @NotNull String name) {
    final PyElementGenerator generator = PyElementGenerator.getInstance(element.getProject());
    final LanguageLevel languageLevel = LanguageLevel.forElement(element);

    final PsiElement anchor = getLocalInsertPosition(element);
    final PsiElement parentElement = sure(anchor).getParent();
    if (parentElement != null) {
      parentElement.addBefore(generator.createFromImportStatement(languageLevel, qualifier, name, null), anchor);
    }

  }

  @Nullable
  public static PsiElement getLocalInsertPosition(@NotNull PsiElement anchor) {
    return PsiTreeUtil.getParentOfType(anchor, PyStatement.class, false);
  }

  /**
   * Returns position in the file after all leading comments, docstring and import statements.
   * <p>
   * Returned PSI element is intended to be used as "anchor" parameter for {@link PsiElement#addBefore(PsiElement, PsiElement)},
   * hence {@code null} means that element to be inserted will be the first in the file.
   *
   * @param file target file where some new top-level element is going to be inserted
   * @return anchor PSI element as described
   */
  @Nullable
  public static PsiElement getFileInsertPosition(final PsiFile file) {
    return getInsertPosition(file, null, null);
  }

  @Nullable
  private static PsiElement getInsertPosition(@NotNull PsiElement insertParent,
                                              @Nullable PyImportStatementBase newImport,
                                              @Nullable ImportPriority priority) {
    PsiElement feeler = insertParent.getFirstChild();
    if (feeler == null) return null;
    // skip initial comments and whitespace and try to get just below the last import stmt
    boolean skippedOverImports = false;
    boolean skippedOverDoc = false;
    PsiElement seeker = feeler;
    final boolean isInjected = InjectedLanguageManager.getInstance(feeler.getProject()).isInjectedFragment(feeler.getContainingFile());
    PyImportStatementBase importAbove = null, importBelow = null;
    do {
      if (feeler instanceof PyImportStatementBase && !isInjected) {
        final PyImportStatementBase existingImport = (PyImportStatementBase)feeler;
        if (priority != null && newImport != null) {
          if (shouldInsertBefore(newImport, existingImport, priority)) {
            importBelow = existingImport;
            break;
          }
          else {
            importAbove = existingImport;
          }
        }
        seeker = feeler;
        feeler = feeler.getNextSibling();
        skippedOverImports = true;
      }
      else if (PsiTreeUtil.instanceOf(feeler, PsiWhiteSpace.class, PsiComment.class)) {
        seeker = feeler;
        feeler = feeler.getNextSibling();
      }
      // maybe we arrived at the doc comment stmt; skip over it, too
      else if (!skippedOverImports && !skippedOverDoc && insertParent instanceof PyFile) {
        // this gives the literal; its parent is the expr seeker may have encountered
        final PsiElement docElem = DocStringUtil.findDocStringExpression((PyElement)insertParent);
        if (docElem != null && docElem.getParent() == feeler) {
          feeler = feeler.getNextSibling();
          seeker = feeler; // skip over doc even if there's nothing below it
          skippedOverDoc = true;
        }
        else {
          break; // not a doc comment, stop on it
        }
      }
      else {
        break; // some other statement, stop
      }
    }
    while (feeler != null);
    final ImportPriority priorityAbove = importAbove != null ? getImportPriority(importAbove) : null;
    final ImportPriority priorityBelow = importBelow != null ? getImportPriority(importBelow) : null;
    if (newImport != null && (priorityAbove == null || priorityAbove.compareTo(priority) < 0)) {
      newImport.putCopyableUserData(PyBlock.IMPORT_GROUP_BEGIN, true);
    }
    if (priorityBelow != null) {
      // actually not necessary because existing import with higher priority (i.e. lower import group)
      // probably should have IMPORT_GROUP_BEGIN flag already, but we add it anyway just for safety
      if (priorityBelow.compareTo(priority) > 0) {
        importBelow.putCopyableUserData(PyBlock.IMPORT_GROUP_BEGIN, true);
      }
      else if (priorityBelow == priority) {
        importBelow.putCopyableUserData(PyBlock.IMPORT_GROUP_BEGIN, null);
      }
    }
    return seeker;
  }

  private static boolean shouldInsertBefore(@Nullable PyImportStatementBase newImport,
                                            @NotNull PyImportStatementBase existingImport,
                                            @NotNull ImportPriority priority) {
    final ImportPriority existingImportPriority = getImportPriority(existingImport);
    final int byPriority = priority.compareTo(existingImportPriority);
    if (byPriority != 0) {
      return byPriority < 0;
    }
    if (newImport == null) {
      return false;
    }
    return getSameGroupImportsComparator(existingImport.getProject()).compare(newImport, existingImport) < 0;
  }

  @NotNull
  public static ImportPriority getImportPriority(@NotNull PsiElement importLocation, @NotNull PsiFileSystemItem toImport) {
    final ImportPriorityChoice choice = getImportPriorityWithReason(importLocation, toImport);
    LOG.debug(String.format("Import group for %s at %s is %s: %s", toImport, importLocation.getContainingFile(),
                            choice.myPriority, choice.myDescription));
    return choice.myPriority;
  }

  @NotNull
  public static ImportPriority getImportPriority(@NotNull PyImportStatementBase importStatement) {
    final ImportPriorityChoice choice = getImportPriorityWithReason(importStatement);
    LOG.debug(String.format("Import group for '%s' is %s: %s", importStatement.getText(), choice.myPriority, choice.myDescription));
    return choice.myPriority;
  }

  @NotNull
  static ImportPriorityChoice getImportPriorityWithReason(@NotNull PyImportStatementBase importStatement) {
    final PsiElement resolved;
    final PsiElement resolveAnchor;
    if (importStatement instanceof PyFromImportStatement) {
      final PyFromImportStatement fromImportStatement = (PyFromImportStatement)importStatement;
      if (fromImportStatement.isFromFuture()) {
        return new ImportPriorityChoice(ImportPriority.FUTURE, "import from __future__");
      }
      if (fromImportStatement.getRelativeLevel() > 0) {
        return new ImportPriorityChoice(ImportPriority.PROJECT, "explicit relative import");
      }
      resolveAnchor = ((PyFromImportStatement)importStatement).getImportSource();
      resolved = fromImportStatement.resolveImportSource();
    }
    else {
      final PyImportElement firstImportElement = ArrayUtil.getFirstElement(importStatement.getImportElements());
      if (firstImportElement == null) {
        return new ImportPriorityChoice(UNRESOLVED_SYMBOL_PRIORITY, "incomplete import statement");
      }
      resolveAnchor = firstImportElement;
      resolved = firstImportElement.resolve();
    }
    if (resolved == null) {
      return new ImportPriorityChoice(UNRESOLVED_SYMBOL_PRIORITY,
                                      resolveAnchor == null ? "incomplete import statement" : resolveAnchor.getText() + " is unresolved");
    }

    PsiFileSystemItem resolvedFileOrDir;
    if (resolved instanceof PsiDirectory) {
      resolvedFileOrDir = (PsiFileSystemItem)resolved;
    }
    // resolved symbol may be PsiPackage in Jython
    else if (resolved instanceof PsiDirectoryContainer) {
      resolvedFileOrDir = ArrayUtil.getFirstElement(((PsiDirectoryContainer)resolved).getDirectories());
    }
    else {
      resolvedFileOrDir = resolved.getContainingFile();
    }

    if (resolvedFileOrDir instanceof PyiFile) {
      resolvedFileOrDir = as(PyiUtil.getOriginalElement((PyiFile)resolvedFileOrDir), PsiFileSystemItem.class);
    }

    if (resolvedFileOrDir == null) {
      return new ImportPriorityChoice(UNRESOLVED_SYMBOL_PRIORITY, resolved + " is not a file or directory");
    }

    return getImportPriorityWithReason(importStatement, resolvedFileOrDir);
  }

  @NotNull
  static ImportPriorityChoice getImportPriorityWithReason(@NotNull PsiElement importLocation, @NotNull PsiFileSystemItem toImport) {
    final VirtualFile vFile = toImport.getVirtualFile();
    if (vFile == null) {
      return new ImportPriorityChoice(UNRESOLVED_SYMBOL_PRIORITY, toImport + " doesn't have an associated virtual file");
    }
    final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(toImport.getProject());
    final ProjectFileIndex fileIndex = projectRootManager.getFileIndex();
    if (fileIndex.isInContent(vFile) && !fileIndex.isInLibraryClasses(vFile)) {
      return new ImportPriorityChoice(ImportPriority.PROJECT, vFile + " belongs to the project and not under interpreter paths");
    }
    final Module module = ModuleUtilCore.findModuleForPsiElement(importLocation);
    final Sdk pythonSdk = module != null ? PythonSdkType.findPythonSdk(module) : projectRootManager.getProjectSdk();

    if (PythonSdkType.isStdLib(vFile, pythonSdk)) {
      return new ImportPriorityChoice(ImportPriority.BUILTIN, vFile + " is either in lib but not under site-packages," +
                                                              " or belongs to the root of skeletons," +
                                                              " or is a .pyi stub definition for stdlib module");
    }
    else {
      return new ImportPriorityChoice(ImportPriority.THIRD_PARTY, pythonSdk == null ? "SDK for " + vFile + " isn't found"
                                                                                    : "Fall back value for " + vFile);
    }
  }

  /**
   * Adds an import statement, if it doesn't exist yet, presumably below all other initial imports in the file.
   *
   * @param file   where to operate
   * @param name   which to import (qualified is OK)
   * @param asName optional name for 'as' clause
   * @param anchor place where the imported name was used. It will be used to determine proper block where new import should be inserted,
   *               e.g. inside conditional block or try/except statement. Also if anchor is another import statement, new import statement
   *               will be inserted right after it.
   * @return whether import statement was actually added
   */
  public static boolean addImportStatement(@NotNull PsiFile file,
                                           @NotNull String name,
                                           @Nullable String asName,
                                           @Nullable ImportPriority priority,
                                           @Nullable PsiElement anchor) {
    if (!(file instanceof PyFile)) {
      return false;
    }
    final List<PyImportElement> existingImports = ((PyFile)file).getImportTargets();
    for (PyImportElement element : existingImports) {
      final QualifiedName qName = element.getImportedQName();
      if (qName != null && name.equals(qName.toString())) {
        if ((asName != null && asName.equals(element.getAsName())) || (asName == null && element.getAsName() == null)) {
          return false;
        }
      }
    }

    final PyElementGenerator generator = PyElementGenerator.getInstance(file.getProject());
    final LanguageLevel languageLevel = LanguageLevel.forElement(file);
    final PyImportStatement importNodeToInsert = generator.createImportStatement(languageLevel, name, asName);
    final PyImportStatementBase importStatement = PsiTreeUtil.getParentOfType(anchor, PyImportStatementBase.class, false);
    final PsiElement insertParent = importStatement != null && importStatement.getContainingFile() == file ?
                                    importStatement.getParent() : file;
    try {
      if (anchor instanceof PyImportStatementBase) {
        insertParent.addAfter(importNodeToInsert, anchor);
      }
      else {
        insertParent.addBefore(importNodeToInsert, getInsertPosition(insertParent, importNodeToInsert, priority));
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return true;
  }

  /**
   * Adds a new {@link PyFromImportStatement} statement within other top-level imports or as specified by anchor.
   *
   * @param file   where to operate
   * @param from   import source (reference after {@code from} keyword)
   * @param name   imported name (identifier after {@code import} keyword)
   * @param asName optional alias (identifier after {@code as} keyword)
   * @param anchor place where the imported name was used. It will be used to determine proper block where new import should be inserted,
   *               e.g. inside conditional block or try/except statement. Also if anchor is another import statement, new import statement
   *               will be inserted right after it.
   * @see #addOrUpdateFromImportStatement
   */
  public static void addFromImportStatement(@NotNull PsiFile file,
                                            @NotNull String from,
                                            @NotNull String name,
                                            @Nullable String asName,
                                            @Nullable ImportPriority priority,
                                            @Nullable PsiElement anchor) {
    final PyElementGenerator generator = PyElementGenerator.getInstance(file.getProject());
    final LanguageLevel languageLevel = LanguageLevel.forElement(file);
    final PyFromImportStatement newImport = generator.createFromImportStatement(languageLevel, from, name, asName);
    addFromImportStatement(file, newImport, priority, anchor);
  }

  /**
   * Adds a new {@link PyFromImportStatement} statement within other top-level imports or as specified by anchor.
   *
   * @param file      where to operate
   * @param newImport new "from import" statement to insert. It may be generated, because it won't be used for resolving anyway.
   *                  You might want to use overloaded version of this method to generate such statement automatically.
   * @param anchor    place where the imported name was used. It will be used to determine proper block where new import should be inserted,
   *                  e.g. inside conditional block or try/except statement. Also if anchor is another import statement, new import statement
   *                  will be inserted right after it.
   * @see #addFromImportStatement(PsiFile, String, String, String, ImportPriority, PsiElement)
   * @see #addFromImportStatement
   */
  public static void addFromImportStatement(@NotNull PsiFile file,
                                            @NotNull PyFromImportStatement newImport,
                                            @Nullable ImportPriority priority,
                                            @Nullable PsiElement anchor) {
    try {
      final PyImportStatementBase parentImport = PsiTreeUtil.getParentOfType(anchor, PyImportStatementBase.class, false);
      final InjectedLanguageManager manager = InjectedLanguageManager.getInstance(file.getProject());
      final PsiLanguageInjectionHost injectionHost = manager.getInjectionHost(file);
      final boolean insideDoctest = file instanceof PyDocstringFile &&
                                    injectionHost != null &&
                                    DocStringUtil.getParentDefinitionDocString(injectionHost) == injectionHost;

      final PsiElement insertParent;
      if (parentImport != null && parentImport.getContainingFile() == file) {
        insertParent = parentImport.getParent();
      }
      else if (injectionHost != null && !insideDoctest) {
        insertParent = manager.getTopLevelFile(file);
      }
      else {
        insertParent = file;
      }

      if (insideDoctest) {
        final PsiElement element = insertParent.addBefore(newImport, getInsertPosition(insertParent, newImport, priority));
        PsiElement whitespace = element.getNextSibling();
        if (!(whitespace instanceof PsiWhiteSpace)) {
          whitespace = PsiParserFacade.SERVICE.getInstance(file.getProject()).createWhiteSpaceFromText("  >>> ");
        }
        insertParent.addBefore(whitespace, element);
      }
      else if (anchor instanceof PyImportStatementBase) {
        insertParent.addAfter(newImport, anchor);
      }
      else {
        insertParent.addBefore(newImport, getInsertPosition(insertParent, newImport, priority));
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  /**
   * Adds new {@link PyFromImportStatement} in file or append {@link PyImportElement} to
   * existing from import statement.
   *
   * @param file     module where import will be added
   * @param from     import source (reference after {@code from} keyword)
   * @param name     imported name (identifier after {@code import} keyword)
   * @param asName   optional alias (identifier after {@code as} keyword)
   * @param priority optional import priority used to sort imports
   * @param anchor   place where the imported name was used. It will be used to determine proper block where new import should be inserted,
   *                 e.g. inside conditional block or try/except statement. Also if anchor is another import statement, new import statement
   *                 will be inserted right after it.
   * @return whether import was actually added
   * @see #addFromImportStatement
   */
  public static boolean addOrUpdateFromImportStatement(@NotNull PsiFile file,
                                                       @NotNull String from,
                                                       @NotNull String name,
                                                       @Nullable String asName,
                                                       @Nullable ImportPriority priority,
                                                       @Nullable PsiElement anchor) {
    final List<PyFromImportStatement> existingImports = ((PyFile)file).getFromImports();
    for (PyFromImportStatement existingImport : existingImports) {
      if (existingImport.isStarImport()) {
        continue;
      }
      final QualifiedName qName = existingImport.getImportSourceQName();
      if (qName != null && qName.toString().equals(from) && existingImport.getRelativeLevel() == 0) {
        for (PyImportElement el : existingImport.getImportElements()) {
          final QualifiedName importedQName = el.getImportedQName();
          if (importedQName != null && StringUtil.equals(name, importedQName.toString()) && StringUtil.equals(asName, el.getAsName())) {
            return false;
          }
        }
        final PyElementGenerator generator = PyElementGenerator.getInstance(file.getProject());
        final PyImportElement importElement = generator.createImportElement(LanguageLevel.forElement(file), name, asName);
        existingImport.add(importElement);
        // May need to add parentheses, trailing comma, etc.
        CodeStyleManager.getInstance(file.getProject()).reformat(existingImport);
        return true;
      }
    }
    addFromImportStatement(file, from, name, asName, priority, anchor);
    return true;
  }

  /**
   * Adds either {@link PyFromImportStatement} or {@link PyImportStatement}
   * to specified target depending on user preferences and whether it's possible to import element via "from" form of import
   * (e.g. consider top level module).
   *
   * @param target  element import is pointing to
   * @param file    file where import will be inserted
   * @param element used to determine where to insert import
   * @see PyCodeInsightSettings#PREFER_FROM_IMPORT
   * @see #addImportStatement
   * @see #addOrUpdateFromImportStatement
   */
  public static void addImport(@NotNull PsiNamedElement target, @NotNull PsiFile file, @NotNull PyElement element) {
    if (target instanceof PsiFileSystemItem) {
      addFileSystemItemImport((PsiFileSystemItem)target, file, element);
      return;
    }

    final String name = target.getName();
    if (name == null) return;

    final PsiFileSystemItem toImport = target.getContainingFile();
    if (toImport == null) return;

    final QualifiedName importPath = QualifiedNameFinder.findCanonicalImportPath(target, element);
    if (importPath == null) return;

    final String path = importPath.toString();
    final ImportPriority priority = getImportPriority(file, toImport);
    if (!PyCodeInsightSettings.getInstance().PREFER_FROM_IMPORT) {
      addImportStatement(file, path, null, priority, element);

      final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(file.getProject());
      element.replace(elementGenerator.createExpressionFromText(LanguageLevel.forElement(target), path + "." + name));
    }
    else {
      addOrUpdateFromImportStatement(file, path, name, null, priority, element);
    }
  }

  private static void addFileSystemItemImport(@NotNull PsiFileSystemItem target, @NotNull PsiFile file, @NotNull PyElement element) {
    final PsiFileSystemItem toImport = target.getParent();
    if (toImport == null) return;

    final QualifiedName importPath = QualifiedNameFinder.findCanonicalImportPath(target, element);
    if (importPath == null) return;

    final ImportPriority priority = getImportPriority(file, toImport);
    if (importPath.getComponentCount() == 1 || !PyCodeInsightSettings.getInstance().PREFER_FROM_IMPORT) {
      final String path = importPath.toString();

      addImportStatement(file, path, null, priority, element);

      final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(file.getProject());
      element.replace(elementGenerator.createExpressionFromText(LanguageLevel.forElement(target), path));
    }
    else {
      addOrUpdateFromImportStatement(file, importPath.removeLastComponent().toString(), target.getName(), null, priority, element);
    }
  }
}
