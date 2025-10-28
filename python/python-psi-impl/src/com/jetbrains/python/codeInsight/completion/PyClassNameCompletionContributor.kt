// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry.Companion.intValue
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.QualifiedName
import com.intellij.psi.util.findParentOfType
import com.intellij.util.ArrayUtil
import com.intellij.util.ThrowableRunnable
import com.intellij.util.TimeoutUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.PyCodeInsightSettings
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.QualifiedNameFinder
import com.jetbrains.python.psi.resolve.QualifiedNameFinder.QualifiedNameBasedScope
import com.jetbrains.python.psi.resolve.fromFoothold
import com.jetbrains.python.psi.resolve.resolveQualifiedName
import com.jetbrains.python.psi.search.PySearchUtilBase
import com.jetbrains.python.psi.stubs.PyExportedModuleAttributeIndex
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.pyi.PyiFileType
import java.util.function.LongConsumer

/**
 * Adds completion variants for Python classes, functions and variables.
 */
class PyClassNameCompletionContributor : CompletionContributor(), DumbAware {
  init {
    if (TRACING_WITH_SPUTNIK_ENABLED) {
      println("\u0001hr('Importable names completion')")
    }
  }

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (!shouldDoCompletion(parameters, result)) return
    
    result.restartCompletionWhenNothingMatches()
    val remainingResults = result.runRemainingContributors(parameters, true)
    if (parameters.isExtendedCompletion || remainingResults.isEmpty() || containsOnlyElementUnderTheCaret(remainingResults, parameters)) {
      fillCompletionVariantsImpl(parameters, result)
    }
  }

  private fun shouldDoCompletion(parameters: CompletionParameters, result: CompletionResultSet): Boolean {
    if (result.prefixMatcher.prefix.isEmpty()) {
      result.restartCompletionOnPrefixChange(StandardPatterns.string().longerThan(0))
      return false
    }

    val element = parameters.position
    val parent = element.parent
    if (parent is PyReferenceExpression && parent.isQualified) {
      return false
    }
    if (parent is PyStringLiteralExpression) {
      val prefix = parent.text.substring(0, parameters.offset - parent.textRange.startOffset)
      if (prefix.contains(".")) {
        return false
      }
    }
    return element.findParentOfType<PyImportStatementBase>() == null
  }


  private fun fillCompletionVariantsImpl(parameters: CompletionParameters, result: CompletionResultSet) {
    val isExtendedCompletion = parameters.isExtendedCompletion
    if (!PyCodeInsightSettings.getInstance().INCLUDE_IMPORTABLE_NAMES_IN_BASIC_COMPLETION && !isExtendedCompletion) {
      return
    }
    val originalFile = parameters.originalFile
    val position = parameters.position
    val refExpr = position.parent as? PyReferenceExpression
    val targetExpr = position.parent as? PyTargetExpression
    val originalPosition = parameters.originalPosition
    // In cases like `fo<caret>o = 42` the target expression gets split by the trailing space at the end of DUMMY_IDENTIFIER as
    // `foIntellijIdeaRulezzz o = 42`, so in the copied file we're still completing inside a standalone reference expression.
    val originallyInsideTarget = originalPosition != null && originalPosition.parent is PyTargetExpression
    val insideUnqualifiedReference = refExpr != null && !refExpr.isQualified && !originallyInsideTarget
    val insidePattern = targetExpr != null && position.parent.parent is PyCapturePattern
    val insideStringLiteralInExtendedCompletion = position is PyStringElement && isExtendedCompletion
    if (!(insideUnqualifiedReference || insidePattern || insideStringLiteralInExtendedCompletion)) {
      return
    }

    // Directly inside the class body scope, it's rarely needed to have expression statements
    // TODO apply the same logic for completion of importable module and package names
    if (refExpr != null && (isDirectlyInsideClassBody(refExpr) || isInsideErrorElement(refExpr))) {
      return
    }
    val project = originalFile.getProject()
    val typeEvalContext = TypeEvalContext.codeCompletion(project, originalFile)
    val maxVariants = intValue("ide.completion.variant.limit")
    val counters = Counters()
    val stubIndex = StubIndex.getInstance()
    TimeoutUtil.run<RuntimeException>(ThrowableRunnable {
      val scope = createScope(originalFile)
      val alreadySuggested: MutableSet<QualifiedName> = HashSet()
      forEachPublicNameFromIndex(scope) { elementName: String ->
        ProgressManager.checkCanceled()
        counters.scannedNames++
        if (!result.prefixMatcher.isStartMatch(elementName)) return@forEachPublicNameFromIndex true
        stubIndex.processElements(PyExportedModuleAttributeIndex.KEY, elementName, project, scope,
                                  PyElement::class.java) { exported ->
          ProgressManager.checkCanceled()
          val name = exported.getName()
          if (name == null) return@processElements true
          val fqn: QualifiedName = getFullyQualifiedName(exported)
          if (!isApplicableInInsertionContext(exported, fqn, position, typeEvalContext)) {
            counters.notApplicableInContextNames++
            return@processElements true
          }
          if (alreadySuggested.add(fqn)) {
            if (isPrivateDefinition(fqn, exported, originalFile)) {
              counters.privateNames++
              return@processElements true
            }
            val lookupElement = LookupElementBuilder
              .createWithSmartPointer(name, exported)
              .withIcon(exported.getIcon(0))
              .withExpensiveRenderer(object : LookupElementRenderer<LookupElement>() {
                override fun renderElement(element: LookupElement, presentation: LookupElementPresentation) {
                  presentation.setItemText(element.getLookupString())
                  presentation.setIcon(exported.getIcon(0))
                  val importPath = QualifiedNameFinder.findCanonicalImportPath(exported, originalFile)
                  if (importPath == null) return
                  presentation.typeText = importPath.toString()
                }
              })
              .withInsertHandler(getInsertHandler(exported, position, typeEvalContext))
            result.addElement(PrioritizedLookupElement.withPriority(lookupElement, PythonCompletionWeigher.NOT_IMPORTED_MODULE_WEIGHT.toDouble()))
            counters.totalVariants++
            if (counters.totalVariants >= maxVariants) {
              return@processElements false
            }
          }
          true
        }
      }
    }, LongConsumer { duration ->
      LOG.debug("$counters computed for prefix '${result.prefixMatcher.prefix}' in $duration ms")
      if (TRACING_WITH_SPUTNIK_ENABLED) {
        println("\u0001h('Importable names completion','${(duration / 10) * 10}')")
        println("\u0001Hi($duration)")
      }
    })
  }

  private fun getInsertHandler(
    exported: PyElement,
    position: PsiElement,
    typeEvalContext: TypeEvalContext
  ): InsertHandler<LookupElement> {
    if (position.getParent() is PyStringLiteralExpression) {
      if (isInsideAllInInitPy(position)) {
        return InsertHandlers.importingInsertHandler
      }
      return InsertHandlers.stringLiteralInsertHandler
    }
    else if (PyParameterizedTypeInsertHandler.isCompletingParameterizedType(exported, position, typeEvalContext)) {
      return InsertHandlers.genericTypeInsertHandler
    }
    else if (exported is PyFunction && position.getParent().getParent() !is PyDecorator) {
      return InsertHandlers.functionInsertHandler
    }
    return InsertHandlers.importingInsertHandler
  }

  private fun containsOnlyElementUnderTheCaret(
    remainingResults: MutableSet<CompletionResult>,
    parameters: CompletionParameters
  ): Boolean {
    val position = parameters.originalPosition
    if (remainingResults.size == 1 && position != null) {
      val lookup = remainingResults.first()
      return lookup.lookupElement.getLookupString() == position.getText()
    }
    return false
  }

  private fun forEachPublicNameFromIndex(scope: GlobalSearchScope, processor: (String) -> Boolean) {
    val stubIndex = StubIndex.getInstance()
    if (!RECURSIVE_INDEX_ACCESS_ALLOWED) {
      val project = scope.project!!
      val manager = CachedValuesManager.getManager(project)

      val cachedAllNames = manager.getCachedValue(project, CachedValueProvider {
        val index = StubIndex.getInstance()
        val keys = index.getAllKeys(PyExportedModuleAttributeIndex.KEY, project)
        val modificationTracker = index.getPerFileElementTypeModificationTracker(PyFileElementType.INSTANCE)
        CachedValueProvider.Result.create<Collection<String>>(keys, modificationTracker)
      })

      for (allKey in cachedAllNames) {
        if (!processor.invoke(allKey)) {
          return
        }
      }
    }
    else {
      stubIndex.processAllKeys(PyExportedModuleAttributeIndex.KEY, processor, scope)
    }
  }

  private fun isApplicableInInsertionContext(
    definition: PyElement,
    fqn: QualifiedName, position: PsiElement,
    context: TypeEvalContext
  ): Boolean {
    if (PyTypingTypeProvider.isInsideTypeHint(position, context)) {
      // Not all names from typing.py are defined as classes
      return (definition is PyClass ||
              isSuitableTypeAlias(definition, context) ||
              ArrayUtil.contains(fqn.getFirstComponent(), "typing", "typing_extensions"))
    }
    if (position.findParentOfType<PyPattern>() != null) {
      return definition is PyClass
    }
    return true
  }

  private fun isSuitableTypeAlias(element: PyElement, context: TypeEvalContext): Boolean {
    if (element is PyTargetExpression && element.getParent() is PyAssignmentStatement) {
      if (PyTypingTypeProvider.isExplicitTypeAlias(element.parent as PyAssignmentStatement, context)) return true
    }
    return element is PyTypeAliasStatement
  }
  
  private fun isInsideErrorElement(referenceExpression: PyReferenceExpression): Boolean {
    return referenceExpression.findParentOfType<PsiErrorElement>() != null
  }

  private fun isDirectlyInsideClassBody(referenceExpression: PyReferenceExpression): Boolean {
    return referenceExpression.parent is PyExpressionStatement &&
           ScopeUtil.getScopeOwner(referenceExpression.parent) is PyClass
  }

  private fun getFullyQualifiedName(exported: PyElement): QualifiedName {
    val shortName = exported.getName() ?: ""
    val qualifiedName = if (exported is PyQualifiedNameOwner) exported.getQualifiedName() else null
    return QualifiedName.fromDottedString(qualifiedName ?: shortName)
  }

  private fun isPrivateDefinition(fqn: QualifiedName, exported: PyElement, originalFile: PsiFile?): Boolean {
    if (containsPrivateComponents(fqn)) {
      val importPath = QualifiedNameFinder.findCanonicalImportPath(exported, originalFile)
      return importPath != null && containsPrivateComponents(importPath)
    }
    return false
  }

  private fun containsPrivateComponents(fqn: QualifiedName): Boolean {
    return fqn.components.any { it.startsWith("_") }
  }

  private fun createScope(originalFile: PsiFile): GlobalSearchScope {
    class HavingLegalImportPathScope(project: Project) : QualifiedNameBasedScope(project) {
      override fun containsQualifiedNameInRoot(root: VirtualFile, qName: QualifiedName): Boolean {
        return qName.components.all { PyNames.isIdentifier(it) } && qName != QualifiedName.fromComponents("__future__")
      }
    }

    val project = originalFile.getProject()
    val pyiStubsScope = GlobalSearchScope.getScopeRestrictedByFileTypes(GlobalSearchScope.everythingScope(project), PyiFileType.INSTANCE)
    return PySearchUtilBase.defaultSuggestionScope(originalFile)
      .intersectWith(GlobalSearchScope.notScope(pyiStubsScope))
      .intersectWith(GlobalSearchScope.notScope(GlobalSearchScope.fileScope(
        originalFile))) // Some types in typing.py are defined as functions, causing inserting them with parentheses. It's better to rely on typing.pyi.
      .intersectWith(GlobalSearchScope.notScope(fileScope("typing", originalFile, false)))
      .uniteWith(fileScope("typing", originalFile, true))
      .intersectWith(HavingLegalImportPathScope(project))
  }

  private fun fileScope(fqn: String, anchor: PsiFile, pyiStub: Boolean): GlobalSearchScope {
    val context = if (pyiStub) fromFoothold(anchor) else fromFoothold(anchor).copyWithoutStubs()
    val files = resolveQualifiedName(QualifiedName.fromDottedString(fqn), context).filterIsInstance<PsiFile>()
    if (files.isEmpty()) return GlobalSearchScope.EMPTY_SCOPE
    return GlobalSearchScope.filesWithLibrariesScope(anchor.getProject(), files.map { it.getVirtualFile() })
  }

  private fun isInsideAllInInitPy(position: PsiElement): Boolean {
    val originalFile = position.getContainingFile()
    if (originalFile == null) {
      return false
    }

    if (PyNames.INIT_DOT_PY != originalFile.getName()) {
      return false
    }

    val assignment = position.findParentOfType<PyAssignmentStatement>()
    if (assignment == null) {
      return false
    }

    val targets = assignment.getTargets()
    if (targets.size != 1) {
      return false
    }

    val target = targets[0]
    if (target !is PyTargetExpression) {
      return false
    }

    return PyNames.ALL == target.getName()
  }

  private data class Counters(
    var scannedNames: Int = 0,
    var privateNames: Int = 0,
    var notApplicableInContextNames: Int = 0,
    var totalVariants: Int = 0,
  )

  companion object {
    // See https://plugins.jetbrains.com/plugin/18465-sputnik
    private const val TRACING_WITH_SPUTNIK_ENABLED = false
    private val LOG = Logger.getInstance(PyClassNameCompletionContributor::class.java)

    // See PY-73964, IJPL-265
    private const val RECURSIVE_INDEX_ACCESS_ALLOWED = false
  }
  
  object InsertHandlers {
    internal val importingInsertHandler: InsertHandler<LookupElement> = InsertHandler { context, item ->
      addImportForLookupElement(context, item, context.tailOffset - 1)
    }

    internal val functionInsertHandler: InsertHandler<LookupElement> = object : PyFunctionInsertHandler() {
      override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val tailOffset = context.tailOffset - 1
        super.handleInsert(context, item)  // adds parentheses, modifies tail offset
        context.commitDocument()
        addImportForLookupElement(context, item, tailOffset)
      }
    }

    internal val genericTypeInsertHandler: InsertHandler<LookupElement> = InsertHandler<LookupElement> { context, item ->
      val tailOffset = context.tailOffset - 1
      PyParameterizedTypeInsertHandler.INSTANCE.handleInsert(context, item)
      context.commitDocument()
      addImportForLookupElement(context, item, tailOffset)
    }

    internal val stringLiteralInsertHandler: InsertHandler<LookupElement> = InsertHandler { context, item ->
      val element = item.psiElement
      if (element == null) return@InsertHandler
      if (element is PyQualifiedNameOwner) {
        insertStringLiteralPrefix(element.qualifiedName, element.name, context)
      }
      else {
        val importPath = QualifiedNameFinder.findCanonicalImportPath(element, null)
        if (importPath != null) {
          insertStringLiteralPrefix(importPath.toString(), importPath.lastComponent.toString(), context)
        }
      }
    }

    private fun insertStringLiteralPrefix(qualifiedName: String?, name: String?, context: InsertionContext) {
      if (qualifiedName != null && name != null) {
        val qualifiedNamePrefix = qualifiedName.dropLast(name.length)
        context.document.insertString(context.startOffset, qualifiedNamePrefix)
      }
    }
    
    fun addImportForLookupElement(context: InsertionContext, item: LookupElement, tailOffset: Int) {
      val manager = PsiDocumentManager.getInstance(context.project)
      val document = manager.getDocument(context.file)
      if (document != null) {
        manager.commitDocument(document)
      }
      val ref = context.file.findReferenceAt(tailOffset)
      if (ref == null || ref.resolve() === item.psiElement) {
        // no import statement needed
        return
      }
      WriteCommandAction.writeCommandAction(context.project, context.file).run<RuntimeException> {
        val psiElement = item.psiElement
        if (psiElement is PsiNamedElement) {
          AddImportHelper.addImport(psiElement, context.file, ref.element as PyElement)
        }
      }
    }
  }
}