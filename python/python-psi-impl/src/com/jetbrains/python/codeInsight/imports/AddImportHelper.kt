// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.imports

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.registry.Registry.Companion.intValue
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDirectoryContainer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiParserFacade
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.templateLanguages.OuterLanguageElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.python.PythonCodeStyleService
import com.jetbrains.python.ast.impl.PyUtilCore
import com.jetbrains.python.codeInsight.PyCodeInsightSettings
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.imports.ImportLocationHelper.Companion.getInstance
import com.jetbrains.python.codeInsight.imports.PyNestedClassUtils.findTopLevelClass
import com.jetbrains.python.codeInsight.imports.PyRelativeImportData.Companion.fromString
import com.jetbrains.python.documentation.docstrings.DocStringUtil
import com.jetbrains.python.documentation.doctest.PyDocstringFile
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyImportElement
import com.jetbrains.python.psi.PyImportStatement
import com.jetbrains.python.psi.PyImportStatementBase
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStatement
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.PyBuiltinCache.Companion.getInstance
import com.jetbrains.python.psi.impl.PyCodeFragmentWithHiddenImports
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.resolve.QualifiedNameFinder
import com.jetbrains.python.pyi.PyiFile
import com.jetbrains.python.pyi.PyiUtil
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.skeleton.PySkeletonUtil
import one.util.streamex.StreamEx
import org.jetbrains.annotations.Contract
import java.util.Objects
import kotlin.math.max

/**
 * Does the actual job of adding an import statement into a file.
 */
object AddImportHelper {
  private val LOG = thisLogger()

  // normal imports go first, then "from" imports
  private val IMPORT_TYPE_COMPARATOR = Comparator { import1: PyImportStatementBase, import2: PyImportStatementBase ->
    val firstIsFromImport = if (import1 is PyFromImportStatement) 1 else 0
    val secondIsFromImport = if (import2 is PyFromImportStatement) 1 else 0
    firstIsFromImport - secondIsFromImport
  }

  private fun getImportStatementComparator(settingsAnchor: PsiFile): Comparator<PyImportStatementBase> {
    return Comparator { import1, import2 ->
      val stringComparator = getImportTextComparator(settingsAnchor)
      ContainerUtil.compareLexicographically(
        getSortNames(import1),
        getSortNames(import2),
        Comparator.nullsFirst(stringComparator)
      )
    }
  }

  @JvmStatic
  fun getImportTextComparator(settingsAnchor: PsiFile): Comparator<String> {
    return (if (PythonCodeStyleService.getInstance().isOptimizeImportsCaseSensitiveOrder(settingsAnchor))
      String.CASE_INSENSITIVE_ORDER
    else
      Comparator.naturalOrder()) as Comparator<String>
  }

  private fun getSortNames(importStatement: PyImportStatementBase): List<String> {
    val result: MutableList<String> = ArrayList()
    val fromImport = importStatement as? PyFromImportStatement
    if (fromImport != null) {
      // because of that relative imports go to the end of an import block
      result.add(StringUtil.repeatSymbol('.', fromImport.relativeLevel))
      val source = fromImport.importSourceQName
      result.add(Objects.toString(source, ""))
      if (fromImport.isStarImport) {
        result.add("*")
      }
    }
    else {
      // fake relative level
      result.add("")
    }

    for (importElement in importStatement.importElements) {
      val qualifiedName = importElement.importedQName
      result.add(Objects.toString(qualifiedName, ""))
      result.add((importElement.asName ?: ""))
    }
    return result
  }

  /**
   * Creates and return comparator for import statements that compares them according to the rules specified in the code style settings.
   * It's intended to be used for imports that have the same import priority in order to sort them within the corresponding group.
   *
   * @param settingsAnchor file to use as an anchor to detect settings of Optimize Imports
   * @see ImportPriority
   */
  @JvmStatic
  fun getSameGroupImportsComparator(settingsAnchor: PsiFile): Comparator<PyImportStatementBase> {
    if (PythonCodeStyleService.getInstance().isOptimizeImportsSortedByTypeFirst(settingsAnchor)) {
      return IMPORT_TYPE_COMPARATOR.thenComparing(getImportStatementComparator(settingsAnchor))
    }
    else {
      return getImportStatementComparator(settingsAnchor).thenComparing(IMPORT_TYPE_COMPARATOR)
    }
  }

