// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.setupPy

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.jetbrains.python.PyBundle
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.sdk.rootManager


internal object SetupPyHelpers {
  const val SETUP_PY: String = "setup.py"
  const val REQUIRES: String = "requires"
  const val INSTALL_REQUIRES: String = "install_requires"
  private const val DEPENDENCY_LINKS: String = "dependency_links"
  private const val SETUP_TOOLS_PACKAGE = "setuptools"
  private val SETUP_PY_REQUIRES_KWARGS_NAMES: Array<String> = arrayOf<String>(REQUIRES, INSTALL_REQUIRES, "setup_requires", "tests_require")

  @JvmStatic
  fun detectSetupPyInModule(module: Module): PyFile? {
    val file = module.rootManager.contentRoots.firstNotNullOfOrNull {
      it.findChild(SETUP_PY)
    } ?: return null

    return PsiManager.getInstance(module.project).findFile(file) as? PyFile
  }

  fun parseSetupPy(file: PyFile): List<PyRequirement>? {
    val setupCall = findSetupCall(file) ?: return null

    val requirementsFromRequires = getSetupPyRequiresFromArguments(setupCall, *SETUP_PY_REQUIRES_KWARGS_NAMES)
    val requirementsFromLinks = getSetupPyRequiresFromArguments(setupCall, DEPENDENCY_LINKS)
    val extra = findSetupPyExtrasRequire(file)?.flatMap { it.value } ?: emptyList()

    return (requirementsFromRequires + requirementsFromLinks + extra).distinctBy { it.name }
  }

  @JvmStatic
  fun findSetupPyExtrasRequire(pyFile: PyFile): Map<String, List<PyRequirement>>? {
    val setupCall = findSetupCall(pyFile) ?: return null

    val extrasRequire = resolveValue(setupCall.getKeywordArgument("extras_require")) as? PyDictLiteralExpression
                        ?: return null

    return extrasRequire.getElements().mapNotNull { extraRequires ->
      getExtraRequires(extraRequires.key, extraRequires.value)
    }.toMap()
  }

  /**
   * @param expression expression to resolve
   * @return `expression` if it is not a reference or element that is found by following assignment chain.
   * *Note: if result is [PyExpression] then parentheses around will be flattened.*
   */
  @JvmStatic
  fun resolveValue(expression: PyExpression?): PsiElement? {
    val elementToAnalyze: PsiElement? = PyPsiUtils.flattenParens(expression)

    if (elementToAnalyze !is PyReferenceExpression) {
      return elementToAnalyze
    }

    val context = TypeEvalContext.deepCodeInsight(elementToAnalyze.getProject())
    val resolveContext = PyResolveContext.defaultContext(context)

    val resolvedElements = elementToAnalyze.multiFollowAssignmentsChain(resolveContext)
    val foundElement = resolvedElements.firstNotNullOfOrNull {
      it.element
    } ?: return null

    return if (foundElement is PyExpression)
      PyPsiUtils.flattenParens(foundElement)
    else
      foundElement
  }

  private fun resolveRequiresValue(expression: PyExpression?): List<String>? {
    val elementToAnalyze = resolveValue(expression)

    if (elementToAnalyze is PyStringLiteralExpression) {
      return listOf(elementToAnalyze.getStringValue())
    }
    if (elementToAnalyze !is PyListLiteralExpression && elementToAnalyze !is PyTupleExpression) {
      return null
    }

    return elementToAnalyze.getElements().mapNotNull {
      val literalExpression = resolveValue(it) as? PyStringLiteralExpression ?: return@mapNotNull null
      literalExpression.stringValue
    }
  }


  private fun getSetupPyRequiresFromArguments(setupCall: PyCallExpression, vararg argumentNames: String): List<PyRequirement> {
    val requirements = argumentNames.mapNotNull {
      val keywordArgument = setupCall.getKeywordArgument(it) ?: return@mapNotNull null
      resolveRequiresValue(keywordArgument)
    }.flatten()

    val parsed = requirements.mapNotNull {
      PyRequirementParser.fromLine(it)
    }

    return parsed
  }

