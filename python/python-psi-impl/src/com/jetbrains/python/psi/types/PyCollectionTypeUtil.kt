/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.psi.types

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ArrayUtil
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.resolve.PyResolveContext

object PyCollectionTypeUtil {

  const val DICT_CONSTRUCTOR: String = "dict.__init__"
  private const val LIST_CONSTRUCTOR = "list.__init__"
  private const val SET_CONSTRUCTOR = "set.__init__"
  private const val RANGE_CONSTRUCTOR = "range"

  private const val MAX_ANALYZED_ELEMENTS_OF_LITERALS = 10 /* performance */

  fun getCollectionConstructors(languageLevel: LanguageLevel): Set<String> {
    return if (languageLevel.isPython2) {
      setOf(LIST_CONSTRUCTOR,
            DICT_CONSTRUCTOR,
            SET_CONSTRUCTOR,
            RANGE_CONSTRUCTOR)
    }
    else {
      setOf(LIST_CONSTRUCTOR,
            DICT_CONSTRUCTOR,
            SET_CONSTRUCTOR)
    }
  }

  fun getTypedDictTypeWithModifications(sequence: PySequenceExpression, context: TypeEvalContext): PyTypedDictType? {
    var allStrKeys = true
    val keysToValueTypes = LinkedHashMap<String, Pair<PyExpression?, PyType?>>()

    val typedDictType = getTypedDictTypeFromDictLiteral(sequence, context)
    if (typedDictType != null) {
      keysToValueTypes.putAll(typedDictType.getKeysToValuesWithTypes())
    }
    else {
      allStrKeys = false
    }

    val (allStrKeysInModifications, typedDictTypeByModifications) = getTypedDictTypeByModificationsForDictConstructor(sequence, context)
    if (allStrKeysInModifications && typedDictTypeByModifications != null) {
      typedDictTypeByModifications.getKeysToValuesWithTypes().forEach {
        if (keysToValueTypes.putIfAbsent(it.key, it.value) != null) {
          keysToValueTypes[it.key] = Pair(it.value.first, PyUnionType.union(keysToValueTypes[it.key]?.second, it.value.second))
        }
      }
    }

    return if (allStrKeys && allStrKeysInModifications) return PyTypedDictType.createFromKeysToValueTypes(sequence, keysToValueTypes)
    else null
  }

  fun getTypeByModifications(sequence: PySequenceExpression, context: TypeEvalContext): List<PyType?> {
    return when (sequence) {
      is PyListLiteralExpression -> listOf(getListOrSetIteratedValueType(sequence, context, true))
      is PySetLiteralExpression -> listOf(getListOrSetIteratedValueType(sequence, context, true))
      is PyDictLiteralExpression -> getDictElementTypesWithModifications(sequence, context)
      else -> listOf<PyType?>(null)
    }
  }

  private fun getListOrSetIteratedValueType(sequence: PySequenceExpression, context: TypeEvalContext, withModifications: Boolean): PyType? {
    val elements = sequence.elements
    val maxAnalyzedElements = MAX_ANALYZED_ELEMENTS_OF_LITERALS.coerceAtMost(elements.size)
    var analyzedElementsType = PyUnionType.union(elements.take(maxAnalyzedElements).map { context.getType(it) })
    if (withModifications) {
      val typesByModifications = getCollectionTypeByModifications(sequence, context)
      if (typesByModifications.isNotEmpty()) {
        val typeByModifications = PyUnionType.union(typesByModifications)
        analyzedElementsType = if (analyzedElementsType == null) typeByModifications
        else PyUnionType.union(analyzedElementsType, typeByModifications)
      }
    }

    return if (elements.size > maxAnalyzedElements) {
      PyUnionType.createWeakType(analyzedElementsType)
    }
    else {
      analyzedElementsType
    }
  }

