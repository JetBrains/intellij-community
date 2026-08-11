// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.codeInsight.imports.AddImportHelper.addOrUpdateFromImportStatement
import com.jetbrains.python.psi.PyExpressionStatement
import com.jetbrains.python.psi.PyReferenceExpression

/**
 * Offers completions that scaffold common Python class forms (`dataclass`, `Enum`, `TypedDict`).
 * 
 * 
 * Selecting such a completion at the start of a statement inserts the corresponding class skeleton, adds the required
 * import, and starts a live template with editable stops for the class name and the class body.
 */
class PyClassFormCompletionContributor : CompletionContributor(), DumbAware {
  private enum class ClassForm(
    val myLookupString: String,
    val myImportModule: String,
    val myImportName: String,
    val myTemplateText: String,
  ) {
    DATACLASS("dataclass", "dataclasses", "dataclass", $$"@dataclass\nclass $NAME$:\n    $END$"),
    ENUM("Enum", "enum", "Enum", $$"class $NAME$(Enum):\n    $END$"),
    TYPED_DICT("TypedDict", "typing", "TypedDict", $$"class $NAME$(TypedDict):\n    $END$"),
    NAMED_TUPLE("NamedTuple", "typing", "NamedTuple", $$"class $NAME$(NamedTuple):\n    $END$"),
    PROTOCOL("Protocol", "typing", "Protocol", $$"class $NAME$(Protocol):\n    $END$"),
  }

  init {
    val place = PlatformPatterns.psiElement().withParents(PyReferenceExpression::class.java, PyExpressionStatement::class.java)
    extend(CompletionType.BASIC, place, object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
      ) {
        if (!isStatementStart(parameters.position)) return
        for (form in ClassForm.entries) {
          result.addElement(
            LookupElementBuilder
              .create(form.myLookupString)
              .withIcon(AllIcons.Nodes.Template)
              .withTypeText(form.myImportModule + "." + form.myImportName)
              .withCaseSensitivity(false)
              .withInsertHandler(createInsertHandler(form))
          )
        }
      }
    })
  }

  private fun isStatementStart(position: PsiElement): Boolean {
    val parent = position.getParent() as? PyReferenceExpression ?: return false
    if (parent.qualifier != null) return false
    return parent.getParent() is PyExpressionStatement
  }

  private fun createInsertHandler(form: ClassForm): InsertHandler<LookupElement?> {
    return InsertHandler { insertionContext: InsertionContext, _: LookupElement ->
      val project = insertionContext.project
      val editor = insertionContext.editor
      val document = insertionContext.document
      val file = insertionContext.file

      // Mark the text inserted by the completion itself with a non-empty range so the marker reliably tracks an
      // import inserted above it. A zero-width marker at the file start would not move past an import added at the
      // same offset, leaving the scaffold above its own import. The marked text is removed below; the live template
      // provides the real content.
      val anchor = document.createRangeMarker(insertionContext.startOffset, insertionContext.tailOffset)
      insertionContext.commitDocument()

      addOrUpdateFromImportStatement(
        file, form.myImportModule, form.myImportName, null,
        AddImportHelper.ImportPriority.BUILTIN, null
      )
      // Flush the PSI changes made by the import insertion back to the document before the live template edits it,
      // otherwise the document stays locked by postponed reformatting.
      PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)

      document.deleteString(anchor.getStartOffset(), anchor.getEndOffset())
      editor.getCaretModel().moveToOffset(anchor.getStartOffset())
      anchor.dispose()

      val manager = TemplateManager.getInstance(project)
      val template = manager.createTemplate("", "", form.myTemplateText)
      template.setToReformat(true)
      template.addVariable("NAME", "", "", true)
      manager.startTemplate(editor, template)
    }
  }
}
