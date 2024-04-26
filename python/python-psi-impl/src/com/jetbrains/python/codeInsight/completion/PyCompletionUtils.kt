// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.TailTypes
import com.intellij.codeInsight.completion.AutoCompletionContext
import com.intellij.codeInsight.completion.AutoCompletionDecision
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.TailTypeDecorator
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.resolve.QualifiedNameFinder
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.parser.icons.PythonParserIcons
import com.jetbrains.python.psi.icons.PythonPsiApiIcons
import one.util.streamex.StreamEx

/**
 * Various utils for custom completions
 */

/**
 * Implementation of CompletionContributor#handleAutoCompletionPossibility
 *
 * auto-insert the obvious only case; else show other cases.
 */
fun autoInsertSingleItem(context: AutoCompletionContext): AutoCompletionDecision =
  if (context.items.size == 1) {
    AutoCompletionDecision.insertItem(context.items.first())!!
  }
  else {
    AutoCompletionDecision.SHOW_LOOKUP!!
  }


fun CompletionParameters.getPyClass(): PyClass? = (ScopeUtil.getScopeOwner(position) as? PyFunction)?.containingClass
fun CompletionParameters.getFile(): PsiFile? = (ScopeUtil.getScopeOwner(position) as? PyFunction)?.containingFile


fun CompletionParameters.getTypeEvalContext(): TypeEvalContext = TypeEvalContext.codeCompletion(originalFile.project, originalFile)


/**
 * Add method completion to result.
 * @param result destination
 * @param pyClass if provided will check if method does not exist already
 * @param builderPostprocessor function to be used to tune lookup builder
 */
fun addMethodToResult(result: CompletionResultSet,
                      pyClass: PyClass?,
                      typeEvalContext: TypeEvalContext,
                      methodName: String,
                      methodParentheses: String = "(self)",
                      builderPostprocessor: ((LookupElementBuilder) -> LookupElementBuilder)? = null) {
  if (pyClass?.findMethodByName(methodName, false, typeEvalContext) != null) return

  val item = LookupElementBuilder.create(methodName + methodParentheses).withIcon(
    PythonPsiApiIcons.Nodes.CyanDot)
  result.addElement(TailTypeDecorator.withTail(builderPostprocessor?.invoke(item) ?: item, TailTypes.caseColonType()))
}

/**
 * Add function completion to result.
 * @param result destination
 * @param pyFile if provided will check if function does not exist already
 * @param builderPostprocessor function to be used to tune lookup builder
 */
fun addFunctionToResult(result: CompletionResultSet,
                        pyFile: PyFile?,
                        functionName: String,
                        functionParentheses: String = "()",
                        builderPostprocessor: ((LookupElementBuilder) -> LookupElementBuilder)? = null) {
  if (pyFile?.findTopLevelFunction(functionName) != null) return

  val item = LookupElementBuilder.create(functionName + functionParentheses).withIcon(
    PythonPsiApiIcons.Nodes.CyanDot)
  result.addElement(TailTypeDecorator.withTail(builderPostprocessor?.invoke(item) ?: item, TailTypes.caseColonType()))
}

/**
 * Create [LookupElementBuilder] for [element] in the [file].
 *
 * Can be used to create lookup elements for packages and modules.
 * Uses canonical import path for item representation.
 */
fun createLookupElementBuilder(file: PsiFile, element: PsiFileSystemItem): LookupElementBuilder? {
  val name = FileUtil.getNameWithoutExtension(element.name)
  if (!PyNames.isIdentifier(name)) return null

  val importPath = QualifiedNameFinder.findCanonicalImportPath(element, file)?.removeLastComponent()
  val tailText = if (importPath != null && importPath.componentCount > 0) " ($importPath)" else null

  return LookupElementBuilder.create(element, name)
    .withTailText(tailText, true)
    .withIcon(element.getIcon(0))
}


private const val ELEMENT_TYPE = 10
private const val LOCATION = 100
private const val PRIVATE_API = 1_000
private const val LOCATION_NOT_YET_IMPORTED = 10_000
private const val UNDERSCORE_IN_NAME = 100_000
const val FALLBACK_WEIGHT = -1_000_000


/**
 * Determines weight of suggested completion/import item.
 * @param completionLocation file, in which the completion is taking place
 * @param nameOnly indicates that we just need to check the name for underscores
 */
fun computeCompletionWeight(element: PsiElement, elementName: String?, path: QualifiedName?, completionLocation: PsiFile?, nameOnly: Boolean): Int {
  var weight = 0
  val name = elementName ?: return FALLBACK_WEIGHT

  weight -= when {
    name.startsWith("__") && name.endsWith("__") -> UNDERSCORE_IN_NAME * 3
    name.startsWith("__") -> UNDERSCORE_IN_NAME * 2
    name.startsWith("_") -> UNDERSCORE_IN_NAME
    else -> 0
  }

  if (nameOnly) return weight

  var vFile: VirtualFile? = null
  var sdk: Sdk? = null
  val containingFile = element.containingFile
  if (element is PsiDirectory) {
    vFile = element.virtualFile
    sdk = PythonSdkUtil.findPythonSdk(element)
  }
  else if (containingFile != null) {
    vFile = containingFile.virtualFile
    sdk = PythonSdkUtil.findPythonSdk(containingFile)
  }

  val importPath = path ?: QualifiedNameFinder.findShortestImportableQName(element.containingFile) ?: return FALLBACK_WEIGHT
  if (completionLocation != null && !hasImportsFrom(completionLocation, importPath)) {
    weight -= LOCATION_NOT_YET_IMPORTED
  }

  val privatePathComponents = importPath.components.count{ it.startsWith("_") } * PRIVATE_API
  weight -= privatePathComponents + importPath.componentCount

  if (vFile != null) {
    weight -=  when {
      PythonSdkUtil.isStdLib(vFile, sdk) -> LOCATION
      ModuleUtilCore.findModuleForFile(vFile, element.project) == null -> LOCATION * 2
      else -> 0
    }
  }

  weight -= when(element) {
    is PsiDirectory -> ELEMENT_TYPE * 2
    is PyFile -> ELEMENT_TYPE
    else -> 0
  }

  return weight
}

fun hasImportsFrom(file: PsiFile, qName: QualifiedName): Boolean {
  return if (file is PyFile) {
    StreamEx.of(file.fromImports).map<QualifiedName> { it.getImportSourceQName() }
      .nonNull()
      .anyMatch { qName == it }
  }
  else false
}
