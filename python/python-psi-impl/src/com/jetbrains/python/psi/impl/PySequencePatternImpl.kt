package com.jetbrains.python.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiListLikeElement
import com.intellij.psi.util.findParentInFile
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.*
import com.jetbrains.python.psi.types.PyLiteralType.Companion.upcastLiteralToClass
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class PySequencePatternImpl(astNode: ASTNode?) : PyElementImpl(astNode), PySequencePattern, PsiListLikeElement {
  override fun acceptPyVisitor(pyVisitor: PyElementVisitor) {
    pyVisitor.visitPySequencePattern(this)
  }

  override fun getComponents(): List<PyPattern> = elements

  override fun getType(context: TypeEvalContext, key: TypeEvalContext.Key): PyType? {
    val sequenceCaptureType = this.getSequenceCaptureType(context)
    val types = elements.flatMap { pattern ->
      when (pattern) {
        is PySingleStarPattern -> pattern.getCapturedTypesFromSequenceType(sequenceCaptureType, context)
        else -> listOf(context.getType(pattern))
      }
    }
    
    val expectedType = when {
      sequenceCaptureType.isHeterogeneousTuple() -> PyTupleType.create(this, types)
      else -> wrapInSequenceType(PyUnionType.union(types))
    }
    
    if (sequenceCaptureType == null) return expectedType
    return sequenceCaptureType.toList()
      .map { if (PyTypeChecker.match(expectedType, it, context)) it else expectedType }
      .let { PyUnionType.union(it) }
  }

  override fun canExcludePatternType(context: TypeEvalContext): Boolean {
    val allElementsCoverCapture = elements.all { PyClassPatternImpl.canExcludeArgumentPatternType(it, context) }
    val allCapturesOfThisAreHeteroTuples = this.getSequenceCaptureType(context).toList().all { it.isHeterogeneousTuple() }
    return allElementsCoverCapture && allCapturesOfThisAreHeteroTuples
  }

  override fun getCaptureTypeForChild(pattern: PyPattern, context: TypeEvalContext): PyType? {
    val sequenceType = this.getSequenceCaptureType(context) ?: return null

    // This is done to skip group- and as-patterns
    val sequenceMember = pattern.findParentInFile(withSelf = true) { el -> this === el.parent }
    if (sequenceMember is PySingleStarPattern) {
      return sequenceType.toList()
        .flatMap { sequenceMember.getCapturedTypesFromSequenceType(it, context) }
        .let { PyUnionType.union(it) }
        .let { wrapInListType(it) }
    }
    
    val idx = elements.indexOf(sequenceMember)
    
    return sequenceType.toList()
      .map { getElementTypeSkippingStar(it, idx, context) }
      .let { PyUnionType.union(it) }
  }

  private fun getElementTypeSkippingStar(sequence: PyType?, idx: Int, context: TypeEvalContext): PyType? {
    if (sequence.isHeterogeneousTuple()) {
      val starIdx = elements.indexOfFirst { it is PySingleStarPattern }
      if (starIdx == -1 || idx < starIdx) {
        return sequence.getElementType(idx)
      }
      else {
        val starSpan = sequence.elementCount - this.elements.size
        return sequence.getElementType(idx + starSpan)
      }
    }
    else {
      val upcast = PyTypeUtil.convertToType(sequence, "typing.Sequence", this, context)
      return (upcast as? PyCollectionType)?.iteratedItemType
    }
  }

  /**
   * Similar to [PyCaptureContext.getCaptureType],
   * but only chooses types that would match to typing.Sequence, and have correct length
   */
  private fun PySequencePattern.getSequenceCaptureType(context: TypeEvalContext): PyType? {
    val captureTypes: PyType? = PyCaptureContext.getCaptureType(this, context)

    val potentialMatchingTypes = captureTypes.toList()
      .filter { it !is PyClassType || it.classQName !in listOf("str", "bytes", "bytearray") }
      .filter { PyTypeUtil.convertToType(it, "typing.Sequence", this, context) != null }

    val hasStar = elements.any { it is PySingleStarPattern }
    val types = potentialMatchingTypes.mapNotNull {
      if (it.isHeterogeneousTuple()) it.takeIfSizeMatches(elements.size, hasStar) else it
    }
    return PyUnionType.union(types)
  }

  fun wrapInListType(elementType: PyType?): PyType? {
    val list = PyBuiltinCache.getInstance(this).getClass("list") ?: return null
    return PyCollectionTypeImpl(list, false, listOf(upcastLiteralToClass(elementType)))
  }

  fun wrapInSequenceType(elementType: PyType?): PyType? {
    val sequence = PyPsiFacade.getInstance(getProject()).createClassByQName("typing.Sequence", this) ?: return null
    return PyCollectionTypeImpl(sequence, false, listOf(upcastLiteralToClass(elementType)))
  }
}

private fun PySingleStarPattern.getCapturedTypesFromSequenceType(sequenceType: PyType?, context: TypeEvalContext): List<PyType?> {
  if (sequenceType.isHeterogeneousTuple()) {
    val sequenceParent = this.parent as? PySequencePattern ?: return listOf()
    val idx = sequenceParent.elements.indexOf(this)
    return sequenceType.elementTypes.subList(idx, idx + sequenceType.elementCount - sequenceParent.elements.size + 1)
  }
  val upcast = PyTypeUtil.convertToType(sequenceType, "typing.Sequence", this, context)
  if (upcast is PyCollectionType) {
    return listOf(upcast.getIteratedItemType())
  }
  return listOf()
}

// Use it like PyTypeUtil#toStream
internal fun PyType?.toList(): List<PyType?> = if (this is PyUnionType) members.toList() else listOf(this)

@OptIn(ExperimentalContracts::class)
private fun PyType?.isHeterogeneousTuple(): Boolean {
  contract { returns(true) implies (this@isHeterogeneousTuple is PyTupleType) }
  return this is PyTupleType && !isHomogeneous
}

private fun PyTupleType.takeIfSizeMatches(desiredSize: Int, hasStar: Boolean): PyTupleType? {
  if (this.elementTypes.any { it is PyUnpackedTupleType }) {
    val variadicElementsCount: Int = desiredSize - this.elementTypes.size + 1
    if (variadicElementsCount >= 0) {
      return this.expandVariadics(variadicElementsCount)
    }
  }
  else {
    if (hasStar && desiredSize <= this.elementCount || desiredSize == this.elementCount) {
      return this
    }
  }
  return null
}

private fun PyTupleType.expandVariadics(variadicElementCount: Int): PyTupleType {
  require(!this.isHomogeneous) { "Supplied tuple must not be homogeneous: $this" }
  require(variadicElementCount >= 0) { "Supplied variadic element count must not be negative: $variadicElementCount" }

  val unpackedTupleIndex = elementTypes.indexOfFirst { it is PyUnpackedTupleType }
  val unpackedTupleType = elementTypes[unpackedTupleIndex] as PyUnpackedTupleType
  assert(unpackedTupleType.isUnbound)

  val adjustedTupleElementTypes = buildList {
    addAll(elementTypes.subList(0, unpackedTupleIndex))
    repeat(variadicElementCount) {
      add(unpackedTupleType.elementTypes[0])
    }
    addAll(elementTypes.subList(unpackedTupleIndex + 1, elementTypes.size))
  }
  return PyTupleType(this.pyClass, adjustedTupleElementTypes, false)
}