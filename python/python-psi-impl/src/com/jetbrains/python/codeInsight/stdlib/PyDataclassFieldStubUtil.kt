package com.jetbrains.python.codeInsight.stdlib

import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.resolve.PyResolveUtil
import org.jetbrains.annotations.ApiStatus

/**
 * Shared helpers for building dataclass field stubs (stdlib / attrs / third-party).
 */
@ApiStatus.Internal
object PyDataclassFieldStubUtil {

  /**
   * Returns the field-initializer call for [field] (the direct assigned value) or `null` if it is not a call.
   */
  fun fieldInitializerCall(field: PyTargetExpression): PyCallExpression? =
    field.findAssignedValue() as? PyCallExpression

  /**
   * The locally resolved qualified names of [call]'s callee, as dotted strings.
   */
  fun calleeQualifiedNames(call: PyCallExpression): List<String> {
    val callee = call.callee as? PyReferenceExpression ?: return emptyList()
    return PyResolveUtil.resolveImportedElementQNameLocally(callee).map { it.toString() }
  }

  /**
   * Reads the field-initializer arguments shared by every framework.
   *
   * [usePositionalDefault] mirrors transform-like frameworks (`dataclass_transform` / Pydantic),
   * where the first positional argument is the default value.
   */
  fun parseCommonFieldCallArgs(
    call: PyCallExpression,
    usePositionalDefault: Boolean,
  ): FieldCallArgs? {
    val qualifiedName = (call.callee as? PyReferenceExpression)?.asQualifiedName() ?: return null
    val positionalDefault = call.arguments.firstOrNull { it !is PyKeywordArgument }
    return FieldCallArgs(
      qualifiedName = qualifiedName,
      initValue = PyEvaluator.evaluateAsBooleanNoResolve(call.getKeywordArgument("init"), true),
      kwOnly = PyEvaluator.evaluateAsBooleanNoResolve(call.getKeywordArgument("kw_only")),
      default = call.getKeywordArgument("default") ?: if (usePositionalDefault) positionalDefault else null,
      defaultFactory = call.getKeywordArgument("default_factory"),
      factory = call.getKeywordArgument("factory"),
      alias = (call.getKeywordArgument("alias") as? PyStringLiteralExpression)?.stringValue,
    )
  }

  /**
   * Raw field-initializer arguments common to all dataclass-like frameworks.
   */
  @ApiStatus.Internal
  class FieldCallArgs(
    val qualifiedName: QualifiedName,
    val initValue: Boolean,
    val kwOnly: Boolean?,
    val default: PyExpression?,
    val defaultFactory: PyExpression?,
    val factory: PyExpression?,
    val alias: String?,
  )
}