  private val UNRESOLVED_SYMBOL_PRIORITY = ImportPriority.THIRD_PARTY

  @JvmStatic
  fun addLocalImportStatement(element: PsiElement, name: String, asName: String?) {
    val generator = PyElementGenerator.getInstance(element.project)
    val languageLevel = LanguageLevel.forElement(element)

    val anchor = getLocalInsertPosition(element)
    val parentElement = PyUtil.sure(anchor).parent
    if (parentElement != null) {
      parentElement.addBefore(generator.createImportStatement(languageLevel, name, asName), anchor)
    }
  }

  @JvmStatic
  fun addLocalFromImportStatement(
    element: PsiElement,
    qualifier: String,
    name: String,
    asName: String?,
  ) {
    val generator = PyElementGenerator.getInstance(element.project)
    val languageLevel = LanguageLevel.forElement(element)

    val anchor = getLocalInsertPosition(element)
    val parentElement = PyUtil.sure(anchor).parent
    if (parentElement != null) {
      parentElement.addBefore(generator.createFromImportStatement(languageLevel, qualifier, name, asName), anchor)
    }
  }

  fun getLocalInsertPosition(anchor: PsiElement): PsiElement? {
    return PsiTreeUtil.getParentOfType(anchor, PyStatement::class.java, false)
  }

  /**
   * Returns position in the file after all leading comments, docstring and import statements.
   *
   *
   * Returned PSI element is intended to be used as "anchor" parameter for [PsiElement.addBefore],
   * hence `null` means that element to be inserted will be the first in the file.
   *
   * @param file target file where some new top-level element is going to be inserted
   * @return anchor PSI element as described
   */
  @JvmStatic
  fun getFileInsertPosition(file: PsiFile): PsiElement? {
    return getInsertPosition(file, null, null, null)
  }

  private fun getInsertPosition(
    insertParent: PsiElement,
    anchor: PsiElement?,
    newImport: PyImportStatementBase?,
    priority: ImportPriority?,
  ): PsiElement? {
    var feeler = getInstance().getSearchStartPosition(anchor, insertParent)
    if (feeler == null) return null
    // skip initial comments and whitespace and try to get just below the last import stmt
    var skippedOverStatements = false
    var skippedOverDoc = false
    var seeker: PsiElement? = feeler
    val isInjected = InjectedLanguageManager.getInstance(feeler.project).isInjectedFragment(feeler.containingFile)
    var importAbove: PyImportStatementBase? = null
    var importBelow: PyImportStatementBase? = null
    do {
      if (feeler is PyImportStatementBase && !isInjected) {
        if (priority != null && newImport != null) {
          if (shouldInsertBefore(newImport, feeler, priority)) {
            importBelow = feeler
            break
          }
          else {
            importAbove = feeler
          }
        }
        seeker = feeler
        feeler = feeler.nextSibling
        skippedOverStatements = true
      }
      else if (PsiTreeUtil.instanceOf(feeler, PsiWhiteSpace::class.java, PsiComment::class.java)) {
        seeker = feeler
        feeler = feeler!!.nextSibling
      }
      else if (PsiTreeUtil.instanceOf(feeler, OuterLanguageElement::class.java)) {
        if (skippedOverStatements) {
          break
        }
        feeler = feeler!!.nextSibling
        seeker = feeler
      }
      else if (PyUtilCore.isAssignmentToModuleLevelDunderName(feeler)) {
        if (priority == ImportPriority.FUTURE) {
          seeker = feeler
          break
        }
        feeler = feeler!!.nextSibling
        seeker = feeler
        skippedOverStatements = true
      }
      else if (!skippedOverStatements && !skippedOverDoc && insertParent is PyFile) {
        // this gives the literal; its parent is the expr seeker may have encountered
        val docElem: PsiElement? = DocStringUtil.findDocStringExpression(insertParent as PyElement)
        if (docElem != null && docElem.parent === feeler) {
          feeler = feeler.nextSibling
          seeker = feeler // skip over doc even if there's nothing below it
          skippedOverDoc = true
        }
        else {
          break // not a doc comment, stop on it
        }
      }
      else {
        break // some other statement, stop
      }
    }
    while (feeler != null)
    val priorityAbove = if (importAbove != null) getImportPriority(importAbove) else null
    val priorityBelow = if (importBelow != null) getImportPriority(importBelow) else null
    if (newImport != null && (priorityAbove == null || priorityAbove < priority!!)) {
      newImport.putCopyableUserData(PythonCodeStyleService.IMPORT_GROUP_BEGIN, true)
    }

    if (feeler != null) {
      val anchorComment = getTopmostBoundComment(feeler)
      if (anchorComment != null) {
        seeker = anchorComment
      }
    }

    if (priorityBelow != null) {
      // actually not necessary because existing import with higher priority (i.e. lower import group)
      // probably should have IMPORT_GROUP_BEGIN flag already, but we add it anyway just for safety
      if (priorityBelow > priority!!) {
        importBelow!!.putCopyableUserData(PythonCodeStyleService.IMPORT_GROUP_BEGIN, true)
      }
      else if (priorityBelow == priority) {
        importBelow!!.putCopyableUserData(PythonCodeStyleService.IMPORT_GROUP_BEGIN, null)
      }
    }
    return seeker
  }

