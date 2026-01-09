package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyClassPatternImpl
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.types.*

class PyPatternInspection : PyInspection() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return PyPatternInspectionVisitor(holder, PyInspectionVisitor.getContext(session))
  }
}

private class PyPatternInspectionVisitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {
  override fun getHolder(): ProblemsHolder = super.getHolder()!!

  /**
   * Simplify `int() as name` to `int(name)`. Only for [PyClassPattern.SPECIAL_BUILTINS].
   */
  override fun visitPyAsPattern(element: PyAsPattern) {
    val pattern = element.getPattern()
    if (element.getTarget() == null) return

    if (pattern is PyClassPattern &&
        pattern.classNameReference.name in PyClassPattern.SPECIAL_BUILTINS &&
        pattern.argumentList.patterns.isEmpty()
    ) {
      holder.problem(element, PyPsiBundle.message("INSP.patterns.pattern.can.be.simplified"))
        .highlight(ProblemHighlightType.WEAK_WARNING)
        .fix(SimplifyAsPatternFix(element))
        .register()
    }
  }


  override fun visitPyClassPattern(node: PyClassPattern) {
    val type = myTypeEvalContext.getType(node.classNameReference)
    val types = PyTypeUtil.toStream(type).toList()
    if (types.isNotEmpty() && types.none { PyTypeChecker.isUnknown(it, myTypeEvalContext) }) {
      val invalidTypes = types.filter { it !is PyClassType || !it.isDefinition }
      if (invalidTypes.isNotEmpty()) {
        val invalidTypesUnion = PyUnionType.union(invalidTypes)
        val invalidTypeName = PythonDocumentationProvider.getTypeName(invalidTypesUnion, myTypeEvalContext)
        holder.problem(node.classNameReference,
                       PyPsiBundle.message("INSP.patterns.not.a.class", node.classNameReference.text, invalidTypeName)).register()
        return
      }
    }

    val classType = type as? PyClassType ?: return
    val pyClass = classType.pyClass
    if (pyClass.name in PyClassPattern.SPECIAL_BUILTINS) return

    val matchArgs = PyClassPatternImpl.getMatchArgs(classType, myTypeEvalContext) ?: run {
      node.argumentList.patterns.filterNot { it is PyKeywordPattern }.forEach { pattern ->
        holder.problem(pattern,
                       PyPsiBundle.message("INSP.patterns.class.does.not.support.pattern.matching.with.positional.arguments", pyClass.name))
          .fix(AddMatchArgsFix(pyClass))
          .register()
      }
      return
    }

    val (positionalPatterns, keywordPatterns) = node.argumentList.patterns.partition { it !is PyKeywordPattern }

    for (pattern in positionalPatterns.drop(matchArgs.size)) {
      holder.problem(pattern, PyPsiBundle.message("INSP.patterns.too.many.positional.patterns.expected", matchArgs.size))
        .fix(PyRemoveElementFix(pattern))
        .register()
    }

    if (positionalPatterns.isEmpty() || keywordPatterns.isEmpty()) return

    // Map positional patterns to their corresponding attribute names
    val positionalAttributeNames = positionalPatterns.indices.map { index ->
      if (index < matchArgs.size) matchArgs[index] else null
    }

    // Check for conflicts between positional and named attributes
    for (keywordPattern in keywordPatterns) {
      val keywordName = (keywordPattern as PyKeywordPattern).keyword
      val positionalIndex = positionalAttributeNames.indexOf(keywordName)
      if (positionalIndex >= 0) {
        holder.problem(keywordPattern,
                       PyPsiBundle.message("INSP.patterns.attribute.already.specified.as.positional.pattern.at.position",
                                           keywordName,
                                           positionalIndex + 1))
          .fix(PyRemoveElementFix(keywordPattern))
          .register()
      }
    }
  }