  private fun getDictElementTypes(sequence: PySequenceExpression, context: TypeEvalContext): Pair<PyType?, PyType?> {
    val typedDictType = getTypedDictTypeFromDictLiteral(sequence, context)
    if (typedDictType != null) {
      return Pair(typedDictType.elementTypes.component1(), typedDictType.elementTypes.component2())
    }
    else {
      return getDictLiteralElementTypes(sequence, context)
    }
  }

  private fun getTypedDictTypeFromDictLiteral(sequence: PySequenceExpression,
                                              context: TypeEvalContext): PyTypedDictType? {
    val elements = sequence.elements
    val maxAnalyzedElements = MAX_ANALYZED_ELEMENTS_OF_LITERALS.coerceAtMost(elements.size)
    var allStrKeys = true
    val strKeysToValueTypes = HashMap<String, Pair<PyExpression?, PyType?>>()

    elements
      .take(maxAnalyzedElements)
      .map { element -> Pair(element, context.getType(element) as? PyTupleType) }
      .forEach { (tuple, tupleType) ->
        if (tupleType != null) {
          val tupleElementTypes = tupleType.elementTypes
          when {
            tupleType.isHomogeneous -> {
              val keyAndValueType = tupleType.iteratedItemType
              val keysToValueTypes = assemblePotentialTypedDictFields(keyAndValueType, keyAndValueType, tuple)
              if (keysToValueTypes != null) {
                strKeysToValueTypes.putAll(keysToValueTypes)
              }
              else {
                allStrKeys = false
              }
            }
            tupleElementTypes.size == 2 -> {
              val keysToValueTypes = assemblePotentialTypedDictFields(tupleElementTypes[0], tupleElementTypes[1], tuple)
              if (keysToValueTypes != null) {
                strKeysToValueTypes.putAll(keysToValueTypes)
              }
              else {
                allStrKeys = false
              }
            }
            else -> {
              allStrKeys = false
            }
          }
        }
        else {
          allStrKeys = false
        }
      }

    if (elements.size > maxAnalyzedElements) {
      allStrKeys = false
    }

    return if (allStrKeys) PyTypedDictType.createFromKeysToValueTypes(sequence, strKeysToValueTypes) else null
  }

  private fun getDictLiteralElementTypes(sequence: PySequenceExpression,
                                         context: TypeEvalContext): Pair<PyType?, PyType?> {
    val elements = sequence.elements
    val maxAnalyzedElements = MAX_ANALYZED_ELEMENTS_OF_LITERALS.coerceAtMost(elements.size)
    val keyTypes = ArrayList<PyType?>()
    val valueTypes = ArrayList<PyType?>()

    elements
      .take(maxAnalyzedElements)
      .map { element -> context.getType(element) as? PyTupleType }
      .forEach { tupleType ->
        if (tupleType != null) {
          val tupleElementTypes = tupleType.elementTypes
          when {
            tupleType.isHomogeneous -> {
              val keyAndValueType = tupleType.iteratedItemType
              keyTypes.add(keyAndValueType)
              valueTypes.add(keyAndValueType)
            }
            tupleElementTypes.size == 2 -> {
              val keyType = tupleElementTypes[0]
              val valueType = tupleElementTypes[1]
              keyTypes.add(keyType)
              valueTypes.add(valueType)
            }
            else -> {
              keyTypes.add(null)
              valueTypes.add(null)
            }
          }
        }
        else {
          keyTypes.add(null)
          valueTypes.add(null)
        }
      }

    if (elements.size > maxAnalyzedElements) {
      keyTypes.add(null)
      valueTypes.add(null)
    }

    return Pair(PyUnionType.union(keyTypes), PyUnionType.union(valueTypes))
  }