  private fun getTopmostBoundComment(element: PsiElement): PsiComment? {
    val commentBlocks = PyPsiUtils.getPrecedingCommentBlocks(element)
    if (commentBlocks.isEmpty()) return null

    val firstBlock = commentBlocks[0]
    val firstComment = firstBlock[0]
    if (firstComment.prevSibling != null) {
      return firstComment
    }

    val lastCommentFirstBlock = firstBlock[firstBlock.size - 1]
    if (PyUtil.isNoinspectionComment(lastCommentFirstBlock)) {
      return lastCommentFirstBlock
    }

    if (commentBlocks.size == 1) return null
    return commentBlocks[1].firstOrNull()
  }

  private fun shouldInsertBefore(
    newImport: PyImportStatementBase?,
    existingImport: PyImportStatementBase,
    priority: ImportPriority,
  ): Boolean {
    val existingImportPriority = getImportPriority(existingImport)
    val byPriority = priority.compareTo(existingImportPriority)
    if (byPriority != 0) {
      return byPriority < 0
    }
    if (newImport == null) {
      return false
    }
    return getSameGroupImportsComparator(existingImport.containingFile).compare(newImport, existingImport) < 0
  }

  @JvmStatic
  fun getImportPriority(importLocation: PsiElement, toImport: PsiFileSystemItem): ImportPriority {
    val choice = getImportPriorityWithReason(importLocation, toImport)
    LOG.debug(
      String.format(
        "Import group for %s at %s is %s: %s", toImport, importLocation.containingFile,
        choice.priority, choice.description
      )
    )
    return choice.priority
  }

  fun getImportPriority(importStatement: PyImportStatementBase): ImportPriority {
    val choice = getImportPriorityWithReason(importStatement)
    LOG.debug(String.format("Import group for '%s' is %s: %s", importStatement.text, choice.priority, choice.description))
    return choice.priority
  }

  @JvmStatic
  fun getImportPriorityWithReason(importStatement: PyImportStatementBase): ImportPriorityChoice {
    val resolved: PsiElement?
    val resolveAnchor: PsiElement?
    if (importStatement is PyFromImportStatement) {
      if (importStatement.isFromFuture) {
        return ImportPriorityChoice(ImportPriority.FUTURE, "import from __future__")
      }
      if (importStatement.relativeLevel > 0) {
        return ImportPriorityChoice(ImportPriority.PROJECT, "explicit relative import")
      }
      resolveAnchor = importStatement.importSource
      resolved = importStatement.resolveImportSource()
    }
    else {
      val firstImportElement = importStatement.importElements.firstOrNull()
      if (firstImportElement == null) {
        return ImportPriorityChoice(UNRESOLVED_SYMBOL_PRIORITY, "incomplete import statement")
      }
      resolveAnchor = firstImportElement
      resolved = firstImportElement.resolve()
    }
    if (resolved == null) {
      return ImportPriorityChoice(
        UNRESOLVED_SYMBOL_PRIORITY,
        if (resolveAnchor == null) "incomplete import statement" else resolveAnchor.text + " is unresolved"
      )
    }

    var resolvedFileOrDir: PsiFileSystemItem?
    when (resolved) {
      is PsiDirectory -> resolvedFileOrDir = resolved as PsiFileSystemItem
      is PsiDirectoryContainer -> resolvedFileOrDir = resolved.directories.firstOrNull()
      else -> resolvedFileOrDir = resolved.containingFile
    }

    if (resolvedFileOrDir is PyiFile) {
      val original = PyiUtil.getOriginalElement(resolvedFileOrDir)
      resolvedFileOrDir = (original as? PsiFileSystemItem) ?: resolvedFileOrDir
    }

    if (resolvedFileOrDir == null) {
      return ImportPriorityChoice(UNRESOLVED_SYMBOL_PRIORITY, "$resolved is not a file or directory")
    }

    return getImportPriorityWithReason(importStatement, resolvedFileOrDir)
  }