  private fun getExtraRequires(extra: PyExpression, requires: PyExpression?): Pair<String, List<PyRequirement>>? {
    if (extra !is PyStringLiteralExpression)
      return null
    val requiresValues = resolveRequiresValue(requires) ?: return null

    val extra = extra.getStringValue()
    val pyRequirements = requiresValues.mapNotNull { PyRequirementParser.fromLine(it) }
    return extra to pyRequirements
  }

  @JvmStatic
  fun findSetupPyInstallRequires(setupCall: PyCallExpression?): PsiElement? {
    if (setupCall == null) return null

    return listOf(REQUIRES, INSTALL_REQUIRES).firstNotNullOfOrNull {
      val expression = setupCall.getKeywordArgument(it)
      resolveValue(expression)
    }
  }

  @JvmStatic
  fun findSetupCall(file: PyFile): PyCallExpression? {
    val result = Ref<PyCallExpression?>(null)
    file.acceptChildren(object : PyRecursiveElementVisitor() {
      override fun visitPyCallExpression(node: PyCallExpression) {
        val callee = node.callee
        val name = PyUtil.getReadableRepr(callee, true)
        if ("setup" == name) {
          result.set(node)
        }
      }

      override fun visitPyElement(node: PyElement) {
        if (node !is ScopeOwner) {
          super.visitPyElement(node)
        }
      }
    })
    return result.get()
  }

  @Suppress("DialogTitleCapitalization")
  @JvmStatic
  fun addRequirementsToSetupPy(setupPy: PyFile, requirementName: String, languageLevel: LanguageLevel): Boolean {
    val setupCall = findSetupCall(setupPy) ?: return false

    val installRequires = findSetupPyInstallRequires(setupCall)

    val project = setupPy.project
    WriteCommandAction.runWriteCommandAction(project, PyBundle.message("command.name.add.package.to.setup.py"), null, {
      if (installRequires != null) {
        addRequirementToInstallRequires(installRequires, requirementName, languageLevel)
      }
      else {
        val argumentList = setupCall.argumentList
        val requiresArg = generateRequiresKwarg(setupPy, requirementName, languageLevel)

        if (argumentList != null && requiresArg != null) {
          argumentList.addArgument(requiresArg)
        }
      }
    }, setupPy)
    return true
  }

  private fun addRequirementToInstallRequires(
    installRequires: PsiElement,
    requirementName: String,
    languageLevel: LanguageLevel,
  ) {
    val generator = PyElementGenerator.getInstance(installRequires.getProject())
    val newRequirement = generator.createExpressionFromText(languageLevel, "'$requirementName'")

    when (installRequires) {
      is PyListLiteralExpression -> {
        installRequires.add(newRequirement)
      }
      is PyTupleExpression -> {
        val requirements = (installRequires.getElements() + newRequirement).mapNotNull { it.text }
        val newInstallRequiresText = "(" + requirements.joinToString(", ") + ")"
        val expression = generator.createExpressionFromText(languageLevel, newInstallRequiresText)

        val pyExpressions = (expression as? PyParenthesizedExpression)?.containedExpression as? PyTupleExpression ?: return
        installRequires.replace(pyExpressions)
      }
      is PyStringLiteralExpression -> {
        val newInstallRequires = generator.createListLiteral()

        newInstallRequires.add(installRequires)
        newInstallRequires.add(newRequirement)

        installRequires.replace(newInstallRequires)
      }
    }
  }


  private fun generateRequiresKwarg(setupPy: PyFile, requirementName: String, languageLevel: LanguageLevel): PyKeywordArgument? {
    val keyword = if (PyPsiUtils.containsImport(setupPy, SETUP_TOOLS_PACKAGE)) INSTALL_REQUIRES else REQUIRES
    val text = String.format("foo(%s=['%s'])", keyword, requirementName)
    val elementGenerator = PyElementGenerator.getInstance(setupPy.getProject())
    val generated = elementGenerator.createExpressionFromText(languageLevel, text) as? PyCallExpression ?: return null
    val keywordArguments = generated.getArguments().filterIsInstance<PyKeywordArgument>()

    return keywordArguments.firstOrNull { it.keyword == keyword }
  }
}