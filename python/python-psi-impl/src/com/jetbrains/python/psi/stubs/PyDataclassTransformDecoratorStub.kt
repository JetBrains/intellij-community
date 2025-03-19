package com.jetbrains.python.psi.stubs

import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.util.QualifiedName
import com.intellij.util.io.DataInputOutputUtil
import com.jetbrains.python.codeInsight.PyDataclassNames
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.impl.stubs.PyCustomDecoratorStub
import com.jetbrains.python.psi.resolve.PyResolveUtil
import java.io.IOException

/**
 * Represents arguments of `typing.dataclass_transform` or `typing_extensions.dataclass_transform` decorators
 * applied to another decorator function or a base class for creating new dataclasses.
 *
 * Omitted arguments are expected to be replaced by their default values by the caller creating a new instance.
 *
 * See https://docs.python.org/3.13/library/typing.html#typing.dataclass_transform.
 */
data class PyDataclassTransformDecoratorStub(
  val eqDefault: Boolean,
  val orderDefault: Boolean,
  val kwOnlyDefault: Boolean,
  val frozenDefault: Boolean,
  val fieldSpecifiers: List<QualifiedName>,
) : PyCustomDecoratorStub {

  override fun getTypeClass(): Class<PyDataclassTransformDecoratorStubType> {
    return PyDataclassTransformDecoratorStubType::class.java
  }

  @Throws(IOException::class)
  override fun serialize(stream: StubOutputStream) {
    stream.writeBoolean(eqDefault)
    stream.writeBoolean(orderDefault)
    stream.writeBoolean(kwOnlyDefault)
    stream.writeBoolean(frozenDefault)
    DataInputOutputUtil.writeSeq(stream, fieldSpecifiers) { QualifiedName.serialize(it, stream) }
  }

  companion object {
    fun deserialize(stream: StubInputStream): PyDataclassTransformDecoratorStub {
      val eqDefault = stream.readBoolean()
      val orderDefault = stream.readBoolean()
      val kwOnlyDefault = stream.readBoolean()
      val frozenDefault = stream.readBoolean()
      val fieldSpecifiers = DataInputOutputUtil.readSeq(stream) { QualifiedName.deserialize(stream)!! }

      return PyDataclassTransformDecoratorStub(
        eqDefault = eqDefault,
        orderDefault = orderDefault,
        kwOnlyDefault = kwOnlyDefault,
        frozenDefault = frozenDefault,
        fieldSpecifiers = fieldSpecifiers,
      )
    }

    fun create(decorator: PyDecorator): PyDataclassTransformDecoratorStub? {
      val decoratorName = decorator.callee as? PyReferenceExpression ?: return null
      val importedAsDataclassTransform = PyResolveUtil.resolveImportedElementQNameLocally(decoratorName)
        .any { it.toString().contains(PyDataclassNames.DataclassTransform.DATACLASS_TRANSFORM_NAME) }
      if (importedAsDataclassTransform) {
        val fieldSpecifierList = PyUtil.peelArgument(decorator.getKeywordArgument("field_specifiers")) as? PyTupleExpression
        return PyDataclassTransformDecoratorStub(
          eqDefault = PyEvaluator.evaluateAsBooleanNoResolve(decorator.getKeywordArgument("eq_default"), true),
          orderDefault = PyEvaluator.evaluateAsBooleanNoResolve(decorator.getKeywordArgument("order_default"), false),
          kwOnlyDefault = PyEvaluator.evaluateAsBooleanNoResolve(decorator.getKeywordArgument("kw_only_default"), false),
          frozenDefault = PyEvaluator.evaluateAsBooleanNoResolve(decorator.getKeywordArgument("frozen_default"), false),
          fieldSpecifiers = fieldSpecifierList?.elements?.mapNotNull { (it as? PyReferenceExpression)?.asQualifiedName() } ?: emptyList(),
        )
      }
      return null
    }
  }
}