  fun getImportPriorityWithReason(
    importLocation: PsiElement,
    toImport: PsiFileSystemItem,
  ): ImportPriorityChoice {
    val vFile = toImport.virtualFile
    if (vFile == null) {
      return ImportPriorityChoice(UNRESOLVED_SYMBOL_PRIORITY, "$toImport doesn't have an associated virtual file")
    }
    val projectRootManager = ProjectRootManager.getInstance(toImport.project)
    val fileIndex = projectRootManager.fileIndex
    if (fileIndex.isInContent(vFile) && !fileIndex.isInLibraryClasses(vFile)) {
      return ImportPriorityChoice(
        ImportPriority.PROJECT,
        "$vFile belongs to the project and not under interpreter paths"
      )
    }
    val module = ModuleUtilCore.findModuleForPsiElement(importLocation)
    val pythonSdk = if (module != null) PythonSdkUtil.findPythonSdk(module) else projectRootManager.projectSdk

    if (PySkeletonUtil.isStdLib(vFile, pythonSdk)) {
      return ImportPriorityChoice(
        ImportPriority.BUILTIN, vFile.toString() + " is either in lib but not under site-packages," +
                                " or belongs to the root of skeletons," +
                                " or is a .pyi stub definition for stdlib module"
      )
    }
    else {
      return ImportPriorityChoice(
        ImportPriority.THIRD_PARTY, if (pythonSdk == null)
          "SDK for $vFile isn't found"
        else
          "Fall back value for $vFile"
      )
    }
  }

  /**
   * Adds an import statement, if it doesn't exist yet, presumably below all other initial imports in the file.
   *
   * @param file         where to operate
   * @param name         which to import (qualified is OK)
   * @param asName       optional name for 'as' clause
   * @param anchor       place where the imported name was used. It will be used to determine proper block where new import should be
   * inserted, e.g. inside conditional block or try/except statement. Also if anchor is another import statement,
   * new import statement will be inserted right after it.
   * @param insertBefore import statement should be inserted right before this element. If null, the position will be chosen automatically.
   * @return whether import statement was actually added
   */
  @JvmStatic
  @JvmOverloads
  fun addImportStatement(
    file: PsiFile,
    name: String,
    asName: String?,
    priority: ImportPriority?,
    anchor: PsiElement?,
    insertBefore: PsiElement? = null,
  ): Boolean {
    if (file !is PyFile) {
      return false
    }
    val existingImports = file.importTargets
    val importsAllowedToReuse = getImportsAllowedToReuse(insertBefore)
    for (existingImport in existingImports) {
      if (importsAllowedToReuse != null && !importsAllowedToReuse.contains(existingImport.containingImportStatement)) {
        continue
      }

      val existingName = Objects.toString(existingImport.importedQName, "")
      if (name == existingName && asName == existingImport.asName) {
        return false
      }
    }

    val generator = PyElementGenerator.getInstance(file.project)
    val languageLevel = LanguageLevel.forElement(file)
    val importNodeToInsert = generator.createImportStatement(languageLevel, name, asName)

    if (file is PyCodeFragmentWithHiddenImports) {
      file.addImports(mutableListOf(importNodeToInsert))
      return true
    }

    val insertParent: PsiElement
    if (insertBefore == null || insertBefore.parent == null) {
      val importStatement = PsiTreeUtil.getParentOfType(anchor, PyImportStatementBase::class.java, false)
      insertParent =
        if (importStatement != null && importStatement.containingFile === file) importStatement.parent else file
    }
    else {
      insertParent = insertBefore.parent
    }
    try {
      if (anchor is PyImportStatementBase) {
        insertParent.addAfter(importNodeToInsert, anchor)
      }
      else {
        val position: PsiElement?
        if (insertBefore == null) {
          position = getInsertPosition(insertParent, anchor, importNodeToInsert, priority)
        }
        else {
          position = insertBefore
        }
        insertParent.addBefore(importNodeToInsert, position)
      }
    }
    catch (e: IncorrectOperationException) {
      LOG.error(e)
    }
    return true
  }