  private fun assemblePotentialTypedDictFields(keyType: PyType?,
                                               valueType: PyType?,
                                               tuple: PyExpression): Map<String, Pair<PyExpression?, PyType?>>? {
    val strKeysToValueTypes = LinkedHashMap<String, Pair<PyExpression?, PyType?>>()
    var allStrKeys = true

    if (keyType is PyClassType && "str" == keyType.name) {
      when (tuple) {
        is PyKeyValueExpression -> {
          if (tuple.key is PyStringLiteralExpression) {
            strKeysToValueTypes[(tuple.key as PyStringLiteralExpression).stringValue] = Pair(tuple.value, valueType)
          }
        }
        is PyTupleExpression -> {
          val tupleElements = tuple.elements
          if (tupleElements.size > 1 && tupleElements[0] is PyStringLiteralExpression) {
            strKeysToValueTypes[(tupleElements[0] as PyStringLiteralExpression).stringValue] = Pair(tupleElements[1], valueType)
          }
        }
      }
    }
    else {
      allStrKeys = false
    }

    return if (allStrKeys) strKeysToValueTypes else null
  }

  private fun getDictElementTypesWithModifications(sequence: PySequenceExpression, context: TypeEvalContext): List<PyType?> {
    var keyType: PyType?
    var valueType: PyType?

    val elementTypes = getDictLiteralElementTypes(sequence, context)
    keyType = elementTypes.first
    valueType = elementTypes.second

    val elements = sequence.elements
    val typesByModifications = getCollectionTypeByModifications(sequence, context)
    if (typesByModifications.size == 2) {
      val keysByModifications = typesByModifications[0]
      keyType = if (elements.isNotEmpty()) {
        PyUnionType.union(keyType, keysByModifications)
      }
      else {
        keysByModifications
      }
      val valuesByModifications = typesByModifications[1]
      valueType = if (elements.isNotEmpty()) {
        PyUnionType.union(valueType, valuesByModifications)
      }
      else {
        valuesByModifications
      }
    }

    return listOf(keyType, valueType)
  }

  private fun getTypedDictTypeByModificationsForDictConstructor(sequence: PySequenceExpression,
                                                                context: TypeEvalContext): Pair<Boolean, PyTypedDictType?> {
    val target = getTargetForValueInAssignment(sequence) ?: return Pair(true, null)
    val owner = ScopeUtil.getScopeOwner(target) ?: return Pair(true, null)
    val visitor = PyTypedDictTypeVisitor(target, context)
    owner.accept(visitor)
    return Pair(visitor.hasAllStrKeys, visitor.typedDictType)
  }

  private fun getCollectionTypeByModifications(sequence: PySequenceExpression, context: TypeEvalContext): List<PyType?> {
    val target = getTargetForValueInAssignment(sequence) ?: return emptyList()
    val owner = ScopeUtil.getScopeOwner(target) ?: return emptyList()
    val visitor = getVisitorForSequence(sequence, target, context) ?: return emptyList()
    owner.accept(visitor)
    return visitor.elementTypes
  }

  fun getTypedDictTypeByModificationsForDictConstructor(qualifiedName: String,
                                                        element: PsiElement,
                                                        context: TypeEvalContext): Pair<Boolean, PyTypedDictType?> {
    if (qualifiedName == DICT_CONSTRUCTOR) {
      val owner = ScopeUtil.getScopeOwner(element)
      if (owner != null) {
        val visitor = PyTypedDictTypeVisitor(element, context)
        owner.accept(visitor)
        return Pair(visitor.hasAllStrKeys, visitor.typedDictType)
      }
    }
    return Pair(true, null)
  }

  fun getCollectionTypeByModifications(qualifiedName: String, element: PsiElement, context: TypeEvalContext): List<PyType?> {
    val owner = ScopeUtil.getScopeOwner(element)
    if (owner != null) {
      val typeVisitor = getVisitorForQualifiedName(qualifiedName, element, context)
      if (typeVisitor != null) {
        owner.accept(typeVisitor)
        return typeVisitor.elementTypes
      }
    }
    return emptyList()
  }

  private fun getVisitorForSequence(sequence: PySequenceExpression,
                                    element: PsiElement,
                                    context: TypeEvalContext): PyCollectionTypeVisitor? {
    return when (sequence) {
      is PyListLiteralExpression -> PyListTypeVisitor(element, context)
      is PyDictLiteralExpression -> PyDictTypeVisitor(element, context)
      is PySetLiteralExpression -> PySetTypeVisitor(element, context)
      else -> null
    }
  }

