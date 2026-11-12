// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyTestFixtures

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbService
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.python.codeInsight.PyCustomMember
import com.jetbrains.python.psi.PyArgumentList
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyClassMembersProviderBase
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.TypeEvalContext

private const val MARK_GENERATOR_CLASS = "_pytest.mark.structures.MarkGenerator"

private const val MARK_GENERATOR_NAME = "mark"

private const val ADD_INI_VALUE_LINE = "addinivalue_line"

private const val MARKERS_INI_OPTION = "markers"

/**
 * A marker that a pytest plugin registers with `config.addinivalue_line("markers", "<name>(<parameters>): <description>")`.
 *
 * [keywordParameters] are the parameters of the line with a default value, as `transaction` in `django_db(transaction=False)`.
 */
private data class PyTestPluginMarker(val name: String, val keywordParameters: List<String>)

/**
 * Adds the markers of the pytest plugins of the `pytest11` entry points to the completion of `pytest.mark`.
 *
 * A marker has no declaration, so this provider resolves no member, and the type of a marker stays the type that
 * `MarkGenerator.__getattr__` gives.
 */
internal class PyTestPluginMarkerMembersProvider : PyClassMembersProviderBase() {
  override fun getMembers(clazz: PyClassType, location: PsiElement?, context: TypeEvalContext): Collection<PyCustomMember> =
    getPluginMarkers(clazz, location).map { PyCustomMember(it.name) }

  override fun resolveMember(type: PyClassType, name: String, location: PsiElement?, resolveContext: PyResolveContext): PsiElement? = null
}

/**
 * Completes the keyword parameters of a marker of [PyTestPluginMarkerMembersProvider] in its call, as `transaction=` in
 * `pytest.mark.django_db(transaction=True)`.
 */
internal class PyTestPluginMarkerArgumentCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement().withParent(PyReferenceExpression::class.java)
      .withSuperParent(2, PyArgumentList::class.java), PyTestPluginMarkerArgumentCompletionProvider)
  }
}

private object PyTestPluginMarkerArgumentCompletionProvider : CompletionProvider<CompletionParameters>() {
  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val argument = parameters.position.parent as? PyReferenceExpression ?: return
    if (argument.isQualified) return
    val call = (argument.parent as? PyArgumentList)?.callExpression ?: return
    val callee = call.callee as? PyReferenceExpression ?: return
    val markGenerator = callee.qualifier as? PyReferenceExpression ?: return
    if (markGenerator.referencedName != MARK_GENERATOR_NAME) return
    val markerName = callee.referencedName ?: return
    val typeEvalContext = TypeEvalContext.codeCompletion(call.project, call.containingFile)
    val markGeneratorType = typeEvalContext.getType(markGenerator) as? PyClassType ?: return
    val marker = getPluginMarkers(markGeneratorType, call).find { it.name == markerName } ?: return
    val usedKeywords = call.arguments.filterIsInstance<PyKeywordArgument>().mapNotNull { it.keyword }.toSet()
    for (keyword in marker.keywordParameters - usedKeywords) {
      result.addElement(PyUtil.createNamedParameterLookup(keyword, call.containingFile, true))
    }
  }
}

/**
 * Returns the markers of the plugins of the `pytest11` entry points when [markGeneratorType] is the type of `pytest.mark`.
 * The entry points are in an index, so the result is empty during indexing.
 */
private fun getPluginMarkers(markGeneratorType: PyClassType, location: PsiElement?): List<PyTestPluginMarker> {
  if (markGeneratorType.isDefinition || markGeneratorType.classQName != MARK_GENERATOR_CLASS || location == null) return emptyList()
  if (DumbService.isDumb(location.project)) return emptyList()
  val module = ModuleUtilCore.findModuleForPsiElement(location) ?: return emptyList()
  return getPytestEntryPointPluginFiles(module).flatMap { getRegisteredMarkers(it) }.distinctBy { it.name }
}

/**
 * Returns the markers that [pluginFile] registers with `addinivalue_line("markers", ...)`.
 */
private fun getRegisteredMarkers(pluginFile: PyFile): List<PyTestPluginMarker> =
  CachedValuesManager.getCachedValue(pluginFile) {
    val markers = PsiTreeUtil.findChildrenOfType(pluginFile, PyCallExpression::class.java).mapNotNull { call ->
      if ((call.callee as? PyReferenceExpression)?.referencedName != ADD_INI_VALUE_LINE) return@mapNotNull null
      val arguments = call.arguments
      if ((arguments.getOrNull(0) as? PyStringLiteralExpression)?.stringValue != MARKERS_INI_OPTION) return@mapNotNull null
      (arguments.getOrNull(1) as? PyStringLiteralExpression)?.stringValue?.let(::parseMarkerLine)
    }
    CachedValueProvider.Result.create(markers, pluginFile)
  }

/**
 * Parses a line of the `markers` option, as `django_db(transaction=False, databases=None): Mark the test as ...`.
 */
private fun parseMarkerLine(line: String): PyTestPluginMarker? {
  val signature = line.substringBefore(':')
  val name = signature.substringBefore('(').trim().takeIf { it.isNotEmpty() } ?: return null
  val keywordParameters = signature.substringAfter('(', "").substringBefore(')')
    .split(',')
    .filter { '=' in it }
    .map { it.substringBefore('=').trim() }
    .filter { it.isNotEmpty() }
  return PyTestPluginMarker(name, keywordParameters)
}