  /**
   * Adds a new [PyFromImportStatement] statement within other top-level imports or as specified by anchor.
   *
   * @param file         where to operate
   * @param from         import source (reference after `from` keyword)
   * @param name         imported name (identifier after `import` keyword)
   * @param asName       optional alias (identifier after `as` keyword)
   * @param anchor       place where the imported name was used. It will be used to determine proper block where new import should be
   * inserted, e.g. inside conditional block or try/except statement. Also if anchor is another import statement,
   * new import statement will be inserted right after it.
   * @param insertBefore import statement should be inserted right before this element. If null, the position will be chosen automatically.
   * @see .addOrUpdateFromImportStatement
   */
  @JvmStatic
  @JvmOverloads
  fun addFromImportStatement(
    file: PsiFile,
    from: String,
    name: String,
    asName: String?,
    priority: ImportPriority?,
    anchor: PsiElement?,
    insertBefore: PsiElement? = null,
  ) {
    val generator = PyElementGenerator.getInstance(file.project)
    val languageLevel = LanguageLevel.forElement(file)
    val newImport = generator.createFromImportStatement(languageLevel, from, name, asName)
    addFromImportStatement(file, newImport, priority, anchor, insertBefore)
  }

  /**
   * Adds a new [PyFromImportStatement] statement within other top-level imports or as specified by anchor.
   *
   * @param file         where to operate
   * @param newImport    new "from import" statement to insert. It may be generated, because it won't be used for resolving anyway.
   * You might want to use overloaded version of this method to generate such statement automatically.
   * @param anchor       place where the imported name was used. It will be used to determine proper block where new import should be
   * inserted, e.g. inside conditional block or try/except statement. Also if anchor is another import statement,
   * new import statement will be inserted right after it.
   * @param insertBefore import statement should be inserted right before this element. If null, the position will be chosen automatically.
   * @see .addFromImportStatement
   */
  @JvmStatic
  @JvmOverloads
  fun addFromImportStatement(
    file: PsiFile,
    newImport: PyFromImportStatement,
    priority: ImportPriority?,
    anchor: PsiElement?,
    insertBefore: PsiElement? = null,
  ) {
    if (file is PyCodeFragmentWithHiddenImports) {
      file.addImports(mutableListOf(newImport))
      return
    }
    try {
      val parentImport = PsiTreeUtil.getParentOfType(anchor, PyImportStatementBase::class.java, false)
      val manager = InjectedLanguageManager.getInstance(file.project)
      val injectionHost = manager.getInjectionHost(file)
      val insideDoctest =
        file is PyDocstringFile && injectionHost != null && DocStringUtil.getParentDefinitionDocString(injectionHost) === injectionHost

      val insertParent: PsiElement
      if (insertBefore != null && insertBefore.parent != null) {
        insertParent = insertBefore.parent
      }
      else if (parentImport != null && parentImport.containingFile === file) {
        insertParent = parentImport.parent
      }
      else if (injectionHost != null && !insideDoctest) {
        insertParent = manager.getTopLevelFile(file)
      }
      else {
        insertParent = file
      }

      if (insideDoctest) {
        val element = insertParent.addBefore(newImport, getInsertPosition(insertParent, anchor, newImport, priority))
        var whitespace = element.nextSibling
        if (whitespace !is PsiWhiteSpace) {
          whitespace = PsiParserFacade.getInstance(file.project).createWhiteSpaceFromText("  >>> ")
        }
        insertParent.addBefore(whitespace, element)
      }
      else if (anchor is PyImportStatementBase) {
        insertParent.addAfter(newImport, anchor)
      }
      else {
        val position: PsiElement?
        if (insertBefore == null) {
          position = getInsertPosition(insertParent, anchor, newImport, priority)
        }
        else {
          position = insertBefore
        }
        insertParent.addBefore(newImport, position)
      }
    }
    catch (e: IncorrectOperationException) {
      LOG.error(e)
    }
  }

