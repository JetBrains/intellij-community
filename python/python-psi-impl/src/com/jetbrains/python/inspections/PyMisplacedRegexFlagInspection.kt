// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyTypeUtil.asUnionSequence
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus

/**
 * Reports a flag of the `re` module, for example `re.I`, that a function of `re` takes as another `int` argument.
 * `re.compile(p).search(s, re.I)` takes the flag as the start position `pos`, and `re.sub(p, r, s, re.I)` takes it as `count`.
 * A flag is an `int`, so the call fails neither at runtime nor in the type check.
 */
@ApiStatus.Internal
class PyMisplacedRegexFlagInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return Visitor(holder, PyInspectionVisitor.getContext(session))
  }

  private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

    override fun visitPyCallExpression(node: PyCallExpression) {
      val callee = node.callee as? PyReferenceExpression ?: return
      if (callee.referencedName !in FUNCTION_NAMES) return
      val reported = mutableSetOf<PyExpression>()
      for (mapping in node.multiMapArguments(resolveContext)) {
        val function = mapping.callableType?.callable as? PyFunction ?: continue
        val qualifiedName = function.qualifiedName ?: continue
        val (parameterNames, messageKey) = MISPLACED_PARAMETERS[qualifiedName] ?: continue
        for ((argument, parameter) in mapping.mappedParameters) {
          val parameterName = parameter.name ?: continue
          val value = (argument as? PyKeywordArgument)?.valueExpression ?: argument
          if (parameterName in parameterNames && value !in reported && isRegexFlag(value)) {
            reported.add(value)
            registerProblem(value, PyPsiBundle.problemMessage(messageKey, parameterName))
          }
        }
      }
    }

    private fun isRegexFlag(argument: PyExpression): Boolean {
      val members = myTypeEvalContext.getType(argument).asUnionSequence().toList()
      return members.isNotEmpty() && members.all { it is PyClassType && !it.isDefinition && it.pyClass.qualifiedName == REGEX_FLAG_CLASS }
    }
  }

  private companion object {
    const val REGEX_FLAG_CLASS = "re.RegexFlag"
    const val COMPILE_MESSAGE = "INSP.misplaced.regex.flag.compile"
    const val KEYWORD_MESSAGE = "INSP.misplaced.regex.flag.keyword"

    /** The parameters of a function of `re` that take an `int` that is not a flag, and the message that names the right place of the flag. */
    val MISPLACED_PARAMETERS: Map<String, Pair<Set<String>, String>> = buildMap {
      for (method in listOf("search", "match", "fullmatch", "prefixmatch", "findall", "finditer")) {
        put("re.Pattern.$method", setOf("pos", "endpos") to COMPILE_MESSAGE)
      }
      put("re.Pattern.split", setOf("maxsplit") to COMPILE_MESSAGE)
      put("re.Pattern.sub", setOf("count") to COMPILE_MESSAGE)
      put("re.Pattern.subn", setOf("count") to COMPILE_MESSAGE)
      put("re.split", setOf("maxsplit") to KEYWORD_MESSAGE)
      put("re.sub", setOf("count") to KEYWORD_MESSAGE)
      put("re.subn", setOf("count") to KEYWORD_MESSAGE)
    }

    val FUNCTION_NAMES: Set<String> = MISPLACED_PARAMETERS.keys.mapTo(mutableSetOf()) { it.substringAfterLast('.') }
  }
}