  private fun getVisitorForQualifiedName(qualifiedName: String, element: PsiElement, context: TypeEvalContext): PyCollectionTypeVisitor? {
    return when (qualifiedName) {
      LIST_CONSTRUCTOR, RANGE_CONSTRUCTOR -> PyListTypeVisitor(element, context)
      DICT_CONSTRUCTOR -> PyDictTypeVisitor(element, context)
      SET_CONSTRUCTOR -> PySetTypeVisitor(element, context)
      else -> null
    }
  }

  fun getTargetForValueInAssignment(value: PyExpression): PyExpression? {
    val assignmentStatement = PsiTreeUtil.getParentOfType(value, PyAssignmentStatement::class.java, true, ScopeOwner::class.java)
    assignmentStatement?.targetsToValuesMapping?.filter { it.second === value }?.forEach { return it.first }
    return null
  }

  private fun getTypeForArgument(arguments: Array<PyExpression>, argumentIndex: Int, typeEvalContext: TypeEvalContext): PyType? {
    return if (argumentIndex < arguments.size)
      typeEvalContext.getType(arguments[argumentIndex])
    else
      null
  }

  private fun getTypeByModifications(node: PyCallExpression,
                                     modificationMethods: Map<String, (Array<PyExpression>) -> List<PyType?>>,
                                     element: PsiElement,
                                     typeEvalContext: TypeEvalContext): MutableList<PyType?>? {
    val valueTypes = mutableListOf<PyType?>()
    var isModificationExist = false
    val qualifiedExpression = node.callee as? PyQualifiedExpression ?: return null
    val funcName = qualifiedExpression.referencedName
    if (modificationMethods.containsKey(funcName)) {
      val referenceOwner = qualifiedExpression.qualifier as? PyReferenceOwner ?: return null
      val resolveContext = PyResolveContext.defaultContext(typeEvalContext)
      if (referenceOwner.getReference(resolveContext).isReferenceTo(element)) {
        isModificationExist = true
        val function = modificationMethods[funcName]
        if (function != null) {
          valueTypes.addAll(function(node.arguments))
        }
      }
    }
    return if (isModificationExist) valueTypes else null
  }

  private fun getRightValue(node: PySubscriptionExpression): PyExpression? {
    var parent = node.parent

    var tupleParent: PyTupleExpression? = null
    if (parent is PyTupleExpression) {
      tupleParent = parent
      parent = tupleParent.parent
    }

    if (parent is PyAssignmentStatement) {
      val assignment = parent
      val leftExpression = assignment.leftHandSideExpression

      if (tupleParent == null) {
        if (leftExpression !== node) return null
      }
      else {
        if (leftExpression !== tupleParent || !ArrayUtil.contains(node, *tupleParent.elements)) {
          return null
        }
      }

      var rightValue = assignment.assignedValue
      if (tupleParent != null && rightValue is PyTupleExpression) {
        val rightTuple = rightValue as PyTupleExpression?
        val rightElements = rightTuple!!.elements
        val indexInAssignment = tupleParent.elements.indexOf(node)
        if (indexInAssignment < rightElements.size) {
          rightValue = rightElements[indexInAssignment]
        }
      }
      return rightValue
    }
    return null
  }

  private fun getTypedDictTypeByModificationsForDictConstructor(node: PySubscriptionExpression,
                                                                element: PsiElement,
                                                                typeEvalContext: TypeEvalContext): PyTypedDictType? {
    var allStrKeys = true
    val keysToValueTypes = LinkedHashMap<String, Pair<PyExpression?, PyType?>>()
    val rightValue = getRightValue(node)

    if (rightValue != null) {
      val rightValueType = typeEvalContext.getType(rightValue)
      val indexExpression = node.indexExpression
      if (indexExpression is PyStringLiteralExpression) {
        keysToValueTypes[indexExpression.stringValue] = Pair(rightValue, rightValueType)
      }
      else {
        allStrKeys = false
      }
    }

    return if (allStrKeys) PyTypedDictType.createFromKeysToValueTypes(element, keysToValueTypes) else null
  }