  /**
   * Adds new [PyFromImportStatement] in file or append [PyImportElement] to
   * existing from import statement.
   *
   * @param file         module where import will be added
   * @param from         import source (reference after `from` keyword)
   * @param name         imported name (identifier after `import` keyword)
   * @param asName       optional alias (identifier after `as` keyword)
   * @param priority     optional import priority used to sort imports
   * @param anchor       place where the imported name was used. It will be used to determine proper block where new import should be
   * inserted, e.g. inside conditional block or try/except statement. Also if anchor is another import statement,
   * new import statement will be inserted right after it.
   * @param insertBefore import statement should be inserted right before this element. However, if it aims at an insertion statement in
   * a group of inserts, a better insertion point belonging the group may be chosen, and it can be after the specified
   * node. If null, the position will be chosen automatically.
   * @return whether import was actually added
   * @see .addFromImportStatement
   */
  @JvmStatic
  @JvmOverloads
  fun addOrUpdateFromImportStatement(
    file: PsiFile,
    from: String,
    name: String,
    asName: String?,
    priority: ImportPriority?,
    anchor: PsiElement?,
    insertBefore: PsiElement? = null,
  ): Boolean {
    var from = from
    val pyFile = file as PyFile
    val existingImports = pyFile.fromImports

    var relativeLevel = 0
    var importData: PyRelativeImportData? = null
    if (priority == ImportPriority.PROJECT) {
      importData = fromString(from, pyFile)
      if (importData != null) {
        relativeLevel = importData.relativeLevel
      }
    }

    val importsAllowedToReuse = getImportsAllowedToReuse(insertBefore)

    if (!PythonCodeStyleService.getInstance().isOptimizeImportsAlwaysSplitFromImports(file)) {
      for (existingImport in existingImports) {
        if (importsAllowedToReuse != null && !importsAllowedToReuse.contains(existingImport)) {
          continue
        }
        if (existingImport.isStarImport) {
          continue
        }
        val existingSource = Objects.toString(existingImport.importSourceQName, "")

        var updateExisting = false
        val currentRelativeLevel = existingImport.relativeLevel
        if (currentRelativeLevel == 0) {
          updateExisting = from == existingSource
        }
        else if (relativeLevel != 0) {
          updateExisting = currentRelativeLevel == relativeLevel && existingSource == importData!!.relativeLocation
        }

        if (updateExisting) {
          return addNameToFromImportStatement(existingImport, name, asName)
        }
      }
    }

    if (!PyUtil.hasIfNameEqualsMain(pyFile)) {
      val maxRelativeLevelInFile = StreamEx.of(pyFile.fromImports)
        .mapToInt { obj: PyFromImportStatement? -> obj!!.relativeLevel }
        .max()
        .orElse(0)

      if (maxRelativeLevelInFile > 0 && relativeLevel > 0) {
        val maxAllowedDepth = max(maxRelativeLevelInFile, intValue("python.relative.import.depth"))
        if (maxAllowedDepth >= relativeLevel) {
          from = importData!!.locationWithDots
        }
      }
    }

    addFromImportStatement(file, from, name, asName, priority, anchor, insertBefore)
    return true
  }

  @Contract("null -> null; !null -> !null")
  private fun getImportsAllowedToReuse(importBlockStart: PsiElement?): MutableList<PyImportStatementBase?>? {
    if (importBlockStart == null) return null

    val importsAllowedToReuse: MutableList<PyImportStatementBase?> = ArrayList()
    var candidate: PsiElement? = importBlockStart
    while (candidate is PyImportStatementBase) {
      importsAllowedToReuse.add(candidate)
      candidate = PyPsiUtils.getNextNonWhitespaceSibling(candidate)
    }
    return importsAllowedToReuse
  }