  override fun visitPyClass(node: PyClass) {
    val matchArgs = node
                      .findClassAttribute(PyNames.MATCH_ARGS, false, myTypeEvalContext)
                      ?.findAssignedValue()
                      ?.let { PyPsiUtils.flattenParens(it) } ?: return

    val matchArgsType = myTypeEvalContext.getType(matchArgs) ?: return
    val strType = PyBuiltinCache.getInstance(matchArgs).strType ?: return
    val goodTuple = PyTupleType.createHomogeneous(matchArgs, strType) ?: return
    if (PyTypeChecker.match(goodTuple, matchArgsType, myTypeEvalContext)) return
    // __match_args__ must be a tuple[str, ...]
    holder.problem(matchArgs, PyPsiBundle.message(
      "INSP.type.checker.expected.type.got.type.instead",
      PythonDocumentationProvider.getTypeName(goodTuple, myTypeEvalContext),
      PythonDocumentationProvider.getTypeName(matchArgsType, myTypeEvalContext))).register()
  }
}

private class SimplifyAsPatternFix(element: PyAsPattern) : PsiUpdateModCommandAction<PyAsPattern>(element) {
  override fun getFamilyName(): String = PyPsiBundle.message("QFIX.simplify.as.pattern")

  override fun invoke(context: ActionContext, element: PyAsPattern, updater: ModPsiUpdater) {
    val pattern = element.getPattern() as PyClassPattern
    val target = element.getTarget() ?: return

    val generator = PyElementGenerator.getInstance(element.project)
    val newPattern = generator.createPatternFromText(
      LanguageLevel.forElement(element),
      "${pattern.classNameReference.text}(${target.name})"
    )

    element.replace(newPattern)
  }
}

class PyRemoveElementFix(element: PyElement) : PsiUpdateModCommandAction<PyElement>(element) {
  override fun getFamilyName(): String = PyPsiBundle.message("QFIX.NAME.remove.element")
  override fun getPresentation(context: ActionContext, element: PyElement): Presentation? = when (element) {
    is PyPattern -> Presentation.of(PyPsiBundle.message("QFIX.remove.pattern"))
    else -> super.getPresentation(context, element)
  }

  override fun invoke(context: ActionContext, element: PyElement, updater: ModPsiUpdater) {
    element.delete()
  }
}


class AddMatchArgsFix(element: PyClass) : PsiUpdateModCommandAction<PyClass>(element) {
  override fun getFamilyName(): String = PyPsiBundle.message("QFIX.NAME.add.match.args.to.class")
  override fun getPresentation(context: ActionContext, element: PyClass): Presentation {
    return Presentation.of(PyPsiBundle.message("QFIX.add.match.args.to.class", element.name))
  }

  /**
   * Take positional arguments from `__init__`, check whether the class has an attribute with the same name,
   * and if so, add it to `__match_args__`.
   */
  override fun invoke(context: ActionContext, pyClass: PyClass, updater: ModPsiUpdater) {
    val typeEvalContext = TypeEvalContext.userInitiated(pyClass.project, pyClass.containingFile)

    val initMethod = pyClass.findMethodByName(PyNames.INIT, false, typeEvalContext)
    val positionalArgs = initMethod?.parameterList?.parameters
                           ?.drop(1)
                           ?.mapNotNull { it.name }
                           ?.filter { pyClass.findInstanceAttribute(it, true) != null }
                           ?.toList() ?: emptyList()

    val generator = PyElementGenerator.getInstance(pyClass.project)
    val matchArgsValue = positionalArgs.joinToString(
      prefix = "(",
      postfix = if (positionalArgs.size != 1) ")" else ",)",
      separator = ", "
    ) { "'$it'" }
    val matchArgsAssignment = generator.createFromText(
      LanguageLevel.forElement(pyClass),
      PyAssignmentStatement::class.java,
      "${PyNames.MATCH_ARGS} = $matchArgsValue",
    )

    val anchor = pyClass.statementList.statements.firstOrNull()
    val result = pyClass.statementList.addBefore(matchArgsAssignment, anchor) as PyAssignmentStatement
    updater.moveCaretTo(result.textRange.endOffset - 1)
    PyPsiUtils.removeRedundantPass(pyClass.statementList)
  }
}