  private fun getTypeByModifications(node: PySubscriptionExpression,
                                     element: PsiElement,
                                     typeEvalContext: TypeEvalContext): Pair<List<PyType?>, List<PyType?>>? {
    var parent = node.parent
    val keyTypes = ArrayList<PyType?>()
    val valueTypes = ArrayList<PyType?>()
    var isModificationExist = false

    var tupleParent: PyTupleExpression? = null
    if (parent is PyTupleExpression) {
      tupleParent = parent
      parent = tupleParent.parent
    }

    if (parent is PyAssignmentStatement) {
      val assignment = parent
      val leftExpression = assignment.leftHandSideExpression

      if (tupleParent == null) {
        if (leftExpression !== node) return null
      }
      else {
        if (leftExpression !== tupleParent || !ArrayUtil.contains(node, *tupleParent.elements)) {
          return null
        }
      }

      val resolveContext = PyResolveContext.defaultContext(typeEvalContext)
      val referenceOwner = node.operand as? PyReferenceOwner ?: return null
      val reference = referenceOwner.getReference(resolveContext)
      isModificationExist = if (reference.isReferenceTo(element)) true else return null

      val indexExpression = node.indexExpression
      if (indexExpression != null) {
        keyTypes.add(typeEvalContext.getType(indexExpression))
      }

      val rightValue = getRightValue(node)
      if (rightValue != null) {
        val rightValueType = typeEvalContext.getType(rightValue)
        valueTypes.add(rightValueType)
      }
    }

    return if (isModificationExist) Pair(keyTypes, valueTypes) else null
  }

  private abstract class PyCollectionTypeVisitor(protected val myElement: PsiElement,
                                                 protected val myTypeEvalContext: TypeEvalContext) : PyRecursiveElementVisitor() {
    protected val scopeOwner: ScopeOwner? = ScopeUtil.getScopeOwner(myElement)
    protected open var isModificationExist = false
    open val typedDictType: PyTypedDictType? = null

    abstract val elementTypes: List<PyType?>

    abstract fun initMethods(): Map<String, (Array<PyExpression>) -> List<PyType?>>

    override fun visitPyFunction(node: PyFunction) {
      if (node === scopeOwner) {
        super.visitPyFunction(node)
      }
      // ignore nested functions
    }

    override fun visitPyClass(node: PyClass) {
      if (node === scopeOwner) {
        super.visitPyClass(node)
      }
      // ignore nested classes
    }
  }

  private class PyListTypeVisitor(element: PsiElement,
                                  typeEvalContext: TypeEvalContext) : PyCollectionTypeVisitor(element, typeEvalContext) {
    private val modificationMethods: Map<String, (Array<PyExpression>) -> List<PyType?>>
    private val valueTypes: MutableList<PyType?>
    override var isModificationExist = false

    override val elementTypes: List<PyType?>
      get() = if (isModificationExist) valueTypes else emptyList()

    init {
      modificationMethods = initMethods()
      valueTypes = mutableListOf()
    }

    override fun initMethods(): Map<String, (Array<PyExpression>) -> List<PyType?>> {
      val modificationMethods = HashMap<String, (Array<PyExpression>) -> List<PyType?>>()

      modificationMethods["append"] = { listOf(getTypeForArgument(it, 0, myTypeEvalContext)) }
      modificationMethods["index"] = { listOf(getTypeForArgument(it, 0, myTypeEvalContext)) }
      modificationMethods["insert"] = { listOf(getTypeForArgument(it, 1, myTypeEvalContext)) }

      modificationMethods["extend"] = {
        val argType = getTypeForArgument(it, 0, myTypeEvalContext)
        if (argType is PyCollectionType) {
          argType.elementTypes
        }
        else {
          emptyList()
        }
      }

      return modificationMethods
    }

    override fun visitPyCallExpression(node: PyCallExpression) {
      val types = getTypeByModifications(node, modificationMethods, myElement, myTypeEvalContext)
      if (types != null) {
        isModificationExist = true
        valueTypes.addAll(types)
      }
    }

    override fun visitPySubscriptionExpression(node: PySubscriptionExpression) {
      val types = getTypeByModifications(node, myElement, myTypeEvalContext)
      if (types != null) {
        isModificationExist = true
        valueTypes.addAll(types.second)
      }
    }
  }