  /**
   * Adds a new imported name to an existing "from" import statement.
   *
   * @param fromImport import statement to update
   * @param name       new name to import from the same source
   * @param asName     optional alias for a name in its "as" part
   * @return whether the new name was actually added
   */
  @JvmStatic
  fun addNameToFromImportStatement(
    fromImport: PyFromImportStatement,
    name: String,
    asName: String?,
  ): Boolean {
    val file = fromImport.containingFile
    val nameComparator = getImportTextComparator(file)
    val pyCodeStyle = PythonCodeStyleService.getInstance()
    val shouldSort = pyCodeStyle.isOptimizeImportsSortImports(file) && pyCodeStyle.isOptimizeImportsSortNamesInFromImports(file)

    var followingNameElement: PyImportElement? = null
    for (existingNameElement in fromImport.importElements) {
      val existingName = Objects.toString(existingNameElement.importedQName, "")
      if (name == existingName && asName == existingNameElement.asName) {
        return false
      }
      if (shouldSort && followingNameElement == null && nameComparator.compare(existingName, name) > 0) {
        followingNameElement = existingNameElement
      }
    }
    val generator = PyElementGenerator.getInstance(fromImport.project)
    val newNameElement = generator.createImportElement(LanguageLevel.forElement(fromImport), name, asName)
    // addBefore(newNameElement, null) is the same as inserting at the end
    fromImport.addBefore(newNameElement, followingNameElement)
    // May need to add parentheses, trailing comma, etc.
    CodeStyleManager.getInstance(fromImport.project).reformat(fromImport)
    return true
  }

  /**
   * Adds either [PyFromImportStatement] or [PyImportStatement]
   * to specified target depending on user preferences and whether it's possible to import element via "from" form of import
   * (e.g. consider top level module).
   *
   * @param target  element import is pointing to
   * @param file    file where import will be inserted
   * @param element used to determine where to insert import
   * @see PyCodeInsightSettings.PREFER_FROM_IMPORT
   *
   * @see .addImportStatement
   *
   * @see .addOrUpdateFromImportStatement
   */
  @JvmStatic
  fun addImport(target: PsiNamedElement, file: PsiFile, element: PyElement) {
    if (target.containingFile == file) return
    if (getInstance(element).isBuiltin(target)) return

    if (target is PsiFileSystemItem) {
      addFileSystemItemImport(target, file, element)
      return
    }

    // If target is a class attribute or nested class, import the top-level containing class
    var elementToImport = target
    val parent = ScopeUtil.getScopeOwner(target)
    if (parent is PyClass) {
      elementToImport = parent
    }

    // Walk up to the top-level class if elementToImport is itself a nested class
    var nestedQualifierPrefix: String? = null
    if (elementToImport is PyClass && !PyUtil.isTopLevel(elementToImport)) {
      val topLevel = findTopLevelClass(elementToImport)
      if (topLevel != null) {
        nestedQualifierPrefix = PyNestedClassUtils.buildQualifiedName(elementToImport, topLevel)
        elementToImport = topLevel
      }
    }

    val name = elementToImport.name
    if (name == null) return

    val toImport: PsiFileSystemItem? = elementToImport.containingFile
    if (toImport == null) return

    // Check if the target is accessible via an already-imported module
    if (qualifyViaExistingImport(toImport, name, nestedQualifierPrefix, file, element)) {
      return
    }

    val importPath = QualifiedNameFinder.findCanonicalImportPath(elementToImport, element)
    if (importPath == null) return

    val path = importPath.toString()
    val priority = getImportPriority(file, toImport)

    if (!PyCodeInsightSettings.getInstance().PREFER_FROM_IMPORT) {
      addImportStatement(file, path, null, priority, element)

      val elementGenerator = PyElementGenerator.getInstance(file.project)
      val refText = nestedQualifierPrefix ?: name
      element.replace(elementGenerator.createExpressionFromText(LanguageLevel.forElement(elementToImport), "$path.$refText"))
    }
    else {
      addOrUpdateFromImportStatement(file, path, name, null, priority, element)
      if (nestedQualifierPrefix != null) {
        // Rewrite the qualifier reference to use the qualified nested class path, e.g. Inner -> Outer.Inner
        // The element might be a qualified expression like Inner.do, so we need to find the unresolved
        // qualifier that refers to the nested class and replace it with the full nesting chain.
        var toRewrite = element
        if (element is PyReferenceExpression) {
          val qualifier = element.qualifier
          if (qualifier is PyReferenceExpression) {
            toRewrite = qualifier
          }
        }
        val elementGenerator = PyElementGenerator.getInstance(file.project)
        toRewrite.replace(
          elementGenerator.createExpressionFromText(
            LanguageLevel.forElement(elementToImport),
            nestedQualifierPrefix
          )
        )
      }
    }
  }

