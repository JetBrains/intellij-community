// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typing

import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.jetbrains.python.PyCustomType
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.*
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyCallExpressionNavigator
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.impl.StubAwareComputation
import com.jetbrains.python.psi.impl.stubs.PyTypedDictStubImpl
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.stubs.PyTypedDictFieldStub
import com.jetbrains.python.psi.stubs.PyTypedDictStub
import com.jetbrains.python.psi.types.*
import com.jetbrains.python.psi.types.PyTypedDictType.Companion.TYPED_DICT_TOTAL_PARAMETER
import java.util.*
import java.util.stream.Collectors

typealias TDFields = LinkedHashMap<String, PyTypedDictType.FieldTypeAndTotality>

class PyTypedDictTypeProvider : PyTypeProviderBase() {
  override fun getReferenceExpressionType(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyType? {
    return getTypedDictTypeForCallee(referenceExpression, context) ?: getTypedDictGetType(referenceExpression, context)
  }

  override fun getReferenceType(referenceTarget: PsiElement, context: TypeEvalContext, anchor: PsiElement?): Ref<PyType>? {
    return PyTypeUtil.notNullToRef(getTypedDictTypeForResolvedCallee(referenceTarget, context))
  }

  override fun prepareCalleeTypeForCall(type: PyType?, call: PyCallExpression, context: TypeEvalContext): Ref<PyCallableType?>? {
    return if (type is PyTypedDictType) Ref.create(type) else null
  }

  companion object {
    val nameIsTypedDict = { name: String? -> name == TYPED_DICT || name == TYPED_DICT_EXT }

    fun isGetMethodToOverride(call: PyCallExpression, context: TypeEvalContext): Boolean {
      val callee = call.callee
      return callee != null && resolveToQualifiedNames(callee, context).any { it == "dict.get" /* py3 */ || it == MAPPING_GET /* py2 */ }
    }

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

    private fun getTypedDictGetType(referenceTarget: PsiElement, context: TypeEvalContext): PyCallableType? {
      val callExpression =
        if (context.maySwitchToAST(referenceTarget)) PyCallExpressionNavigator.getPyCallExpressionByCallee(referenceTarget) else null
      if (callExpression == null || callExpression.callee == null) return null
      val receiver = callExpression.getReceiver(null) ?: return null
      val type = context.getType(receiver)
      if (type !is PyTypedDictType) return null

      if (isGetMethodToOverride(callExpression, context)) {
        val parameters = mutableListOf<PyCallableParameter>()
        val builtinCache = PyBuiltinCache.getInstance(referenceTarget)
        val elementGenerator = PyElementGenerator.getInstance(referenceTarget.project)
        parameters.add(PyCallableParameterImpl.nonPsi("key", builtinCache.strType))
        parameters.add(PyCallableParameterImpl.nonPsi("default", null,
                                                      elementGenerator.createExpressionFromText(LanguageLevel.forElement(referenceTarget),
                                                                                                "None")))
        val key = PyEvaluator.evaluate(callExpression.getArgument(0, "key", PyExpression::class.java), String::class.java)
        val defaultArgument = callExpression.getArgument(1, "default", PyExpression::class.java)
        val default = if (defaultArgument != null) context.getType(defaultArgument) else builtinCache.noneType
        val valueTypeAndTotality = type.fields[key]
        return PyCallableTypeImpl(parameters,
                                  when {
                                    valueTypeAndTotality == null -> default
                                    valueTypeAndTotality.qualifiers.isRequired == true -> valueTypeAndTotality.type
                                    else -> PyUnionType.union(valueTypeAndTotality.type, default)
                                  })
      }

      return null
    }

    private fun getTypedDictTypeForResolvedCallee(referenceTarget: PsiElement, context: TypeEvalContext): PyTypedDictType? {
      return when (referenceTarget) {
        is PyClass -> getTypedDictTypeForTypingTDInheritorAsCallee(referenceTarget, context, false)
        is PyTargetExpression -> getTypedDictTypeForTarget(referenceTarget, context)
        else -> null
      }
    }

    private fun getTypedDictTypeForCallee(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyType? {
      if (PyCallExpressionNavigator.getPyCallExpressionByCallee(referenceExpression) == null) return null

      if (isTypedDict(referenceExpression, context)) {
        val builtinCache = PyBuiltinCache.getInstance(referenceExpression)
        val languageLevel = LanguageLevel.forElement(referenceExpression)
        val generator = PyElementGenerator.getInstance(referenceExpression.project)

        val dictType = builtinCache.dictType
        val strToTypeDictType = if (dictType != null) {
          PyCollectionTypeImpl(dictType.pyClass, false, listOf(builtinCache.strType, builtinCache.typeType))
        }
        else {
          null
        }

        val parameters = listOf(
          PyCallableParameterImpl.nonPsi("typename", builtinCache.getStringType(languageLevel)),
          PyCallableParameterImpl.nonPsi("fields", strToTypeDictType),
          PyCallableParameterImpl.psi(generator.createSingleStarParameter()),
          PyCallableParameterImpl.psi(generator.createSlashParameter()),
          PyCallableParameterImpl.nonPsi(TYPED_DICT_TOTAL_PARAMETER,
                                         builtinCache.boolType,
                                         generator.createExpressionFromText(languageLevel, PyNames.TRUE))
        )

        return PyCallableTypeImpl(parameters, null)
      }

      return null
    }

    private fun getTypedDictTypeForTypingTDInheritorAsCallee(
      cls: PyClass,
      context: TypeEvalContext,
      isInstance: Boolean,
    ): PyTypedDictType? {
      if (isTypingTypedDictInheritor(cls, context)) {
        return PyTypedDictType(cls.name ?: return null,
                               TDFields(collectFields(cls, context)),
                               PyBuiltinCache.getInstance(cls).dictType?.pyClass ?: return null,
                               if (isInstance) PyTypedDictType.DefinitionLevel.INSTANCE else PyTypedDictType.DefinitionLevel.NEW_TYPE,
                               cls.getAncestorTypes(context).filterIsInstance<PyTypedDictType>(),
                               cls)
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

      val fields = mutableListOf<Pair<PyExpression, PyTypedDictType.TypedDictFieldQualifiers>>()
      val totality = getTotality(cls)
      cls.processClassLevelDeclarations { element, _ ->
        if (element is PyTargetExpression) {
          val stub = element.stub
          if (context.maySwitchToAST(cls) || stub == null) {
            if (element.annotation != null) {
              fields.add(Pair(element, checkTypeSpecification(element.annotation!!.value, context, totality)))
            }
          }
          else {
            if (stub.annotation != null) {
              val annotation = PyUtil.createExpressionFromFragment(stub.annotation!!, cls)
              fields.add(Pair(stub.psi, checkTypeSpecification(annotation, context, totality)))
            }
          }
        }
        true
      }


      val toTDFields = Collectors.toMap<Pair<PyExpression, PyTypedDictType.TypedDictFieldQualifiers>, String, PyTypedDictType.FieldTypeAndTotality, TDFields>(
        { it.first.name },
        { field -> PyTypedDictType.FieldTypeAndTotality(field.first, context.getType(field.first), field.second) },
        { _, v2 -> v2 },
        { TDFields() })

      return fields.stream().collect(toTDFields)
    }

    private fun checkTypeSpecification(annotation: PyExpression?, context: TypeEvalContext, totality: Boolean): PyTypedDictType.TypedDictFieldQualifiers {
      if (annotation is PySubscriptionExpression) {
        return parseTypedDictFieldQualifiers(annotation, context, totality = totality)
      }
      return PyTypedDictType.TypedDictFieldQualifiers(isRequired = totality)
    }

    private fun parseTypedDictFieldQualifiers(expression: PySubscriptionExpression, context: TypeEvalContext, totality: Boolean? = null): PyTypedDictType.TypedDictFieldQualifiers {
      var isRequired = totality
      var isReadOnly = false
      for (qualifier in getTypedDictFieldQualifiers(expression, context)) {
        when (qualifier) {
          TypedDictFieldQualifier.REQUIRED -> isRequired = true
          TypedDictFieldQualifier.NOT_REQUIRED -> isRequired = false
          TypedDictFieldQualifier.READ_ONLY -> isReadOnly = true
        }
      }
      return PyTypedDictType.TypedDictFieldQualifiers(isRequired = isRequired, isReadOnly = isReadOnly)
    }

    fun getTypedDictFieldQualifiers(expression: PySubscriptionExpression, context: TypeEvalContext): List<TypedDictFieldQualifier> {
      val result = mutableListOf<TypedDictFieldQualifier>()
      expression.accept(object : PyRecursiveElementVisitor() {
        override fun visitPySubscriptionExpression(node: PySubscriptionExpression) {
          val resolvedNames = resolveToQualifiedNames(node.operand, context)
          if (resolvedNames.any { name -> REQUIRED == name || REQUIRED_EXT == name }) {
            result.add(TypedDictFieldQualifier.REQUIRED)
          }
          else if (resolvedNames.any { name -> NOT_REQUIRED == name || NOT_REQUIRED_EXT == name }) {
            result.add(TypedDictFieldQualifier.NOT_REQUIRED)
          }
          else if (resolvedNames.any { name -> READONLY == name || READONLY_EXT == name }) {
            result.add(TypedDictFieldQualifier.READ_ONLY)
          }
          super.visitPySubscriptionExpression(node)
        }
      })
      return result
    }

    enum class TypedDictFieldQualifier {
      REQUIRED,
      NOT_REQUIRED,
      READ_ONLY
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
      return StubAwareComputation.on(target)
        .withCustomStub { it.getCustomStub(PyTypedDictStub::class.java) }
        .overStub { getTypedDictTypeFromStub(target, it, context, false) }
        .withStubBuilder { PyTypedDictStubImpl.create(it) }
        .compute(context)
    }

    fun getTypedDictTypeForResolvedElement(resolved: PsiElement, context: TypeEvalContext): PyTypedDictType? {
      return Ref.deref(PyUtil.getParameterizedCachedValue(resolved, context) { typeEvalContext ->
        return@getParameterizedCachedValue Ref.create(calculateTypeDictType(resolved, typeEvalContext))
      })
    }

    private fun calculateTypeDictType(resolved: PsiElement, context: TypeEvalContext): PyTypedDictType? {
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

    private fun getTypedDictTypeFromAST(call: PyCallExpression, context: TypeEvalContext): PyTypedDictType? {
      return if (context.maySwitchToAST(call)) {
        getTypedDictTypeFromStub(call, PyTypedDictStubImpl.create(call), context, true)
      }
      else null
    }

    private fun getTypedDictTypeFromStub(
      targetOrCall: PsiElement,
      stub: PyTypedDictStub?,
      context: TypeEvalContext,
      isInstance: Boolean,
    ): PyTypedDictType? {
      if (stub == null) return null

      val dictClass = PyBuiltinCache.getInstance(targetOrCall).dictType?.pyClass
      if (dictClass == null) return null
      val fields = stub.fields
      val total = stub.isRequired
      val typedDictFields = parseTypedDictFields(targetOrCall, fields, context, total)

      return PyTypedDictType(stub.name,
                             typedDictFields,
                             dictClass,
                             if (isInstance) PyTypedDictType.DefinitionLevel.INSTANCE else PyTypedDictType.DefinitionLevel.NEW_TYPE,
                             emptyList(),
                             getDeclaration(targetOrCall))
    }

    private fun parseTypedDictFields(
      anchor: PsiElement,
      fields: List<PyTypedDictFieldStub>,
      context: TypeEvalContext,
      total: Boolean,
    ): TDFields {
      val result = TDFields()
      for (field in fields) {
        result[field.name] = parseTypedDictField(anchor, field.type, context, total)
      }
      return result
    }

    private fun getDeclaration(targetOrCall: PsiElement): PyQualifiedNameOwner? {
      return when (targetOrCall) {
        is PyTargetExpression -> targetOrCall
        is PyCallExpression -> (targetOrCall.parent as? PyAssignmentStatement)?.leftHandSideExpression as? PyTargetExpression
        else -> null
      }
    }

    private fun parseTypedDictField(
      anchor: PsiElement,
      type: String?,
      context: TypeEvalContext,
      total: Boolean,
    ): PyTypedDictType.FieldTypeAndTotality {
      if (type == null) return PyTypedDictType.FieldTypeAndTotality(null, null)

      val valueTypeWithQualifiers = getStringBasedTypeForTypedDict(type, anchor, context)
      if (valueTypeWithQualifiers == null) return PyTypedDictType.FieldTypeAndTotality(null, null)

      val pyType = Ref.deref(valueTypeWithQualifiers.first)
      val requiredField = valueTypeWithQualifiers.second

      val isRequired = requiredField?.isRequired ?: total
      val qualifiers = PyTypedDictType.TypedDictFieldQualifiers(isRequired = isRequired, isReadOnly = requiredField?.isReadOnly == true)
      return PyTypedDictType.FieldTypeAndTotality(null, pyType, qualifiers)
    }

    private fun getStringBasedTypeForTypedDict(contents: String,
                                               anchor: PsiElement,
                                               context: TypeEvalContext): Pair<Ref<PyType?>?, PyTypedDictType.TypedDictFieldQualifiers?>? {
      val file = FileContextUtil.getContextFile(anchor) ?: return null
      val expr = PyUtil.createExpressionFromFragment(contents, file)
      var qualifiers: PyTypedDictType.TypedDictFieldQualifiers ? = null
      if (expr is PySubscriptionExpression) {
        qualifiers = parseTypedDictFieldQualifiers(expr, context)
      }
      return if (expr != null) Pair(getType(expr, context), qualifiers) else null
    }
  }
}