  private class PyDictTypeVisitor(element: PsiElement,
                                  typeEvalContext: TypeEvalContext) : PyCollectionTypeVisitor(element, typeEvalContext) {
    private val modificationMethods: Map<String, (Array<PyExpression>) -> List<PyType?>>
    private val keyTypes: MutableList<PyType?>
    private val valueTypes: MutableList<PyType?>

    override val elementTypes: List<PyType?>
      get() = if (isModificationExist) {
        listOf(PyUnionType.union(keyTypes), PyUnionType.union(valueTypes))
      }
      else emptyList()

    init {
      modificationMethods = initMethods()
      keyTypes = mutableListOf()
      valueTypes = mutableListOf()
    }

    override fun initMethods(): Map<String, (Array<PyExpression>) -> List<PyType?>> {
      val modificationMethods = HashMap<String, (Array<PyExpression>) -> List<PyType?>>()

      modificationMethods["update"] = { arguments ->
        if (arguments.size == 1 && arguments[0] is PyDictLiteralExpression) {
          val dict = arguments[0] as PyDictLiteralExpression
          val dictTypes = getDictLiteralElementTypes(dict, myTypeEvalContext)
          keyTypes.add(dictTypes.first)
          valueTypes.add(dictTypes.second)
        }
        else if (arguments.isNotEmpty()) {
          var keyStrAdded = false
          for (arg in arguments) {
            if (arg is PyKeywordArgument) {
              if (!keyStrAdded) {
                val strType = PyBuiltinCache.getInstance(myElement).strType
                if (strType != null) {
                  keyTypes.add(strType)
                }
                keyStrAdded = true
              }
              val value = PyUtil.peelArgument(arg)
              if (value != null) {
                val valueType = myTypeEvalContext.getType(value)
                valueTypes.add(valueType)
              }
            }
          }
        }
        emptyList()
      }

      return modificationMethods
    }

    override fun visitPyCallExpression(node: PyCallExpression) {
      val types = getTypeByModifications(node, modificationMethods, myElement, myTypeEvalContext)
      if (types != null) {
        isModificationExist = true
        valueTypes.addAll(types)
      }
    }

    override fun visitPySubscriptionExpression(node: PySubscriptionExpression) {
      val types = getTypeByModifications(node, myElement, myTypeEvalContext)
      if (types != null) {
        isModificationExist = true
        keyTypes.addAll(types.first)
        valueTypes.addAll(types.second)
      }
    }
  }

