/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.psi.types

import com.intellij.openapi.util.Pair
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ArrayUtil
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.resolve.PyResolveContext
import java.util.*

object PyCollectionTypeUtil {

  val DICT_CONSTRUCTOR: String = "dict.__init__"
  private const val LIST_CONSTRUCTOR = "list.__init__"
  private const val SET_CONSTRUCTOR = "set.__init__"

  var COLLECTION_CONSTRUCTORS: Set<String> = setOf(LIST_CONSTRUCTOR, DICT_CONSTRUCTOR, SET_CONSTRUCTOR)

  private val MAX_ANALYZED_ELEMENTS_OF_LITERALS = 10 /* performance */

  fun getTypeByModifications(sequence: PySequenceExpression, context: TypeEvalContext): List<PyType?> {
    return when (sequence) {
      is PyListLiteralExpression -> listOf(getListOrSetIteratedValueType(sequence, context, true))
      is PySetLiteralExpression -> listOf(getListOrSetIteratedValueType(sequence, context, true))
      is PyDictLiteralExpression -> getDictElementTypesWithModifications(sequence, context)
      else -> listOf<PyType?>(null)
    }
  }

  private fun getListOrSetIteratedValueType(sequence: PySequenceExpression, context: TypeEvalContext,
                                            withModifications: Boolean): PyType? {
    val elements = sequence.elements
    val maxAnalyzedElements = Math.min(MAX_ANALYZED_ELEMENTS_OF_LITERALS, elements.size)
    var analyzedElementsType = PyUnionType.union(elements
                                                   .take(maxAnalyzedElements)
                                                   .map { context.getType(it) })
    if (withModifications) {
      val typesByModifications = getCollectionTypeByModifications(sequence, context)
      if (!typesByModifications.isEmpty()) {
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

  private fun getDictElementTypes(sequence: PySequenceExpression, context: TypeEvalContext): List<PyType?> {
    val elements = sequence.elements
    val maxAnalyzedElements = Math.min(MAX_ANALYZED_ELEMENTS_OF_LITERALS, elements.size)
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
              keyTypes.add(tupleElementTypes[0])
              valueTypes.add(tupleElementTypes[1])
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

    return listOf(PyUnionType.union(keyTypes), PyUnionType.union(valueTypes))
  }

  private fun getDictElementTypesWithModifications(sequence: PySequenceExpression,
                                                   context: TypeEvalContext): List<PyType?> {
    val dictTypes = getDictElementTypes(sequence, context)
    var keyType: PyType? = null
    var valueType: PyType? = null
    if (dictTypes.size == 2) {
      keyType = dictTypes[0]
      valueType = dictTypes[1]
    }

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

  private fun getCollectionTypeByModifications(sequence: PySequenceExpression, context: TypeEvalContext): List<PyType?> {
    val target = getTargetForValueInAssignment(sequence) ?: return emptyList()
    val owner = ScopeUtil.getScopeOwner(target) ?: return emptyList()
    val visitor = getVisitorForSequence(sequence, target, context) ?: return emptyList()
    owner.accept(visitor)
    return visitor.result
  }

  fun getCollectionTypeByModifications(qualifiedName: String, element: PsiElement,
                                       context: TypeEvalContext): List<PyType?> {
    val owner = ScopeUtil.getScopeOwner(element)
    if (owner != null) {
      val typeVisitor = getVisitorForQualifiedName(qualifiedName, element, context)
      if (typeVisitor != null) {
        owner.accept(typeVisitor)
        return typeVisitor.result
      }
    }
    return emptyList()
  }

  private fun getVisitorForSequence(sequence: PySequenceExpression, element: PsiElement,
                                    context: TypeEvalContext): PyCollectionTypeVisitor? {
    return when (sequence) {
      is PyListLiteralExpression -> PyListTypeVisitor(element, context)
      is PyDictLiteralExpression -> PyDictTypeVisitor(element, context)
      is PySetLiteralExpression -> PySetTypeVisitor(element, context)
      else -> null
    }
  }

  private fun getVisitorForQualifiedName(qualifiedName: String, element: PsiElement,
                                         context: TypeEvalContext): PyCollectionTypeVisitor? {
    return when (qualifiedName) {
      LIST_CONSTRUCTOR -> PyListTypeVisitor(element, context)
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
      val resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(typeEvalContext)
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

      val resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(typeEvalContext)
      val referenceOwner = node.operand as? PyReferenceOwner ?: return null
      val reference = referenceOwner.getReference(resolveContext)
      isModificationExist = if (reference.isReferenceTo(element)) true else return null

      val indexExpression = node.indexExpression
      if (indexExpression != null) {
        keyTypes.add(typeEvalContext.getType(indexExpression))
      }

      var rightValue = assignment.assignedValue
      if (tupleParent != null && rightValue is PyTupleExpression) {
        val rightTuple = rightValue as PyTupleExpression?
        val rightElements = rightTuple!!.elements
        val indexInAssignment = Arrays.asList(*tupleParent.elements).indexOf(node)
        if (indexInAssignment < rightElements.size) {
          rightValue = rightElements[indexInAssignment]
        }
      }

      if (rightValue != null) {
        valueTypes.add(typeEvalContext.getType(rightValue))
      }
    }

    return if (isModificationExist) Pair(keyTypes, valueTypes) else null
  }

  private abstract class PyCollectionTypeVisitor(protected val myElement: PsiElement,
                                                 protected val myTypeEvalContext: TypeEvalContext) : PyRecursiveElementVisitor() {
    protected val scopeOwner: ScopeOwner? = ScopeUtil.getScopeOwner(myElement)
    protected open var isModificationExist = false

    abstract val result: List<PyType?>

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

    override val result: List<PyType?>
      get() = if (isModificationExist) valueTypes else emptyList()

    init {
      modificationMethods = initMethods()
      valueTypes = mutableListOf()
    }

    override fun initMethods(): Map<String, (Array<PyExpression>) -> List<PyType?>> {
      val modificationMethods = HashMap<String, (Array<PyExpression>) -> List<PyType?>>()

      modificationMethods.put("append", { arguments: Array<PyExpression> -> listOf(getTypeForArgument(arguments, 0, myTypeEvalContext)) })
      modificationMethods.put("index", { arguments: Array<PyExpression> -> listOf(getTypeForArgument(arguments, 0, myTypeEvalContext)) })
      modificationMethods.put("insert", { arguments: Array<PyExpression> -> listOf(getTypeForArgument(arguments, 1, myTypeEvalContext)) })
      modificationMethods.put("extend", { arguments: Array<PyExpression> ->
        val argType = getTypeForArgument(arguments, 0, myTypeEvalContext)
        if (argType is PyCollectionType) {
          argType.elementTypes
        }
        else {
          emptyList()
        }
      })

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

  private class PyDictTypeVisitor(element: PsiElement, typeEvalContext: TypeEvalContext) : PyCollectionTypeVisitor(element,
                                                                                                                   typeEvalContext) {
    private val modificationMethods: Map<String, (Array<PyExpression>) -> List<PyType?>>
    private val keyTypes: MutableList<PyType?>
    private val valueTypes: MutableList<PyType?>

    override val result: List<PyType>
      get() = if (isModificationExist) Arrays.asList<PyType>(PyUnionType.union(keyTypes), PyUnionType.union(valueTypes))
      else emptyList()

    init {
      modificationMethods = initMethods()
      keyTypes = mutableListOf()
      valueTypes = mutableListOf()
    }

    override fun initMethods(): Map<String, (Array<PyExpression>) -> List<PyType?>> {
      val modificationMethods = HashMap<String, (Array<PyExpression>) -> List<PyType?>>()

      modificationMethods.put("update", { arguments ->
        if (arguments.size == 1 && arguments[0] is PyDictLiteralExpression) {
          val dict = arguments[0] as PyDictLiteralExpression
          val dictTypes = getDictElementTypes(dict, myTypeEvalContext)
          if (dictTypes.size == 2) {
            keyTypes.add(dictTypes[0])
            valueTypes.add(dictTypes[1])
          }
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
                valueTypes.add(myTypeEvalContext.getType(value))
              }
            }
          }
        }
        emptyList()
      })

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

  private class PySetTypeVisitor(element: PsiElement, typeEvalContext: TypeEvalContext) : PyCollectionTypeVisitor(element,
                                                                                                                  typeEvalContext) {
    private val modificationMethods: Map<String, (Array<PyExpression>) -> List<PyType?>>
    private val valueTypes: MutableList<PyType?>

    override val result: List<PyType?>
      get() = if (isModificationExist) valueTypes else emptyList()

    init {
      modificationMethods = initMethods()
      valueTypes = ArrayList()
    }

    override fun initMethods(): Map<String, (Array<PyExpression>) -> List<PyType?>> {
      val modificationMethods = HashMap<String, (Array<PyExpression>) -> List<PyType?>>()
      modificationMethods.put("add", { arguments -> listOf(getTypeForArgument(arguments, 0, myTypeEvalContext)) })
      modificationMethods.put("update", { arguments ->
        val types = ArrayList<PyType?>()
        for (argument in arguments) {
          when (argument) {
            is PySetLiteralExpression -> types.add(getListOrSetIteratedValueType(argument as PySequenceExpression, myTypeEvalContext, false))
            is PyListLiteralExpression -> types.add(getListOrSetIteratedValueType(argument as PySequenceExpression, myTypeEvalContext, false))
            is PyDictLiteralExpression -> types.add(getDictElementTypes(argument as PySequenceExpression, myTypeEvalContext)[0])
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
        return@put types
      })
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
