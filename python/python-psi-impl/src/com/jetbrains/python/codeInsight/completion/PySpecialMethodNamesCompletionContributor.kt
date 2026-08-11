// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.AutoCompletionContext
import com.intellij.codeInsight.completion.AutoCompletionDecision
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.extensions.afterDefInFunction
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.impl.PyBuiltinCache

class PySpecialMethodNamesCompletionContributor : CompletionContributor(), DumbAware {
  override fun handleAutoCompletionPossibility(context: AutoCompletionContext): AutoCompletionDecision = autoInsertSingleItem(context)

  init {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement().afterDefInFunction(), MyCompletionProvider)
  }

  private object MyCompletionProvider : CompletionProvider<CompletionParameters>() {
    // Dunders that only make sense on a metaclass (a subclass of `type`). They are suppressed here so they aren't suggested in
    // ordinary classes; metaclasses still get them through the regular base-method override completion. Operator/protocol dunders
    // that `type` happens to implement (`__call__`, `__or__`, `__ror__`, ...) are intentionally NOT listed, since they are
    // legitimate to define on ordinary classes (PY-90275).
    private val metaclassOnlyMethods = setOf(PyNames.PREPARE, "__instancecheck__", "__subclasscheck__")

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val typeEvalContext = parameters.getTypeEvalContext()

      val pyClass = parameters.getPyClass()
      if (pyClass != null) {
        val builtins = PyBuiltinCache.getInstance(pyClass)
        val fromObject = builtins.getClass(PyNames.OBJECT)?.methods?.toSet()?.map { it.name }.orEmpty()
        PyNames.getBuiltinMethods(LanguageLevel.forElement(pyClass))
          .asSequence()
          .filter { it.key !in fromObject && it.key !in metaclassOnlyMethods }
          .forEach { (name, description) ->
            val signature = description.signature

            addMethodToResult(result, pyClass, typeEvalContext, name, signature) { postProcessCompletion(it, name, description) }
          }
      }
      else {
        val file = parameters.getFile()
        if (file != null) {
          PyNames
            .getModuleBuiltinMethods(LanguageLevel.forElement(file))
            .forEach { (name, description) ->
              addFunctionToResult(result, file as? PyFile, name, description.signature) { postProcessCompletion(it, name, description) }
            }
        }
      }
    }

    private fun postProcessCompletion(lookupElementBuilder: LookupElementBuilder, name: String, description: PyNames.BuiltinDescription) =
      lookupElementBuilder
        .withTypeText("predefined")
        .withInsertHandler { insertionContext, _ ->
          WriteCommandAction.writeCommandAction(insertionContext.file).run<Exception> {
            description.imports.forEach {
              val dotIndex = it.lastIndexOf('.')
              AddImportHelper.addFromImportStatement(
                insertionContext.file,
                it.take(dotIndex),
                it.drop(dotIndex + 1),
                null,
                null,
                null,
              )
            }
          }
        }
  }
}
