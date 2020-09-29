package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.QualifiedName
import com.intellij.util.ProcessingContext
import com.intellij.util.Processor
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.resolve.QualifiedNameFinder
import com.jetbrains.python.psi.search.PySearchUtilBase
import com.jetbrains.python.psi.stubs.PyModuleNameIndex
import com.jetbrains.python.psi.stubs.PyQualifiedNameCompletionMatcher
import com.jetbrains.python.psi.stubs.PyQualifiedNameCompletionMatcher.QualifiedNameMatcher

class PyUnresolvedModuleAttributeCompletionContributor : CompletionContributor() {

  private companion object {
    val UNRESOLVED_FIRST_COMPONENT = object : PatternCondition<PyReferenceExpression>("unresolved first component") {
      override fun accepts(expression: PyReferenceExpression, context: ProcessingContext): Boolean {
        val qualifiedName = expression.asQualifiedName() ?: return false
        val qualifiersFirstComponent = qualifiedName.firstComponent ?: return false
        val scopeOwner = ScopeUtil.getScopeOwner(expression) ?: return false
        if (PyResolveUtil.resolveLocally(scopeOwner, qualifiersFirstComponent).isEmpty()) {
          context.put(REFERENCE_QUALIFIER, qualifiedName.removeLastComponent())
          return true
        }
        return false
      }
    }

    val QUALIFIED_REFERENCE_EXPRESSION = psiElement(PyTokenTypes.IDENTIFIER).withParent(
      psiElement(PyReferenceExpression::class.java)
        .andNot(psiElement().inside(PyImportStatementBase::class.java))
        .with(object : PatternCondition<PyReferenceExpression>("plain qualified name") {
          override fun accepts(expression: PyReferenceExpression, context: ProcessingContext): Boolean {
            return expression.isQualified
          }
        })
        .with(UNRESOLVED_FIRST_COMPONENT)
    )

    val REFERENCE_QUALIFIER: Key<QualifiedName> = Key.create("QUALIFIER")

    private val functionInsertHandler: InsertHandler<LookupElement> = object : PyFunctionInsertHandler() {
      override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val tailOffset = context.tailOffset - 1
        super.handleInsert(context, item)  // adds parentheses, modifies tail offset
        context.commitDocument()
        addImportForLookupElement(context, item, tailOffset)
      }
    }

    private val importingInsertHandler: InsertHandler<LookupElement> = InsertHandler { context, item ->
      addImportForLookupElement(context, item, context.tailOffset - 1)
    }

    private fun addImportForLookupElement(context: InsertionContext, item: LookupElement, tailOffset: Int) {
      val manager = PsiDocumentManager.getInstance(context.project)
      val document = manager.getDocument(context.file)
      if (document != null) {
        manager.commitDocument(document)
      }
      val ref = context.file.findReferenceAt(tailOffset)

      WriteCommandAction.writeCommandAction(context.project, context.file).run<RuntimeException> {
        val psiElement = item.psiElement
        if (psiElement is PsiNamedElement && psiElement.containingFile != null) {
          val nameToImport = QualifiedName.fromDottedString(item.lookupString).removeLastComponent().toString()
          AddImportHelper.addImportStatement(context.file, nameToImport, null, null, ref?.element as? PyElement)
        }
      }
    }

    private fun getInsertHandler(elementToInsert: PyElement, position: PsiElement): InsertHandler<LookupElement> {
      return if (elementToInsert is PyFunction && position.parent?.parent !is PyDecorator) functionInsertHandler else importingInsertHandler
    }
  }

  init {
    extend(CompletionType.BASIC, QUALIFIED_REFERENCE_EXPRESSION, object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val originalReferenceExpr = parameters.originalPosition?.parent as? PyReferenceExpression
        // It cannot be checked in the pattern, because the default placeholder splits the reference, e.g. "foo.ba<caret>IntellijIdeaRulezzz z".
        val isOtherReferenceQualifier = originalReferenceExpr?.parent is PyReferenceExpression
        if (isOtherReferenceQualifier) return

        val project = parameters.position.project
        val attribute = result.prefixMatcher.prefix
        val qualifier = context.get(REFERENCE_QUALIFIER)
        val qualifierString = qualifier.toString()
        if (attribute.isEmpty()) {
          result.restartCompletionOnAnyPrefixChange()
          ProgressManager.checkCanceled()

          val builders = PyModuleNameIndex.find(qualifier.lastComponent!!, project, true).asSequence()
            // checks that the name can be imported and name to import matches the qualifier
            .filter { PyUtil.isImportable(parameters.originalFile, it) && QualifiedNameFinder.findShortestImportableQName(it) == qualifier }
            .flatMap { it.iterateNames().asSequence() }
            // filters out files/directories and symbols whose names start with an underscore
            .filter { it !is PsiFileSystemItem && it.name != null && !it.name!!.startsWith('_') }
            .mapNotNull {
              LookupElementBuilder.create(it, "$qualifierString.${it.name}")
                .withIcon(it.getIcon(0))
                .withInsertHandler(getInsertHandler(it, parameters.position))
            }

          val newResultSet = result.withPrefixMatcher(PlainPrefixMatcher("$qualifierString."))
          builders.forEach { newResultSet.addElement(it) }

          return
        }
        val scope = PySearchUtilBase.excludeSdkTestsScope(project)
        val resultMatchingCompleteReference = result.withPrefixMatcher(QualifiedNameMatcher(qualifierString, attribute))
        PyQualifiedNameCompletionMatcher.processMatchingExportedNames(qualifierString, attribute, parameters.originalFile, scope, Processor {
          ProgressManager.checkCanceled()
          resultMatchingCompleteReference.addElement(LookupElementBuilder
                                                       .createWithSmartPointer(it.qualifiedName.toString(), it.element)
                                                       .withIcon(it.element.getIcon(0))
                                                       .withInsertHandler(importingInsertHandler))
          return@Processor true
        })
      }
    })
  }

}