  private class PyTypedDictTypeVisitor(element: PsiElement,
                                       typeEvalContext: TypeEvalContext) : PyCollectionTypeVisitor(element, typeEvalContext) {
    private val modificationMethods: Map<String, (Array<PyExpression>) -> List<PyType?>>
    var hasAllStrKeys = true
    private val strKeysToValueTypes = LinkedHashMap<String, Pair<PyExpression?, PyType?>>()

    override val typedDictType: PyTypedDictType?
      get() = if (isModificationExist && hasAllStrKeys) PyTypedDictType.createFromKeysToValueTypes(myElement, strKeysToValueTypes)
      else null

    override val elementTypes: List<PyType?>
      get() = if (isModificationExist && hasAllStrKeys)
        PyTypedDictType.createFromKeysToValueTypes(myElement, strKeysToValueTypes)?.elementTypes ?: emptyList()
      else emptyList()

    init {
      modificationMethods = initMethods()
    }

    override fun initMethods(): Map<String, (Array<PyExpression>) -> List<PyType?>> {
      val modificationMethods = HashMap<String, (Array<PyExpression>) -> List<PyType?>>()

      modificationMethods["update"] = { arguments ->
        if (arguments.size == 1 && arguments[0] is PyDictLiteralExpression) {
          val dict = arguments[0] as PyDictLiteralExpression
          val typedDictType = getTypedDictTypeFromDictLiteral(dict, myTypeEvalContext)
          if (typedDictType != null) {
            val dictTypeElementTypes = typedDictType.elementTypes
            if (dictTypeElementTypes.size == 2) {
              strKeysToValueTypes.putAll(typedDictType.getKeysToValuesWithTypes())
            }
          }
          hasAllStrKeys = false
        }
        else if (arguments.isNotEmpty()) {
          for (arg in arguments) {
            if (arg is PyKeywordArgument) {
              val value = PyUtil.peelArgument(arg)
              if (value != null) {
                val valueType = myTypeEvalContext.getType(value)
                val keyword = arg.keyword
                if (keyword != null) {
                  strKeysToValueTypes[keyword] = Pair(value, if (strKeysToValueTypes.containsKey(keyword))
                    PyUnionType.union(strKeysToValueTypes[keyword]?.second, valueType)
                  else valueType)
                }
              }
            }
          }
        }
        emptyList()
      }

      return modificationMethods
    }

    override fun visitPyCallExpression(node: PyCallExpression) {
      val types = getTypeByModifications(node, modificationMethods, myElement, myTypeEvalContext)
      if (types != null) {
        isModificationExist = true
      }
    }

    override fun visitPySubscriptionExpression(node: PySubscriptionExpression) {
      val typedDictType = getTypedDictTypeByModificationsForDictConstructor(node, myElement, myTypeEvalContext)
      if (typedDictType != null) {
        isModificationExist = true
        strKeysToValueTypes.putAll(typedDictType.getKeysToValuesWithTypes())
      }
      else {
        hasAllStrKeys = false
      }
    }
  }

  private class PySetTypeVisitor(element: PsiElement,
                                 typeEvalContext: TypeEvalContext) : PyCollectionTypeVisitor(element, typeEvalContext) {
    private val modificationMethods: Map<String, (Array<PyExpression>) -> List<PyType?>>
    private val valueTypes: MutableList<PyType?>

    override val elementTypes: List<PyType?>
      get() = if (isModificationExist) valueTypes else emptyList()

    init {
      modificationMethods = initMethods()
      valueTypes = ArrayList()
    }

    override fun initMethods(): Map<String, (Array<PyExpression>) -> List<PyType?>> {
      val modificationMethods = HashMap<String, (Array<PyExpression>) -> List<PyType?>>()

      modificationMethods["add"] = { listOf(getTypeForArgument(it, 0, myTypeEvalContext)) }

      modificationMethods["update"] = { arguments ->
        val types = ArrayList<PyType?>()
        for (argument in arguments) {
          when (argument) {
            is PySetLiteralExpression ->
              types.add(getListOrSetIteratedValueType(argument as PySequenceExpression, myTypeEvalContext, false))
            is PyListLiteralExpression ->
              types.add(getListOrSetIteratedValueType(argument as PySequenceExpression, myTypeEvalContext, false))
            is PyDictLiteralExpression ->
              types.add(getDictElementTypes(argument as PySequenceExpression, myTypeEvalContext).first)
            else -> {
              val argType = myTypeEvalContext.getType(argument)
              if (argType is PyCollectionType) {
                types.addAll(argType.elementTypes)
              }
              else {
                types.add(argType)
              }
            }
          }
        }
        types
      }

      return modificationMethods
    }

    override fun visitPyCallExpression(node: PyCallExpression) {
      val types = getTypeByModifications(node, modificationMethods, myElement, myTypeEvalContext)
      if (types != null) {
        isModificationExist = true
        valueTypes.addAll(types)
      }
    }
  }
}
