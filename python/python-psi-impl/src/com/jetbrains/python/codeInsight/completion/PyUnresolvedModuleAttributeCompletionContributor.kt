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
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.QualifiedName
import com.intellij.util.ProcessingContext
import com.intellij.util.Processor
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.search.PySearchUtilBase
import com.jetbrains.python.psi.stubs.PyModuleNameIndex
import com.jetbrains.python.psi.stubs.PyQualifiedNameCompletionMatcher
import com.jetbrains.python.psi.stubs.PyQualifiedNameCompletionMatcher.QualifiedNameMatcher

class PyUnresolvedModuleAttributeCompletionContributor : CompletionContributor() {

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
      if (ref == null || ref.resolve() === item.psiElement) {
        // no import statement needed
        return
      }
      WriteCommandAction.writeCommandAction(context.project, context.file).run<RuntimeException> {
        val psiElement = item.psiElement
        if (psiElement is PsiNamedElement && psiElement.containingFile != null) {
          val fileName = psiElement.containingFile.name
          val elementNameQualifier = if (psiElement is PyQualifiedNameOwner) psiElement.qualifiedName?.substringBeforeLast('.') else null
          val nameToImport = elementNameQualifier ?: fileName.substringBefore(".py")
          AddImportHelper.addImportStatement(context.file, nameToImport, null, null, ref.element as PyElement)
        }
      }
    }

    private fun filterAttributes(it: PyElement, qualifier: String): Boolean {
      val qualifiedName = (it as? PyQualifiedNameOwner)?.qualifiedName ?: return false
      return qualifiedName.startsWith(qualifier) && !qualifiedName.substringAfter(qualifier).startsWith("._")
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
        val qualifier = context.get(REFERENCE_QUALIFIER).toString()
        if (attribute.isEmpty()) {
          val builders = PyModuleNameIndex.find(qualifier, project, true).asSequence()
            .filter { PyUtil.isImportable(parameters.originalFile, it) }
            .flatMap { it.iterateNames().asSequence() }
            .filter { filterAttributes(it, qualifier) }
            .mapNotNull {
              val lookupString = if (it is PyQualifiedNameOwner && it.qualifiedName != null) it.qualifiedName!! else it.name!!
              LookupElementBuilder.create(it, lookupString)
                .withIcon(it.getIcon(0))
                .withInsertHandler(importingInsertHandler)
            }

          val newResultSet = result.withPrefixMatcher(PlainPrefixMatcher("$qualifier."))
          builders.forEach { newResultSet.addElement(it) }

          result.restartCompletionOnAnyPrefixChange()
          return
        }
        val scope = PySearchUtilBase.excludeSdkTestsScope(project)
        val resultMatchingCompleteReference = result.withPrefixMatcher(QualifiedNameMatcher(qualifier, attribute))
        PyQualifiedNameCompletionMatcher.processMatchingExportedNames(qualifier, attribute, parameters.originalFile, scope, Processor {
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