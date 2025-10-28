// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.registry.Registry.Companion.intValue
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.QualifiedName
import com.intellij.util.*
import com.intellij.util.Function
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.PyCodeInsightSettings
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
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
import one.util.streamex.StreamEx
import java.util.*
import java.util.function.LongConsumer

/**
 * Adds completion variants for Python classes, functions and variables.
 */
class PyClassNameCompletionContributor : PyImportableNameCompletionContributor() {
  init {
    if (TRACING_WITH_SPUTNIK_ENABLED) {
      println("\u0001hr('Importable names completion')")
    }
  }

  override fun doFillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    result.restartCompletionWhenNothingMatches()
    val remainingResults = result.runRemainingContributors(parameters, true)

    if (parameters.isExtendedCompletion() || remainingResults.isEmpty() || containsOnlyElementUnderTheCaret(remainingResults, parameters)) {
      fillCompletionVariantsImpl(parameters, result)
    }
  }

  private fun fillCompletionVariantsImpl(parameters: CompletionParameters, result: CompletionResultSet) {
    val isExtendedCompletion = parameters.isExtendedCompletion()
    if (!PyCodeInsightSettings.getInstance().INCLUDE_IMPORTABLE_NAMES_IN_BASIC_COMPLETION && !isExtendedCompletion) {
      return
    }
    val originalFile = parameters.getOriginalFile()
    val position = parameters.getPosition()
    val refExpr = PyUtil.`as`<PyReferenceExpression?>(position.getParent(), PyReferenceExpression::class.java)
    val targetExpr = PyUtil.`as`<PyTargetExpression?>(position.getParent(), PyTargetExpression::class.java)
    val originalPosition = parameters.getOriginalPosition()
    // In cases like `fo<caret>o = 42` the target expression gets split by the trailing space at the end of DUMMY_IDENTIFIER as
    // `foIntellijIdeaRulezzz o = 42`, so in the copied file we're still completing inside a standalone reference expression.
    val originallyInsideTarget = originalPosition != null && originalPosition.getParent() is PyTargetExpression
    val insideUnqualifiedReference = refExpr != null && !refExpr.isQualified() && !originallyInsideTarget
    val insidePattern = targetExpr != null && position.getParent().getParent() is PyCapturePattern
    val insideStringLiteralInExtendedCompletion = position is PyStringElement && isExtendedCompletion
    if (!(insideUnqualifiedReference || insidePattern || insideStringLiteralInExtendedCompletion)) {
      return
    }

    // Directly inside the class body scope, it's rarely needed to have expression statements
    // TODO apply the same logic for completion of importable module and package names
    if (refExpr != null &&
        (isDirectlyInsideClassBody(refExpr) || isInsideErrorElement(refExpr))
    ) {
      return
    }
    // TODO Use another method to collect already visible names
    // Candidates: PyExtractMethodValidator, IntroduceValidator.isDefinedInScope
    val refUnderCaret = if (refExpr != null) refExpr.getReference() else if (targetExpr != null) targetExpr.getReference() else null
    val namesInScope = if (refUnderCaret == null) mutableSetOf<String?>()
    else StreamEx.of<Any?>(*refUnderCaret.getVariants())
      .select<LookupElement?>(LookupElement::class.java)
      .map<String> { obj: LookupElement? -> obj!!.getLookupString() }
      .toSet()
    val project = originalFile.getProject()
    val typeEvalContext = TypeEvalContext.codeCompletion(project, originalFile)
    val maxVariants = intValue("ide.completion.variant.limit")
    val counters = Counters()
    val stubIndex = StubIndex.getInstance()
    TimeoutUtil.run<RuntimeException?>(ThrowableRunnable {
      val scope: GlobalSearchScope = createScope(originalFile)
      val alreadySuggested: MutableSet<QualifiedName> = HashSet<QualifiedName>()
      forEachPublicNameFromIndex(scope, Processor { elementName: String ->
        ProgressManager.checkCanceled()
        counters.scannedNames++
        if (!result.getPrefixMatcher().isStartMatch(elementName)) return@Processor true
        stubIndex.processElements<String?, PyElement?>(PyExportedModuleAttributeIndex.KEY, elementName, project, scope,
                                                       PyElement::class.java, Processor { exported: PyElement? ->
            ProgressManager.checkCanceled()
            val name = exported!!.getName()
            if (name == null || namesInScope.contains(name)) return@Processor true
            val fqn: QualifiedName = Companion.getFullyQualifiedName(exported)
            if (!Companion.isApplicableInInsertionContext(exported, fqn, position, typeEvalContext)) {
              counters.notApplicableInContextNames++
              return@Processor true
            }
            if (alreadySuggested.add(fqn)) {
              if (Companion.isPrivateDefinition(fqn, exported, originalFile)) {
                counters.privateNames++
                return@Processor true
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
                    presentation.setTypeText(importPath.toString())
                  }
                })
                .withInsertHandler(getInsertHandler(exported, position, typeEvalContext))
              result.addElement(
                PrioritizedLookupElement.withPriority(lookupElement, PythonCompletionWeigher.NOT_IMPORTED_MODULE_WEIGHT.toDouble()))
              counters.totalVariants++
              if (counters.totalVariants >= maxVariants) return@Processor false
            }
            true
          })
      })
    }, LongConsumer { duration: Long ->
      LOG.debug(counters.toString() + " computed for prefix '" + result.getPrefixMatcher().getPrefix() + "' in " + duration + " ms")
      if (TRACING_WITH_SPUTNIK_ENABLED) {
        System.out.printf("\u0001h('Importable names completion','%d')%n", (duration / 10) * 10)
        System.out.printf("\u0001Hi(%d)%n", duration)
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
        return importingInsertHandler
      }
      return stringLiteralInsertHandler
    }
    else if (PyParameterizedTypeInsertHandler.isCompletingParameterizedType(exported, position, typeEvalContext)) {
      return genericTypeInsertHandler
    }
    else if (exported is PyFunction && position.getParent().getParent() !is PyDecorator) {
      return functionInsertHandler
    }
    return importingInsertHandler
  }

  private class Counters {
    var scannedNames: Int = 0
    var privateNames: Int = 0
    var notApplicableInContextNames: Int = 0
    var totalVariants: Int = 0

    override fun toString(): String {
      return "Counters{" +
             "scannedNames=" + scannedNames +
             ", privateNames=" + privateNames +
             ", notApplicableInContextNames=" + notApplicableInContextNames +
             ", totalVariants=" + totalVariants +
             '}'
    }
  }

  companion object {
    // See https://plugins.jetbrains.com/plugin/18465-sputnik
    private const val TRACING_WITH_SPUTNIK_ENABLED = false
    private val LOG = Logger.getInstance(PyClassNameCompletionContributor::class.java)

    // See PY-73964, IJPL-265
    private const val RECURSIVE_INDEX_ACCESS_ALLOWED = false

    private fun containsOnlyElementUnderTheCaret(
      remainingResults: MutableSet<CompletionResult?>,
      parameters: CompletionParameters
    ): Boolean {
      val position = parameters.getOriginalPosition()
      if (remainingResults.size == 1 && position != null) {
        val lookup = ContainerUtil.getFirstItem<CompletionResult>(remainingResults)
        return lookup.getLookupElement().getLookupString() == position.getText()
      }
      return false
    }

    private fun forEachPublicNameFromIndex(scope: GlobalSearchScope, processor: Processor<String?>) {
      val stubIndex = StubIndex.getInstance()
      if (!RECURSIVE_INDEX_ACCESS_ALLOWED) {
        val project = Objects.requireNonNull<Project>(scope.getProject())
        val manager = CachedValuesManager.getManager(project)

        val cachedAllNames = manager.getCachedValue<MutableCollection<String?>>(project, CachedValueProvider {
          val index = StubIndex.getInstance()
          val keys = index.getAllKeys<String?>(PyExportedModuleAttributeIndex.KEY, project)
          val modificationTracker = index.getPerFileElementTypeModificationTracker(PyFileElementType.INSTANCE)
          CachedValueProvider.Result.create<MutableCollection<String?>?>(keys, modificationTracker)
        })

        for (allKey in cachedAllNames) {
          if (!processor.process(allKey)) {
            return
          }
        }
      }
      else {
        stubIndex.processAllKeys<String?>(PyExportedModuleAttributeIndex.KEY, processor, scope)
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
                ArrayUtil.contains(fqn.getFirstComponent(), "typing", "typing_extensions")
               )
      }
      if (PsiTreeUtil.getParentOfType<PyPattern?>(position, PyPattern::class.java, false) != null) {
        return definition is PyClass
      }
      return true
    }

    private fun isSuitableTypeAlias(element: PyElement, context: TypeEvalContext): Boolean {
      if (element is PyTargetExpression && element.getParent() is PyAssignmentStatement) {
        if (PyTypingTypeProvider.isExplicitTypeAlias(element.parent as PyAssignmentStatement, context)) return true;
      }
      return element is PyTypeAliasStatement;
    }

    private fun isInsideErrorElement(referenceExpression: PyReferenceExpression): Boolean {
      return PsiTreeUtil.getParentOfType<PsiErrorElement?>(referenceExpression, PsiErrorElement::class.java) != null
    }

    private fun isDirectlyInsideClassBody(referenceExpression: PyReferenceExpression): Boolean {
      return referenceExpression.getParent() is PyExpressionStatement &&
             ScopeUtil.getScopeOwner(referenceExpression.getParent()) is PyClass
    }

    private fun getFullyQualifiedName(exported: PyElement): QualifiedName {
      val shortName = StringUtil.notNullize(exported.getName())
      val qualifiedName = if (exported is PyQualifiedNameOwner) exported.getQualifiedName() else null
      return QualifiedName.fromDottedString(if (qualifiedName != null) qualifiedName else shortName)
    }

    private fun isPrivateDefinition(fqn: QualifiedName, exported: PyElement, originalFile: PsiFile?): Boolean {
      if (containsPrivateComponents(fqn)) {
        val importPath = QualifiedNameFinder.findCanonicalImportPath(exported, originalFile)
        return importPath != null && containsPrivateComponents(importPath)
      }
      return false
    }

    private fun containsPrivateComponents(fqn: QualifiedName): Boolean {
      return ContainerUtil.exists<String?>(fqn.getComponents(), Condition { c: String? -> c!!.startsWith("_") })
    }

    private fun createScope(originalFile: PsiFile): GlobalSearchScope {
      class HavingLegalImportPathScope(project: Project) : QualifiedNameBasedScope(project) {
        override fun containsQualifiedNameInRoot(root: VirtualFile, qName: QualifiedName): Boolean {
          return ContainerUtil.all<String?>(qName.getComponents(), Condition { name: String? ->
            PyNames.isIdentifier(name!!)
          }) && qName != QualifiedName.fromComponents("__future__")
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
      val files = ContainerUtil.filterIsInstance<PsiFile?>(resolveQualifiedName(QualifiedName.fromDottedString(fqn), context),
                                                           PsiFile::class.java)
      if (files.isEmpty()) return GlobalSearchScope.EMPTY_SCOPE
      return GlobalSearchScope.filesWithLibrariesScope(anchor.getProject(), ContainerUtil.map<PsiFile?, VirtualFile?>(files,
                                                                                                                      Function { obj: PsiFile? -> obj!!.getVirtualFile() }))
    }

    private fun isInsideAllInInitPy(position: PsiElement): Boolean {
      val originalFile = position.getContainingFile()
      if (originalFile == null) {
        return false
      }

      if (PyNames.INIT_DOT_PY != originalFile.getName()) {
        return false
      }

      val assignment =
        PsiTreeUtil.getParentOfType<PyAssignmentStatement?>(position, PyAssignmentStatement::class.java)
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
  }
}