  private fun addFileSystemItemImport(target: PsiFileSystemItem, file: PsiFile, element: PyElement) {
    val toImport = target.parent
    if (toImport == null) return

    val importPath = QualifiedNameFinder.findCanonicalImportPath(target, element)
    if (importPath == null) return

    val priority = getImportPriority(file, toImport)
    if (importPath.componentCount == 1 || !PyCodeInsightSettings.getInstance().PREFER_FROM_IMPORT) {
      val path = importPath.toString()

      addImportStatement(file, path, null, priority, element)

      val elementGenerator = PyElementGenerator.getInstance(file.project)
      element.replace(elementGenerator.createExpressionFromText(LanguageLevel.forElement(target), path))
    }
    else {
      val importedName: String = checkNotNull(importPath.lastComponent)
      val importSource = importPath.removeLastComponent().toString()
      addOrUpdateFromImportStatement(file, importSource, importedName, null, priority, element)
    }
  }

  private fun qualifyViaExistingImport(
    toImport: PsiFileSystemItem,
    importedName: String,
    nestedQualifierPrefix: String?,
    file: PsiFile,
    element: PyElement,
  ): Boolean {
    if (file !is PyFile) return false

    if (processFromImports(toImport, importedName, nestedQualifierPrefix, element, file) ||
        processRegularImports(toImport, importedName, nestedQualifierPrefix, element, file)
    ) {
      return true
    }

    return false
  }

  // Check regular imports: "import pkg.src" where pkg.src resolves to the target's module
  private fun processRegularImports(
    toImport: PsiFileSystemItem,
    importedName: String,
    nestedQualifierPrefix: String?,
    element: PyElement,
    pyFile: PyFile,
  ): Boolean {
    for (importElement in pyFile.importTargets) {
      val resolved = importElement.resolve()
      val resolvedFile = PyUtil.turnDirIntoInit(resolved) as? PsiFile
      if (resolvedFile == null || resolvedFile != toImport) continue

      val importedQName = importElement.importedQName
      if (importedQName == null) continue

      val prefix = if (importElement.asName != null)
        importElement.visibleName
      else
        importedQName.toString()
      if (prefix == null) continue

      val qualifiedRef = if (nestedQualifierPrefix != null)
        "$prefix.$nestedQualifierPrefix"
      else
        "$prefix.$importedName"
      replaceQualifier(element, qualifiedRef)
      return true
    }
    return false
  }

  // Check from-imports: "from pkg import src" where src resolves to the target's module
  private fun processFromImports(
    toImport: PsiFileSystemItem,
    importedName: String,
    nestedQualifierPrefix: String?,
    element: PyElement,
    pyFile: PyFile,
  ): Boolean {
    for (fromImport in pyFile.fromImports) {
      if (fromImport.isStarImport) continue
      for (importElement in fromImport.importElements) {
        val resolved = importElement.resolve()
        val resolvedFile = PyUtil.turnDirIntoInit(resolved) as? PsiFile
        if (resolvedFile == null || resolvedFile != toImport) continue

        val visibleName = importElement.visibleName
        if (visibleName == null) continue

        val qualifiedRef = if (nestedQualifierPrefix != null)
          "$visibleName.$nestedQualifierPrefix"
        else
          "$visibleName.$importedName"
        replaceQualifier(element, qualifiedRef)
        return true
      }
    }
    return false
  }

  private fun replaceQualifier(element: PyElement, qualifiedRef: String) {
    var toRewrite = element
    if (element is PyReferenceExpression) {
      val qualifier = element.qualifier
      if (qualifier is PyReferenceExpression) {
        toRewrite = qualifier
      }
    }
    val gen = PyElementGenerator.getInstance(element.project)
    toRewrite.replace(gen.createExpressionFromText(LanguageLevel.forElement(element), qualifiedRef))
  }

  enum class ImportPriority {
    FUTURE,
    BUILTIN,
    THIRD_PARTY,
    PROJECT
  }

  class ImportPriorityChoice(val priority: ImportPriority, val description: String)
}
