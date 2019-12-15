// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typing

import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.*
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyCallExpressionNavigator
import com.jetbrains.python.psi.impl.stubs.PyTypedDictStubImpl
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.stubs.PyTypedDictStub
import com.jetbrains.python.psi.types.*
import java.util.*
import java.util.stream.Collectors

typealias TDFields = LinkedHashMap<String, PyTypedDictType.FieldTypeAndTotality>

class PyTypedDictTypeProvider : PyTypeProviderBase() {
  override fun getReferenceExpressionType(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyType? {
    return getTypedDictTypeForCallee(referenceExpression, context)
  }

  override fun getReferenceType(referenceTarget: PsiElement, context: TypeEvalContext, anchor: PsiElement?): Ref<PyType>? {
    return PyTypeUtil.notNullToRef(getTypedDictTypeForResolvedCallee(referenceTarget, context))
  }

  companion object {
    val nameIsTypedDict = { name: String? -> name == TYPED_DICT || name == TYPED_DICT_EXT }

    fun isTypedDict(expression: PyExpression, context: TypeEvalContext): Boolean {
      return resolveToQualifiedNames(expression, context).any(nameIsTypedDict)
    }

    fun isTypingTypedDictInheritor(cls: PyClass, context: TypeEvalContext): Boolean {
      val isTypingTD = { type: PyClassLikeType? ->
        type is PyTypedDictType || nameIsTypedDict(type?.classQName)
      }
      val ancestors = cls.getAncestorTypes(context)

      return ancestors.any(isTypingTD)
    }

    fun getTypedDictTypeForResolvedCallee(referenceTarget: PsiElement, context: TypeEvalContext): PyTypedDictType? {
      return when (referenceTarget) {
        is PyClass -> getTypedDictTypeForTypingTDInheritorAsCallee(referenceTarget, context)
        is PyTargetExpression -> getTypedDictTypeForTarget(referenceTarget, context)
        else -> null
      }
    }

    private fun getTypingTypedDictTypeForResolvedCallee(referenceTarget: PyClass, context: TypeEvalContext): PyTypedDictType? {
      return getTypedDictTypeForTypingTDInheritorAsCallee(referenceTarget, context, true)
    }

    private fun getTypedDictTypeForCallee(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyType? {
      if (PyCallExpressionNavigator.getPyCallExpressionByCallee(referenceExpression) == null) return null

      val resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context)
      val resolveResults = referenceExpression.getReference(resolveContext).multiResolve(false)

      for (element in PyUtil.filterTopPriorityResults(resolveResults)) {
        if (element is PyTargetExpression) {
          val result = getTypedDictTypeForTarget(element, context)
          if (result != null) {
            return result
          }
        }

        if (element is PyClass) {
          val result = getTypedDictTypeForTypingTDInheritorAsCallee(element, context)
          if (result != null) {
            return result
          }
        }

        if (element is PyTypedElement) {
          val type = context.getType(element)
          if (type is PyClassType) {
            if (isTypingTypedDictInheritor(type.pyClass, context)) {
              return getTypedDictTypeForTypingTDInheritorAsCallee(type.pyClass, context)
            }
          }
        }

        if (isTypedDict(referenceExpression, context)) {
          val parameters = mutableListOf<PyCallableParameter>()

          val builtinCache = PyBuiltinCache.getInstance(referenceExpression)
          val languageLevel = LanguageLevel.forElement(referenceExpression)
          val generator = PyElementGenerator.getInstance(referenceExpression.project)

          parameters.add(PyCallableParameterImpl.nonPsi("name", builtinCache.getStringType(languageLevel)))
          parameters.add(PyCallableParameterImpl.nonPsi("fields", builtinCache.dictType))
          parameters.add(
            PyCallableParameterImpl.nonPsi("total", builtinCache.boolType, generator.createExpressionFromText(languageLevel, "True")))

          return PyCallableTypeImpl(parameters, null)
        }
      }

      return null
    }

    private fun getTypedDictTypeForTypingTDInheritorAsCallee(cls: PyClass, context: TypeEvalContext): PyTypedDictType? {
      return getTypedDictTypeForTypingTDInheritorAsCallee(cls, context, false)
    }

    private fun getTypedDictTypeForTypingTDInheritorAsCallee(cls: PyClass,
                                                             context: TypeEvalContext,
                                                             isInstance: Boolean): PyTypedDictType? {
      if (isTypingTypedDictInheritor(cls, context)) {
        val ancestors = cls.getAncestorTypes(context).filterIsInstance<PyTypedDictType>()
        val name = cls.name ?: return null
        val fields = collectFields(cls, context)
        val overallFields = mutableMapOf<String, PyTypedDictType.FieldTypeAndTotality>()
        overallFields.putAll(fields.filter { it.value.isRequired })
        overallFields.putAll(fields.filter { !it.value.isRequired })

        val dictClass = PyBuiltinCache.getInstance(cls).dictType?.pyClass
        if (dictClass == null) return null

        return PyTypedDictType(name,
                               TDFields(overallFields),
                               false,
                               dictClass,
                               if (isInstance) PyTypedDictType.DefinitionLevel.INSTANCE else PyTypedDictType.DefinitionLevel.NEW_TYPE,
                               ancestors)
      }

      return null
    }

    private fun collectFields(cls: PyClass, context: TypeEvalContext): TDFields {
      val fields = mutableMapOf<String, PyTypedDictType.FieldTypeAndTotality>()
      fields.putAll(collectTypingTDInheritorFields(cls, context))
      val ancestors = cls.getAncestorTypes(context)
      ancestors.forEach { if (it is PyTypedDictType) fields.putAll(it.fields) }
      return TDFields(fields)
    }

    private fun collectTypingTDInheritorFields(cls: PyClass, context: TypeEvalContext): TDFields {
      val type = cls.getType(context)
      if (type is PyTypedDictType) {
        return TDFields(type.fields)
      }

      val fields = mutableListOf<PyTargetExpression>()

      cls.processClassLevelDeclarations { element, _ ->
        if (element is PyTargetExpression && element.annotationValue != null) {
          fields.add(element)
        }

        true
      }

      val argumentList: PyArgumentList? = cls.children.filterIsInstance<PyArgumentList>().firstOrNull()
      val totalityValue = argumentList?.getKeywordArgument("total")
      val fieldsRequired = if (totalityValue != null && totalityValue.valueExpression is PyBoolLiteralExpression)
        (totalityValue.valueExpression as PyBoolLiteralExpression).value
      else true

      val toTDFields = Collectors.toMap<PyTargetExpression, String, PyTypedDictType.FieldTypeAndTotality, TDFields>(
        { it.name },
        { field -> PyTypedDictType.FieldTypeAndTotality(context.getType(field), fieldsRequired) },
        { _, v2 -> v2 },
        { TDFields() })

      return fields.stream().collect(toTDFields)
    }

    private fun getTypedDictTypeForTarget(target: PyTargetExpression, context: TypeEvalContext): PyTypedDictType? {
      val stub = target.stub

      return if (stub != null) {
        getTypedDictTypeFromStub(target,
                                 stub.getCustomStub(PyTypedDictStub::class.java),
                                 context)
      }
      else getTypedDictTypeFromAST(target, context)
    }

    fun getTypedDictTypeForResolvedElement(resolved: PsiElement, context: TypeEvalContext): PyType? {
      if (resolved is PyClass && isTypingTypedDictInheritor(resolved, context)) {
        return getTypingTypedDictTypeForResolvedCallee(resolved, context)
      }
      if (resolved is PyCallExpression) {
        val callee = resolved.callee
        if (callee is PyReferenceExpression && isTypedDict(callee, context)) {
          val type = getTypedDictTypeFromAST(resolved, context)
          if (type != null) {
            return type
          }
        }
      }

      return null
    }

    private fun getTypedDictTypeFromAST(expression: PyCallExpression, context: TypeEvalContext): PyTypedDictType? {
      return if (context.maySwitchToAST(expression)) {
        getTypedDictTypeFromStub(expression, PyTypedDictStubImpl.create(expression), context)
      }
      else null
    }

    private fun getTypedDictTypeFromAST(expression: PyTargetExpression, context: TypeEvalContext): PyTypedDictType? {
      return if (context.maySwitchToAST(expression)) {
        getTypedDictTypeFromStub(expression, PyTypedDictStubImpl.create(expression), context)
      }
      else null
    }

    private fun getTypedDictTypeFromStub(referenceTarget: PsiElement,
                                         stub: PyTypedDictStub?,
                                         context: TypeEvalContext): PyTypedDictType? {
      if (stub == null) return null

      val dictClass = PyBuiltinCache.getInstance(referenceTarget).dictType?.pyClass
      if (dictClass == null) return null
      val fields = stub.fields
      val total = stub.isTotal
      val typedDictFields = parseTypedDictFields(referenceTarget, fields, context, total)

      return PyTypedDictType(stub.name,
                             typedDictFields,
                             false,
                             dictClass,
                             PyTypedDictType.DefinitionLevel.NEW_TYPE,
                             listOf(),
                             referenceTarget as? PyTargetExpression)
    }

    private fun parseTypedDictFields(anchor: PsiElement,
                                     fields: Map<String, Optional<String>>,
                                     context: TypeEvalContext,
                                     total: Boolean): TDFields {
      val result = TDFields()
      for ((name, type) in fields) {
        result[name] = parseTypedDictField(anchor, type.orElse(null), context, total)
      }
      return result
    }

    private fun parseTypedDictField(anchor: PsiElement,
                                    type: String?,
                                    context: TypeEvalContext,
                                    total: Boolean): PyTypedDictType.FieldTypeAndTotality {
      if (type == null) return PyTypedDictType.FieldTypeAndTotality(null)

      val pyType = Ref.deref(getStringBasedType(type, anchor, context))
      return PyTypedDictType.FieldTypeAndTotality(pyType, total)
    }

    /**
     * If [expected] type is `typing.TypedDict[...]`,
     * then tries to infer `typing.TypedDict[...]` for [expression],
     * otherwise returns type inferred by [context].
     */
    fun promoteToTypedDict(expression: PyExpression, expected: PyType?, context: TypeEvalContext): PyType? {
      if (expected is PyTypedDictType) {
        return fromValue(expression, context) ?: context.getType(expression)
      }
      else {
        return context.getType(expression)
      }
    }

    /**
     * Tries to construct TypedDict type for a value that could be considered as TypedDict and downcasted to `typing.TypedDict[...]` type.
     */
    private fun fromValue(expression: PyExpression, context: TypeEvalContext): PyType? = newInstance(expression, context)

    private fun newInstance(expression: PyExpression, context: TypeEvalContext): PyType? {
      return when (expression) {
        is PyTupleExpression -> {
          val elements = expression.elements
          val classes = elements.mapNotNull { toTypedDictType(it, context) }
          if (elements.size == classes.size) PyUnionType.union(classes) else null
        }
        else -> toTypedDictType(expression, context)
      }
    }

    private fun toTypedDictType(expression: PyExpression, context: TypeEvalContext): PyType? {
      if (expression is PyNoneLiteralExpression &&
          !expression.isEllipsis ||
          expression is PyReferenceExpression &&
          expression.name == PyNames.NONE &&
          LanguageLevel.forElement(expression).isPython2) return PyNoneType.INSTANCE

      if (expression is PyDictLiteralExpression) {
        val fields = getTypingTDFieldsFromDictLiteral(expression, context)
        if (fields != null) {
          val dictClass = PyBuiltinCache.getInstance(expression).dictType?.pyClass
          if (dictClass == null) return null
          return PyTypedDictType("TypedDict", fields, true, dictClass,
                                 PyTypedDictType.DefinitionLevel.INSTANCE,
                                 listOf())
        }
      } else if (expression is PyCallExpression) {
        val resolvedQualifiedNames = if (expression.callee != null) resolveToQualifiedNames(expression.callee!!, context) else return null
        if (resolvedQualifiedNames.any { it == PyNames.DICT }) {
          val arguments = expression.arguments
          if (arguments.size > 1) {
            val fields = getTypingTDFieldsFromDictKeywordArguments(arguments, context)
            if (fields != null) {
              val dictClass = PyBuiltinCache.getInstance(expression).dictType?.pyClass
              if (dictClass == null) return null
              return PyTypedDictType("TypedDict", fields, true, dictClass,
                                     PyTypedDictType.DefinitionLevel.INSTANCE,
                                     listOf())
            }
          }
        }
      }
      return null
    }

    private fun getTypingTDFieldsFromDictLiteral(dictLiteral: PyDictLiteralExpression, context: TypeEvalContext): TDFields? {
      val fields = LinkedHashMap<String, PyExpression?>()

      dictLiteral.elements.forEach {
        val name: PyExpression = it.key
        val value: PyExpression? = it.value

        if (name !is PyStringLiteralExpression) return null

        fields[name.stringValue] = value
      }

      return typedDictFieldsFromKeysAndValues(fields, context)
    }

    private fun getTypingTDFieldsFromDictKeywordArguments(keywordArguments: Array<PyExpression>, context: TypeEvalContext): TDFields? {
      val fields = LinkedHashMap<String, PyExpression?>()

      keywordArguments.forEach {
        if (it !is PyKeywordArgument || it.keyword == null) return null
        fields[it.keyword!!] = it.valueExpression
      }

      return typedDictFieldsFromKeysAndValues(fields, context)
    }

    private fun typedDictFieldsFromKeysAndValues(fields: Map<String, PyExpression?>, context: TypeEvalContext): TDFields? {
      val result = TDFields()
      for ((name, type) in fields) {
        result[name] = if (type != null) PyTypedDictType.FieldTypeAndTotality(context.getType(type))
        else PyTypedDictType.FieldTypeAndTotality(null)
      }
      return result
    }
  }
}