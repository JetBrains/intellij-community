// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.variablesview.usertyperenderers.codeinsight

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.stubs.StubIndex
import com.intellij.util.ProcessingContext
import com.intellij.util.containers.init
import com.intellij.util.textCompletion.TextCompletionProvider
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.resolve.QualifiedNameFinder
import com.jetbrains.python.psi.search.PySearchUtilBase
import com.jetbrains.python.psi.stubs.PyClassNameIndex
import com.jetbrains.python.psi.types.PyModuleType
import com.jetbrains.python.psi.types.PyType


class TypeNameCompletionProvider(val project: Project) : TextCompletionProvider {

  override fun getAdvertisement(): String? = null

  override fun getPrefix(text: String, offset: Int): String = text.substringAfterLast('.')

  override fun applyPrefixMatcher(result: CompletionResultSet, prefix: String): CompletionResultSet {
    return result.withPrefixMatcher(PlainPrefixMatcher(prefix))
  }

  override fun acceptChar(c: Char): CharFilter.Result = CharFilter.Result.ADD_TO_PREFIX

  override fun fillCompletionVariants(parameters: CompletionParameters, prefix: String, result: CompletionResultSet) {
    result.restartCompletionOnAnyPrefixChange()
    val text = parameters.originalFile.text
    if (!text.contains('.')) {
      addClassesVariants(parameters, result)
    }
    val components = text.split('.').init()
    addVariantsFromModuleComponents(parameters, result, components)
  }

  private fun addClassesFromPyFile(parameters: CompletionParameters, result: CompletionResultSet, file: PyFile, added: HashSet<String>) {
    val moduleType = PyModuleType(file)
    val ctx = ProcessingContext()
    ctx.put(PyType.CTX_NAMES, added)
    val completionVariants = moduleType.getCompletionVariants("", parameters.position.parent, ctx)
    val variantsList = listOf(*completionVariants)
      .filterIsInstance<LookupElementBuilder>()
      .filter { it.psiElement is PyClass }
      .filter { result.prefixMatcher.isStartMatch(it) }
    result.addAllElements(variantsList)
  }

  private fun addVariantsFromModuleComponents(parameters: CompletionParameters,
                                              result: CompletionResultSet,
                                              moduleComponents: List<String>) {
    val alreadyAddedModulesNames = HashSet<String>()
    val alreadyAddedClsNames = HashSet<String>()

    for (element in getElementsFromModule(moduleComponents, project)) {
      when (element) {
        is PsiDirectory -> {
          val modules = PyModuleType.getSubModuleVariants(element, parameters.position.parent, alreadyAddedModulesNames)
          result.addAllElements(modules)
        }
        is PyFile -> addClassesFromPyFile(parameters, result, element, alreadyAddedClsNames)
      }
    }
  }

  private fun addClassesVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val stubIndex = StubIndex.getInstance()
    val clsKeys = PyClassNameIndex.allKeys(project)
    val scope = PySearchUtilBase.defaultSuggestionScope(parameters.originalFile)
    val alreadySuggested = mutableSetOf<String>()

    for (elementName in result.prefixMatcher.sortMatching(clsKeys)) {
      stubIndex.processElements(PyClassNameIndex.KEY, elementName, project, scope, PyClass::class.java) { element ->
        ProgressManager.checkCanceled()
        val name = element.name ?: return@processElements true
        if (element.qualifiedName == null) return@processElements true
        val importPath = QualifiedNameFinder.findCanonicalImportPath(element, null) ?: return@processElements true
        val pathName = "$importPath.$name"
        if (alreadySuggested.add(pathName)) {
          val builder = LookupElementBuilder
            .create(pathName)
            .withIcon(AllIcons.Nodes.Class)
            .withTypeText(importPath.toString())
            .withPresentableText(name)
          result.addElement(builder)
        }
        true
      }
    }
  }
}