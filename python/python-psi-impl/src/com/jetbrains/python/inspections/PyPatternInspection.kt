package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyClassPatternImpl
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyTupleType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.TypeEvalContext

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
    val classType = myTypeEvalContext.getType(node.classNameReference) as? PyClassType ?: return
    val pyClass = classType.pyClass
    if (pyClass.name in PyClassPattern.SPECIAL_BUILTINS) return
    
    val matchArgs = PyClassPatternImpl.getMatchArgs(classType, myTypeEvalContext) ?: run {
      node.argumentList.patterns.filterNot { it is PyKeywordPattern }.forEach { pattern ->
        holder.problem(pattern, PyPsiBundle.message("INSP.patterns.class.does.not.support.pattern.matching.with.positional.arguments", pyClass.name))
          .fix(AddMatchArgsFix(pyClass))
          .register()
      }
      return
    }
    
    val (positionalPatterns, keywordPatterns) = node.argumentList.patterns.partition { it !is PyKeywordPattern }
    
    for (pattern in positionalPatterns.drop(matchArgs.size)) {
      holder.problem(pattern, PyPsiBundle.message("INSP.patterns.too.many.positional.patterns.expected", matchArgs.size))
        .fix(RemoveListMemberFix(pattern))
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
        holder.problem(keywordPattern, PyPsiBundle.message("INSP.patterns.attribute.already.specified.as.positional.pattern.at.position", keywordName, positionalIndex))
          .fix(RemoveListMemberFix(keywordPattern))
          .register()
      }
    }
  }

  override fun visitPyClass(node: PyClass) {
    val matchArgs = node
      .findClassAttribute(PyNames.MATCH_ARGS, false, myTypeEvalContext)
      ?.findAssignedValue()
      ?.let { PyPsiUtils.flattenParens(it) } ?: return
    
    val matchArgsType = myTypeEvalContext.getType(matchArgs)
    val strType = PyBuiltinCache.getInstance(matchArgs).strType
    val goodTuple = PyTupleType.createHomogeneous(matchArgs, strType)
    if (PyTypeChecker.match(goodTuple, matchArgsType, myTypeEvalContext)) return
    // __match_args__ must be a tuple[str, ...]
    holder.problem(matchArgs, "__match_args__ must be a tuple[str, ...]").register()
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

// Almost identical to PyRemoveDictKeyQuickFix
class RemoveListMemberFix(element: PyElement) : PsiUpdateModCommandAction<PyElement>(element) {
  override fun getFamilyName(): String = PyPsiBundle.message("QFIX.NAME.remove.list.member")
  override fun getPresentation(context: ActionContext, element: PyElement): Presentation? = when (element) {
    is PyPattern -> Presentation.of(PyPsiBundle.message("QFIX.remove.pattern"))
    else -> super.getPresentation(context, element)
  }

  override fun invoke(context: ActionContext, element: PyElement, updater: ModPsiUpdater) {
    val nextSibling = PsiTreeUtil.skipWhitespacesForward(element)
    val prevSibling = PsiTreeUtil.skipWhitespacesBackward(element)
    element.delete()
    if (nextSibling != null && nextSibling.getNode().getElementType() == PyTokenTypes.COMMA) {
      nextSibling.delete()
      return
    }
    if (prevSibling != null && prevSibling.getNode().getElementType() == PyTokenTypes.COMMA) {
      prevSibling.delete()
    }
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
    val matchArgsValue = "(" + positionalArgs.joinToString(", ") { "'$it'" } + ")"
    val matchArgsAssignment = generator.createFromText(
      LanguageLevel.forElement(pyClass),
      PyAssignmentStatement::class.java,
      "${PyNames.MATCH_ARGS} = $matchArgsValue",
    )

    val anchor = pyClass.statementList.statements.firstOrNull()
    val result = pyClass.statementList.addBefore(matchArgsAssignment, anchor) as PyAssignmentStatement
    updater.moveCaretTo(result.textRange.endOffset - 1)
  }
}