package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.util.QualifiedName
import com.intellij.util.ProcessingContext
import com.intellij.util.Processor
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyImportStatementBase
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.search.PySearchUtilBase
import com.jetbrains.python.psi.stubs.PyQualifiedNameCompletionMatcher
import com.jetbrains.python.psi.stubs.PyQualifiedNameCompletionMatcher.QualifiedNameMatcher

class PyUnresolvedModuleTransitiveAttributeCompletionContributor : CompletionContributor() {

  private companion object {
    val QUALIFIED_REFERENCE_EXPRESSION = psiElement(PyTokenTypes.IDENTIFIER).withParent(
      psiElement(PyReferenceExpression::class.java)
        .andNot(psiElement().inside(PyImportStatementBase::class.java))
        .with(object : PatternCondition<PyReferenceExpression>("plain qualified name") {
          override fun accepts(expression: PyReferenceExpression, context: ProcessingContext): Boolean {
            if (!expression.isQualified) return false
            val qualifiedName = expression.asQualifiedName() ?: return false
            context.put(REFERENCE_QUALIFIER, qualifiedName.removeLastComponent())
            return true
          }
        })
    )
    val REFERENCE_QUALIFIER: Key<QualifiedName> = Key.create("QUALIFIER")
  }

  init {
    extend(CompletionType.BASIC, QUALIFIED_REFERENCE_EXPRESSION, object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val originalReferenceExpr = parameters.originalPosition?.parent as? PyReferenceExpression
        // It cannot be checked in the pattern, because the default placeholder splits the reference, e.g. "foo.ba<caret>IntellijIdeaRulezzz z".
        val isOtherReferenceQualifier = originalReferenceExpr?.parent is PyReferenceExpression
        if (isOtherReferenceQualifier) return

        val attribute = result.prefixMatcher.prefix
        val qualifier = context.get(REFERENCE_QUALIFIER).toString()
        if (attribute.isEmpty()) {
          result.restartCompletionOnAnyPrefixChange()
          return
        }
        val scope = PySearchUtilBase.excludeSdkTestsScope(parameters.position.project)
        val resultMatchingCompleteReference = result.withPrefixMatcher(QualifiedNameMatcher(qualifier, attribute))
        PyQualifiedNameCompletionMatcher.processMatchingExportedNames(qualifier, attribute, parameters.originalFile, scope, Processor {
          ProgressManager.checkCanceled()
          resultMatchingCompleteReference.addElement(LookupElementBuilder
                                                       .createWithSmartPointer(it.qualifiedName.toString(), it.element)
                                                       .withIcon(it.element.getIcon(0)))
          return@Processor true
        })
      }
    })
  }
}