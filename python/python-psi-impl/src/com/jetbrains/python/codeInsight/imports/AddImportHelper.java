// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.imports;

import static com.jetbrains.python.psi.PyUtil.as;
import static com.jetbrains.python.psi.PyUtil.sure;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDirectoryContainer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiParserFacade;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PythonCodeStyleService;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.documentation.doctest.PyDocstringFile;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.PyImportStatementBase;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.pyi.PyiFile;
import com.jetbrains.python.pyi.PyiUtil;
import com.jetbrains.python.sdk.PythonSdkUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  @NotNull
  private static Comparator<PyImportStatementBase> getImportStatementComparator(@NotNull PsiFile settingsAnchor) {
    return (import1, import2) -> {
      final Comparator<String> stringComparator = getImportTextComparator(settingsAnchor);
      return ContainerUtil.compareLexicographically(getSortNames(import1), getSortNames(import2), Comparator.nullsFirst(stringComparator));
    };
  }

  @NotNull
  public static Comparator<String> getImportTextComparator(@NotNull PsiFile settingsAnchor) {
    return PythonCodeStyleService.getInstance().isOptimizeImportsCaseSensitiveOrder(settingsAnchor) ? String.CASE_INSENSITIVE_ORDER : Comparator.naturalOrder();
  }

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
   * @param settingsAnchor file to use as an anchor to detect settings of Optimize Imports
   * @see ImportPriority
   */
  @NotNull
  public static Comparator<PyImportStatementBase> getSameGroupImportsComparator(@NotNull PsiFile settingsAnchor) {
    if (PythonCodeStyleService.getInstance().isOptimizeImportsSortedByTypeFirst(settingsAnchor)) {
      return IMPORT_TYPE_COMPARATOR.thenComparing(getImportStatementComparator(settingsAnchor));
    }
    else {
      return getImportStatementComparator(settingsAnchor).thenComparing(IMPORT_TYPE_COMPARATOR);
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

    ImportPriorityChoice(@NotNull ImportPriority priority, @NotNull String description) {
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
    return getInsertPosition(file, null, null, null);
  }

  @Nullable
  private static PsiElement getInsertPosition(@NotNull PsiElement insertParent,
                                              @Nullable PsiElement anchor,
                                              @Nullable PyImportStatementBase newImport,
                                              @Nullable ImportPriority priority) {
    PsiElement feeler = ImportLocationHelper.getInstance().getSearchStartPosition(anchor, insertParent);
    if (feeler == null) return null;
    // skip initial comments and whitespace and try to get just below the last import stmt
    boolean skippedOverStatements = false;
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
        skippedOverStatements = true;
      }
      else if (PsiTreeUtil.instanceOf(feeler, PsiWhiteSpace.class, PsiComment.class)) {
        seeker = feeler;
        feeler = feeler.getNextSibling();
      }
      else if (PsiTreeUtil.instanceOf(feeler, OuterLanguageElement.class)) {
        feeler = feeler.getNextSibling();
        seeker = feeler;
      }
      else if (isAssignmentToModuleLevelDunderName(feeler)) {
        if (priority == ImportPriority.FUTURE) {
          seeker = feeler;
          break;
        }
        feeler = feeler.getNextSibling();
        seeker = feeler;
        skippedOverStatements = true;
      }
      // maybe we arrived at the doc comment stmt; skip over it, too
      else if (!skippedOverStatements && !skippedOverDoc && insertParent instanceof PyFile) {
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
      newImport.putCopyableUserData(PythonCodeStyleService.IMPORT_GROUP_BEGIN, true);
    }
    if (priorityBelow != null) {
      // actually not necessary because existing import with higher priority (i.e. lower import group)
      // probably should have IMPORT_GROUP_BEGIN flag already, but we add it anyway just for safety
      if (priorityBelow.compareTo(priority) > 0) {
        importBelow.putCopyableUserData(PythonCodeStyleService.IMPORT_GROUP_BEGIN, true);
      }
      else if (priorityBelow == priority) {
        importBelow.putCopyableUserData(PythonCodeStyleService.IMPORT_GROUP_BEGIN, null);
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
    return getSameGroupImportsComparator(existingImport.getContainingFile()).compare(newImport, existingImport) < 0;
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
      final PsiElement original = PyiUtil.getOriginalElement((PyiFile)resolvedFileOrDir);
      resolvedFileOrDir = ObjectUtils.notNull(as(original, PsiFileSystemItem.class), resolvedFileOrDir);
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
    final Sdk pythonSdk = module != null ? PythonSdkUtil.findPythonSdk(module) : projectRootManager.getProjectSdk();

    if (PythonSdkUtil.isStdLib(vFile, pythonSdk)) {
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
    for (PyImportElement existingImport : existingImports) {
      final String existingName = Objects.toString(existingImport.getImportedQName(), "");
      if (name.equals(existingName) && Objects.equals(asName, existingImport.getAsName())) {
        return false;
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
        insertParent.addBefore(importNodeToInsert, getInsertPosition(insertParent, anchor, importNodeToInsert, priority));
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
        final PsiElement element = insertParent.addBefore(newImport, getInsertPosition(insertParent, anchor, newImport, priority));
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
        insertParent.addBefore(newImport, getInsertPosition(insertParent, anchor, newImport, priority));
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
    final PyFile pyFile = (PyFile)file;
    final List<PyFromImportStatement> existingImports = pyFile.getFromImports();

    int relativeLevel = 0;
    PyRelativeImportData importData = null;
    if (priority == ImportPriority.PROJECT) {
      importData = PyRelativeImportData.fromString(from, pyFile);
      if (importData != null) {
        relativeLevel = importData.getRelativeLevel();
      }
    }

    if (!PythonCodeStyleService.getInstance().isOptimizeImportsAlwaysSplitFromImports(file)) {
      for (PyFromImportStatement existingImport : existingImports) {
        if (existingImport.isStarImport()) {
          continue;
        }
        final String existingSource = Objects.toString(existingImport.getImportSourceQName(), "");

        boolean updateExisting = false;
        final int currentRelativeLevel = existingImport.getRelativeLevel();
        if (currentRelativeLevel == 0) {
          updateExisting = from.equals(existingSource);
        }
        else if (relativeLevel != 0) {
          updateExisting = currentRelativeLevel == relativeLevel && existingSource.equals(importData.getRelativeLocation());
        }

        if (updateExisting) {
          return addNameToFromImportStatement(existingImport, name, asName);
        }
      }
    }

    if (!PyUtil.hasIfNameEqualsMain(pyFile)) {
      final int maxRelativeLevelInFile = StreamEx.of(pyFile.getFromImports())
        .mapToInt(PyFromImportStatement::getRelativeLevel)
        .max()
        .orElse(0);

      if (maxRelativeLevelInFile > 0 && relativeLevel > 0) {
        int maxAllowedDepth = Math.max(maxRelativeLevelInFile, Registry.intValue("python.relative.import.depth"));
        if (maxAllowedDepth >= relativeLevel) {
          from = importData.getLocationWithDots();
        }
      }
    }

    addFromImportStatement(file, from, name, asName, priority, anchor);
    return true;
  }

  /**
   * Adds a new imported name to an existing "from" import statement.
   *
   * @param fromImport import statement to update
   * @param name       new name to import from the same source
   * @param asName     optional alias for a name in its "as" part
   * @return whether the new name was actually added
   */
  public static boolean addNameToFromImportStatement(@NotNull PyFromImportStatement fromImport,
                                                     @NotNull String name,
                                                     @Nullable String asName) {
    final PsiFile file = fromImport.getContainingFile();
    final Comparator<String> nameComparator = getImportTextComparator(file);
    final PythonCodeStyleService pyCodeStyle = PythonCodeStyleService.getInstance();
    final boolean shouldSort = pyCodeStyle.isOptimizeImportsSortImports(file) && pyCodeStyle.isOptimizeImportsSortNamesInFromImports(file);

    PyImportElement followingNameElement = null;
    for (PyImportElement existingNameElement : fromImport.getImportElements()) {
      final String existingName = Objects.toString(existingNameElement.getImportedQName(), "");
      if (name.equals(existingName) && Objects.equals(asName, existingNameElement.getAsName())) {
        return false;
      }
      if (shouldSort && followingNameElement == null && nameComparator.compare(existingName, name) > 0) {
        followingNameElement = existingNameElement;
      }
    }
    final PyElementGenerator generator = PyElementGenerator.getInstance(fromImport.getProject());
    final PyImportElement newNameElement = generator.createImportElement(LanguageLevel.forElement(fromImport), name, asName);
    // addBefore(newNameElement, null) is the same as inserting at the end
    fromImport.addBefore(newNameElement, followingNameElement);
    // May need to add parentheses, trailing comma, etc.
    CodeStyleManager.getInstance(fromImport.getProject()).reformat(fromImport);
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

  public static boolean isAssignmentToModuleLevelDunderName(@Nullable PsiElement element) {
    if (element instanceof PyAssignmentStatement && PyUtil.isTopLevel(element)) {
      PyAssignmentStatement statement = (PyAssignmentStatement)element;
      PyExpression[] targets = statement.getTargets();
      if (targets.length == 1) {
        String name = targets[0].getName();
        return name != null && PyUtil.isSpecialName(name);
      }
    }
    return false;
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
