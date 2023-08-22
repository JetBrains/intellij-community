// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.inline

import com.intellij.history.LocalHistory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.util.containers.MultiMap
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.PyDunderAllReference
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyCallExpressionHelper
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.pyi.PyiUtil
import com.jetbrains.python.refactoring.PyRefactoringUtil
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil
import org.jetbrains.annotations.PropertyKey

/**
 * @author Aleksei.Kniazev
 */
class PyInlineFunctionProcessor(project: Project,
                                private val myEditor: Editor,
                                private val myFunction: PyFunction,
                                private val myReference: PsiReference?,
                                private val myInlineThisOnly: Boolean,
                                removeDeclaration: Boolean) : BaseRefactoringProcessor(project) {

  private val myFunctionClass = myFunction.containingClass
  private val myGenerator = PyElementGenerator.getInstance(myProject)
  private var myRemoveDeclaration = !myInlineThisOnly && removeDeclaration

  override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
    if (refUsages.isNull) return false
    val conflicts = MultiMap.create<PsiElement, String>()
    val usagesAndImports = refUsages.get()
    val (imports, usages) = usagesAndImports.partition { PsiTreeUtil.getParentOfType(it.element, PyImportStatementBase::class.java) != null }
    val filteredUsages = usages.filter { usage ->
      if (usage.reference is PyDunderAllReference) return@filter true
      val element = usage.element!!
      if (element.parent is PyDecorator) {
        if (!handleUsageError(element, "refactoring.inline.function.is.decorator", conflicts)) return false
        return@filter false
      }
      else if (element.parent !is PyCallExpression) {
        if (!handleUsageError(element, "refactoring.inline.function.is.reference", conflicts)) return false
        return@filter false
      }
      else {
        val callExpression = element.parent as PyCallExpression
        if (callExpression.arguments.any { it is PyStarArgument}) {
          if (!handleUsageError(element, "refactoring.inline.function.uses.unpacking", conflicts)) return false
          return@filter false
        }
      }
      return@filter true
    }

    val conflictLocations = conflicts.keySet().map { it.containingFile }
    val filteredImports = imports.filter { it.file !in conflictLocations }
    val filtered = filteredUsages + filteredImports
    refUsages.set(filtered.toTypedArray())
    return showConflicts(conflicts, filtered.toTypedArray())
  }

  private fun handleUsageError(element: PsiElement, @PropertyKey(resourceBundle = PyPsiBundle.BUNDLE) error: String, conflicts: MultiMap<PsiElement, String>): Boolean {
    val errorText = PyPsiBundle.message(error, myFunction.name)
    if (myInlineThisOnly) {
      // shortcut for inlining single reference: show error hint instead of modal dialog
      CommonRefactoringUtil.showErrorHint(myProject, myEditor, errorText, PyPsiBundle.message("refactoring.inline.function.title"),
                                          PyInlineFunctionHandler.REFACTORING_ID)
      prepareSuccessful()
      return false
    }
    conflicts.putValue(element, errorText)
    myRemoveDeclaration = false
    return true
  }

  override fun findUsages(): Array<UsageInfo> {
    if (myInlineThisOnly) {
      val element = myReference!!.element as PyReferenceExpression
      val localImport = PyResolveUtil.resolveLocally(ScopeUtil.getScopeOwner(element)!!, element.name!!).firstOrNull { it is PyImportElement }
      return if (localImport != null) arrayOf(UsageInfo(element), UsageInfo(localImport)) else arrayOf(UsageInfo(element))
    }

    // TODO: replace with PyRefactoringUtil#findUsages after PY-26881 and PY-36493 are fixed
    var references = ReferencesSearch.search(myFunction, myRefactoringScope).findAll().asSequence()
    PyiUtil.getPythonStub(myFunction)?.let { stub ->
      references += ReferencesSearch.search(stub, myRefactoringScope).asSequence()
    }
    return references
      .distinct()
      .map(PsiReference::getElement)
      .map(::UsageInfo)
      .toList()
      .toTypedArray()
  }

  override fun performRefactoring(usages: Array<out UsageInfo>) {
    val action = LocalHistory.getInstance().startAction(commandName)
    try {
      doRefactor(usages)
    }
    finally {
      action.finish()
    }
  }

  private fun doRefactor(usages: Array<out UsageInfo>) {
    val (unsortedRefs, imports) = usages.partition { PsiTreeUtil.getParentOfType(it.element, PyImportStatementBase::class.java) == null }
    val (callRefs, dunderAll) = unsortedRefs.partition { it.reference !is PyDunderAllReference }

    val references = callRefs.sortedByDescending { usage ->
      SyntaxTraverser.psiApi().parents(usage.element).asSequence().filter { it is PyCallExpression }.count()
    }

    val typeEvalContext = TypeEvalContext.userInitiated(myProject, null)
    val resolveContext = PyResolveContext.defaultContext(typeEvalContext)

    val selfUsed = myFunction.parameterList.parameters.firstOrNull()?.let { firstParam ->
      if (!firstParam.isSelf) return@let false
      return@let SyntaxTraverser.psiTraverser(myFunction.statementList).traverse()
        .filter(PyReferenceExpression::class.java)
        .filter { !it.isQualified }
        .any { it.reference.isReferenceTo(firstParam) }
    } ?: false

    val functionScope = ControlFlowCache.getScope(myFunction)
    PyClassRefactoringUtil.rememberNamedReferences(myFunction)

    references.forEach { usage ->
      val reference = usage.element as PyReferenceExpression
      val languageLevel = LanguageLevel.forElement(reference)
      val refScopeOwner = ScopeUtil.getScopeOwner(reference) ?: error("Unable to find scope owner for ${reference.name}")
      val declarations = mutableListOf<PyAssignmentStatement>()
      val generatedNames = mutableSetOf<String>()


      val callSite = PsiTreeUtil.getParentOfType(reference, PyCallExpression::class.java) ?: error("Unable to find call expression for ${reference.name}")
      val containingStatement = PsiTreeUtil.getParentOfType(callSite, PyStatement::class.java) ?: error("Unable to find statement for ${reference.name}")
      val scopeAnchor = if (containingStatement is PyFunction) containingStatement else reference

      val functionCopy = myFunction.copy() as PyFunction
      functionCopy.typeComment?.delete()
      PsiTreeUtil.getParentOfType(functionCopy.docStringExpression, PyStatement::class.java)?.delete()

      val replacement = functionCopy.statementList
      val namesInOuterScope = PyUtil.collectUsedNames(refScopeOwner)
      val builtinCache = PyBuiltinCache.getInstance(reference)

      val argumentReplacements = mutableMapOf<PyReferenceExpression, PyExpression>()
      val nameClashes = mutableSetOf<String>()
      val importAsTargets = mutableSetOf<String>()
      val nameClashRefs = MultiMap.create<String, PyExpression>()
      val importAsRefs = MultiMap.create<String, PyReferenceExpression>()
      val returnStatements = mutableListOf<PyReturnStatement>()

      val mappedArguments = prepareArguments(callSite, declarations, generatedNames, scopeAnchor, reference, languageLevel, resolveContext, selfUsed)

      myFunction.statementList.accept(object : PyRecursiveElementVisitor() {
        override fun visitPyReferenceExpression(node: PyReferenceExpression) {
          if (!node.isQualified) {
            val name = node.name!!
            if (name in namesInOuterScope && name !in mappedArguments) {
              val resolved = node.reference.resolve()
              val target = PyUtil.turnConstructorIntoClass(resolved as? PyFunction) ?: resolved
              if (!builtinCache.isBuiltin(target)) {
                val resolvedLocally = PyResolveUtil.resolveLocally(refScopeOwner, name)
                val localImports = resolvedLocally.asSequence()
                  .filterIsInstance<PyImportElement>()
                  .mapNotNull { it.importReferenceExpression?.reference }
                if (target !in resolvedLocally && localImports.none { it.isReferenceTo(target!!) }) {
                  if (PyClassRefactoringUtil.hasEncodedTarget(node)) importAsTargets.add(name)
                  else nameClashes.add(name)
                }
              }
            }
          }
          super.visitPyReferenceExpression(node)
        }

        override fun visitPyTargetExpression(node: PyTargetExpression) {
          if (!node.isQualified) {
            val name = node.name!!
            if (name in namesInOuterScope && name !in mappedArguments && functionScope.containsDeclaration(name)) {
              nameClashes.add(name)
            }
          }
          super.visitPyTargetExpression(node)
        }
      })


      replacement.accept(object : PyRecursiveElementVisitor() {
        override fun visitPyReferenceExpression(node: PyReferenceExpression) {
          if (!node.isQualified) {
            val parentLambda = PsiTreeUtil.getParentOfType(node, PyLambdaExpression::class.java)
            if (parentLambda == null  || parentLambda.parameterList.parameters.none { it.name == node.name }) {
              when (val name = node.name) {
                in mappedArguments -> argumentReplacements[node] = mappedArguments[name]!!
                in nameClashes -> nameClashRefs.putValue(name!!, node)
                in importAsTargets -> importAsRefs.putValue(name!!, node)
              }
            }
          }
          super.visitPyReferenceExpression(node)
        }

        override fun visitPyReturnStatement(node: PyReturnStatement) {
          returnStatements.add(node)
          super.visitPyReturnStatement(node)
        }

        override fun visitPyTargetExpression(node: PyTargetExpression) {
          if (!node.isQualified && node.name in nameClashes) {
            nameClashRefs.putValue(node.name, node)
          }
          super.visitPyTargetExpression(node)
        }
      })

      // Replacing
      argumentReplacements.forEach { (old, new) -> old.replace(new) }
      nameClashRefs.entrySet().forEach { (name, elements) ->
        val generated = generateUniqueAssignment(languageLevel, name, generatedNames, scopeAnchor)
        elements.forEach {
            when (it) {
              is PyTargetExpression -> it.replace(generated.targets[0])
              is PyReferenceExpression -> it.replace(generated.assignedValue!!)
            }
          }
      }

      importAsRefs.entrySet().forEach { (name, elements) ->
        val newRef = generateUniqueAssignment(languageLevel, name, generatedNames, scopeAnchor).assignedValue as PyReferenceExpression
        elements.forEach {
          PyClassRefactoringUtil.transferEncodedImports(it, newRef)
          PyClassRefactoringUtil.forceAsName(newRef, newRef.name!!)
          it.replace(newRef)
        }
      }

      if (returnStatements.size == 1 && returnStatements[0].expression !is PyTupleExpression) {
        // replace single return with expression itself
        val statement = returnStatements[0]
        val replaced = callSite.replace(statement.expression!!)
        PyClassRefactoringUtil.restoreNamedReferences(replaced)
        statement.delete()
      }
      else if (returnStatements.isNotEmpty())  {
        val newReturn = generateUniqueAssignment(languageLevel, "result", generatedNames, scopeAnchor)
        returnStatements.forEach {
          val copy = newReturn.copy() as PyAssignmentStatement
          copy.assignedValue!!.replace(it.expression!!)
          it.replace(copy)
        }
        callSite.replace(newReturn.assignedValue!!)
      }

      CodeStyleManager.getInstance(myProject).reformat(replacement, true)

      val insertElement = { elem: PsiElement -> containingStatement.parent.addBefore(elem, containingStatement) }

      declarations.forEach { insertElement(it) }
      if (replacement.firstChild != null) {
        val children = SyntaxTraverser.psiApi().children(replacement).filter { it !is PsiWhiteSpace }.toList()
        val statements = children.filterIsInstance<PyStatement>()
        if (statements.size > 1 || statements.firstOrNull() !is PyPassStatement) {
          children.asSequence()
            .map { insertElement(it) }
            .filterIsInstance<PyStatement>()
            .forEach { PyClassRefactoringUtil.restoreNamedReferences(it) }
        }
      }

      if (returnStatements.isEmpty()) {
        if (callSite.parent is PyExpressionStatement) {
          containingStatement.delete()
        }
        else {
          callSite.replace(myGenerator.createExpressionFromText(languageLevel, "None"))
        }
      }
    }

    imports.asSequence()
      .mapNotNull { it.element?.containingFile }
      .distinct()
      .forEach { PyClassRefactoringUtil.optimizeImports(it) }

    if (myRemoveDeclaration) {
      val file = myFunction.containingFile
      val stubFunction = PyiUtil.getPythonStub(myFunction)
      if (stubFunction != null && stubFunction.isWritable) {
        stubFunction.delete()
      }
      val typingOverloads = PyiUtil.getOverloads(myFunction, typeEvalContext)
      if (typingOverloads.isNotEmpty()) {
        typingOverloads.forEach { it.delete() }
      }
      myFunction.delete()
      PyClassRefactoringUtil.optimizeImports(file)
      dunderAll.forEach { it.element?.delete() }
    }
  }

  private fun prepareArguments(callSite: PyCallExpression, declarations: MutableList<PyAssignmentStatement>, generatedNames: MutableSet<String>, scopeAnchor: PsiElement,
                               reference: PyReferenceExpression, languageLevel: LanguageLevel, context: PyResolveContext, selfUsed: Boolean): Map<String, PyExpression> {
    val mapping = PyCallExpressionHelper.mapArguments(callSite, context).firstOrNull() ?: error("Can't map arguments for ${reference.name}")
    val mappedParams = mapping.mappedParameters
    val firstImplicit = mapping.implicitParameters.firstOrNull()

    val self = firstImplicit?.let { first ->
      val qualifier = reference.qualifier ?: error("Function $myFunction has first implicit parameter, but no qualifier")
      val selfReplacement = when {
        !selfUsed -> qualifier
        qualifier is PyReferenceExpression && !qualifier.isQualified -> qualifier
        else -> extractDeclaration(myFunctionClass?.name!!, qualifier, declarations, generatedNames, scopeAnchor, languageLevel).second
      }
      mapOf(first.name!! to selfReplacement)
    } ?: emptyMap()

    val passedArguments = mappedParams.asSequence()
      .map { (arg, param) ->
        val argValue = if (arg is PyKeywordArgument) arg.valueExpression!! else arg
        tryExtractDeclaration(param.name!!, argValue, declarations, generatedNames, scopeAnchor, languageLevel)
      }
      .toMap()

    val defaultValues = myFunction.parameterList.parameters.asSequence()
      .filter { it.name !in passedArguments }
      .filter { it.hasDefaultValue() }
      .map { tryExtractDeclaration(it.name!!, it.defaultValue!!, declarations, generatedNames, scopeAnchor, languageLevel) }
      .toMap()

    return self + passedArguments + defaultValues
  }

  private fun tryExtractDeclaration(paramName: String, arg: PyExpression, declarations: MutableList<PyAssignmentStatement>, generatedNames: MutableSet<String>,
                                    scopeAnchor: PsiElement, languageLevel: LanguageLevel): Pair<String, PyExpression> {
    if (arg !is PyReferenceExpression && arg !is PyLiteralExpression) {
      return extractDeclaration(paramName, arg, declarations, generatedNames, scopeAnchor, languageLevel)
    }
    return paramName to arg
  }

  private fun extractDeclaration(paramName: String, arg: PyExpression, declarations: MutableList<PyAssignmentStatement>, generatedNames: MutableSet<String>,
                                 scopeAnchor: PsiElement, languageLevel: LanguageLevel): Pair<String, PyExpression> {
    val statement = generateUniqueAssignment(languageLevel, paramName, generatedNames, scopeAnchor)
    statement.assignedValue!!.replace(arg)
    declarations.add(statement)
    return paramName to statement.targets[0]
  }

  private fun generateUniqueAssignment(level: LanguageLevel, name: String, previouslyGeneratedNames: MutableSet<String>, scopeAnchor: PsiElement): PyAssignmentStatement {
    val uniqueName = PyRefactoringUtil.selectUniqueName(name, scopeAnchor) { newName, anchor ->
      PyRefactoringUtil.isValidNewName(newName, anchor) && newName !in previouslyGeneratedNames
    }
    previouslyGeneratedNames.add(uniqueName)
    return myGenerator.createFromText(level, PyAssignmentStatement::class.java, "$uniqueName = $uniqueName")
  }

  override fun getCommandName() = PyPsiBundle.message("refactoring.inline.function.command.name", myFunction.name)
  override fun getRefactoringId() = PyInlineFunctionHandler.REFACTORING_ID

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>) = object : UsageViewDescriptor {
    override fun getElements(): Array<PsiElement> = arrayOf(myFunction)
    override fun getProcessedElementsHeader(): String = PyPsiBundle.message("refactoring.inline.function.function.to.inline")
    override fun getCodeReferencesText(usagesCount: Int, filesCount: Int): String = PyPsiBundle.message("refactoring.inline.function.invocations.to.be.inlined", filesCount)
    override fun getCommentReferencesText(usagesCount: Int, filesCount: Int): String = ""
  }
}