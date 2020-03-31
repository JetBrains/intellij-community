// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typing

import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.jetbrains.python.PyCustomType
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.*
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyCallExpressionNavigator
import com.jetbrains.python.psi.impl.stubs.PyTypedDictStubImpl
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.stubs.PyTypedDictStub
import com.jetbrains.python.psi.types.*
import com.jetbrains.python.psi.types.PyTypedDictType.Companion.TYPED_DICT_FIELDS_PARAMETER
import com.jetbrains.python.psi.types.PyTypedDictType.Companion.TYPED_DICT_NAME_PARAMETER
import com.jetbrains.python.psi.types.PyTypedDictType.Companion.TYPED_DICT_TOTAL_PARAMETER
import java.util.*
import java.util.stream.Collectors

typealias TDFields = LinkedHashMap<String, PyTypedDictType.FieldTypeAndTotality>

class PyTypedDictTypeProvider : PyTypeProviderBase() {
  override fun getReferenceExpressionType(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyType? {
    return getTypedDictTypeForCallee(referenceExpression, context)
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
      if (checkIfClassIsDirectTypedDictInheritor(cls, context)) return true
      val ancestors = cls.getAncestorTypes(context)

      return ancestors.any(isTypingTD)
    }

    /**
     * This method helps to avoid the situation when two processes try to get an element's type simultaneously
     * and one of them ends up with null.
     */
    private fun checkIfClassIsDirectTypedDictInheritor(cls: PyClass, context: TypeEvalContext): Boolean {
      val stub = cls.stub
      if (context.maySwitchToAST(cls) || stub == null) {
        return cls.superClassExpressions.any { isTypedDict(it, context) }
      }
      else {
        return stub.superClassesText.any { isTypedDict(PyUtil.createExpressionFromFragment(it, cls) ?: return false, context) }
      }
    }

    fun getTypedDictTypeForResolvedCallee(referenceTarget: PsiElement, context: TypeEvalContext): PyTypedDictType? {
      return when (referenceTarget) {
        is PyClass -> getTypedDictTypeForTypingTDInheritorAsCallee(referenceTarget, context, false)
        is PyTargetExpression -> getTypedDictTypeForTarget(referenceTarget, context)
        else -> null
      }
    }

    private fun getTypedDictTypeForCallee(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyType? {
      if (PyCallExpressionNavigator.getPyCallExpressionByCallee(referenceExpression) == null) return null

      val resolveContext = PyResolveContext.defaultContext().withTypeEvalContext(context)
      val resolveResults = referenceExpression.getReference(resolveContext).multiResolve(false)

      for (element in PyUtil.filterTopPriorityResults(resolveResults)) {
        if (element is PyTargetExpression) {
          val result = getTypedDictTypeForTarget(element, context)
          if (result != null) {
            return result
          }
        }

        if (element is PyClass) {
          val result = getTypedDictTypeForTypingTDInheritorAsCallee(element, context, false)
          if (result != null) {
            return result
          }
        }

        if (element is PyTypedElement) {
          val type = context.getType(element)
          if (type is PyClassType) {
            if (isTypingTypedDictInheritor(type.pyClass, context)) {
              return getTypedDictTypeForTypingTDInheritorAsCallee(type.pyClass, context, false)
            }
          }
        }

        if (isTypedDict(referenceExpression, context)) {
          val parameters = mutableListOf<PyCallableParameter>()

          val builtinCache = PyBuiltinCache.getInstance(referenceExpression)
          val languageLevel = LanguageLevel.forElement(referenceExpression)
          val generator = PyElementGenerator.getInstance(referenceExpression.project)

          parameters.add(PyCallableParameterImpl.nonPsi(TYPED_DICT_NAME_PARAMETER, builtinCache.getStringType(languageLevel)))
          val dictClassType = builtinCache.dictType
          parameters.add(PyCallableParameterImpl.nonPsi(TYPED_DICT_FIELDS_PARAMETER,
                                                        if (dictClassType != null) PyCollectionTypeImpl(dictClassType.pyClass, false,
                                                                                                        listOf(builtinCache.strType, null))
                                                        else dictClassType))
          parameters.add(
            PyCallableParameterImpl.nonPsi(TYPED_DICT_TOTAL_PARAMETER,
                                           builtinCache.boolType,
                                           generator.createExpressionFromText(languageLevel, PyNames.TRUE)))

          return PyCallableTypeImpl(parameters, null)
        }
      }

      return null
    }

    private fun getTypedDictTypeForTypingTDInheritorAsCallee(cls: PyClass,
                                                             context: TypeEvalContext,
                                                             isInstance: Boolean): PyTypedDictType? {
      if (isTypingTypedDictInheritor(cls, context)) {
        return PyTypedDictType(cls.name ?: return null,
                               TDFields(collectFields(cls, context)),
                               false,
                               PyBuiltinCache.getInstance(cls).dictType?.pyClass ?: return null,
                               if (isInstance) PyTypedDictType.DefinitionLevel.INSTANCE else PyTypedDictType.DefinitionLevel.NEW_TYPE,
                               cls.getAncestorTypes(context).filterIsInstance<PyTypedDictType>())
      }

      return null
    }

    private fun collectFields(cls: PyClass, context: TypeEvalContext): TDFields {
      val fields = mutableMapOf<String, PyTypedDictType.FieldTypeAndTotality>()
      val ancestors = cls.getAncestorTypes(context)
      val typedDictCustomTypeIndex = ancestors.indexOfFirst { it is PyCustomType && nameIsTypedDict(it.classQName) }
      // When some ancestor is located in another file its type will be PyClassType because of difference in getting ancestor types.
      // (see com.jetbrains.python.psi.impl.PyClassImpl.fillSuperClassesNoSwitchToAst)
      // When AST is unavailable, the type of resolved element is returned, TypedDict reference is resolved to PyClass,
      // therefore the type of that PyClass is PyClassType.
      // That's why in case when AST for ancestors is unavailable we need to collect fields from PyClassType instances.
      if (typedDictCustomTypeIndex > 0) {
        ancestors.take(typedDictCustomTypeIndex)
          .forEach { if (it is PyClassType) fields.putAll(collectTypingTDInheritorFields(it.pyClass, context)) }
      }
      else {
        ancestors.forEach { if (it is PyTypedDictType) fields.putAll(it.fields) }
      }
      fields.putAll(collectTypingTDInheritorFields(cls, context))
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

      val totality = getTotality(cls)
      val toTDFields = Collectors.toMap<PyTargetExpression, String, PyTypedDictType.FieldTypeAndTotality, TDFields>(
        { it.name },
        { field -> PyTypedDictType.FieldTypeAndTotality(context.getType(field), totality) },
        { _, v2 -> v2 },
        { TDFields() })

      return fields.stream().collect(toTDFields)
    }

    private fun getTotality(cls: PyClass): Boolean {
      return if (cls.stub != null) {
        "total=False" !in cls.stub.superClassesText
      }
      else {
        (cls.superClassExpressionList?.getKeywordArgument("total")?.valueExpression as? PyBoolLiteralExpression)?.value ?: true
      }
    }

    private fun getTypedDictTypeForTarget(target: PyTargetExpression, context: TypeEvalContext): PyTypedDictType? {
      val stub = target.stub

      return if (stub != null) {
        getTypedDictTypeFromStub(target,
                                 stub.getCustomStub(PyTypedDictStub::class.java),
                                 context,
                                 false)
      }
      else getTypedDictTypeFromAST(target, context)
    }

    fun getTypedDictTypeForResolvedElement(resolved: PsiElement, context: TypeEvalContext): PyType? {
      if (resolved is PyClass && isTypingTypedDictInheritor(resolved, context)) {
        return getTypedDictTypeForTypingTDInheritorAsCallee(resolved, context, true)
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
        getTypedDictTypeFromStub(expression, PyTypedDictStubImpl.create(expression), context, true)
      }
      else null
    }

    private fun getTypedDictTypeFromAST(expression: PyTargetExpression, context: TypeEvalContext): PyTypedDictType? {
      return if (context.maySwitchToAST(expression)) {
        getTypedDictTypeFromStub(expression, PyTypedDictStubImpl.create(expression), context, false)
      }
      else null
    }

    private fun getTypedDictTypeFromStub(referenceTarget: PsiElement,
                                         stub: PyTypedDictStub?,
                                         context: TypeEvalContext,
                                         isInstance: Boolean): PyTypedDictType? {
      if (stub == null) return null

      val dictClass = PyBuiltinCache.getInstance(referenceTarget).dictType?.pyClass
      if (dictClass == null) return null
      val fields = stub.fields
      val total = stub.isRequired
      val typedDictFields = parseTypedDictFields(referenceTarget, fields, context, total)

      return PyTypedDictType(stub.name,
                             typedDictFields,
                             false,
                             dictClass,
                             if (isInstance) PyTypedDictType.DefinitionLevel.INSTANCE else PyTypedDictType.DefinitionLevel.NEW_TYPE,
                             emptyList(),
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
      if (expression is PyDictLiteralExpression) {
        val fields = getTypingTDFieldsFromDictLiteral(expression, context)
        if (fields != null) {
          val dictClass = PyBuiltinCache.getInstance(expression).dictType?.pyClass
          if (dictClass == null) return null
          return PyTypedDictType("TypedDict", fields, true, dictClass,
                                 PyTypedDictType.DefinitionLevel.INSTANCE,
                                 emptyList())
        }
      }
      else if (expression is PyCallExpression) {
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
                                     emptyList())
            }
          }
        }
      }
      return null
    }

    private fun getTypingTDFieldsFromDictLiteral(dictLiteral: PyDictLiteralExpression, context: TypeEvalContext): TDFields? {
      val fields = LinkedHashMap<String, PyExpression?>()

      dictLiteral.elements.forEach {
        val name = it.key
        val value = it.value

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
